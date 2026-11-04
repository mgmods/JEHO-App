package com.Dramizo.Series.presentation.gifts;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.Dramizo.Series.data.remote.dto.GiftDtos;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.data.repository.GiftRepositoryImpl;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GiftViewModel extends ViewModel {
    private final AppContainer c;
    private final MutableLiveData<List<GiftDtos.GiftDto>> gifts =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<GiftDtos.GiftCategoryDto>> categories =
            new MutableLiveData<>(GiftRepositoryImpl.defaultCategories());
    private final MutableLiveData<GiftDtos.SendGiftResult> sent = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Long> coinsBalance = new MutableLiveData<>(null);
    private String lastGiftId;
    private String lastReceiverId;
    private int combo = 1;
    private long lastSendAt;
    private boolean loadInFlight;

    public GiftViewModel(AppContainer c) {
        this.c = c;
        // Instant fill for sheet open (no loading spinner needed when warm).
        GiftDtos.GiftList local = c.getGiftRepository().peekLocalOrNull();
        if (local != null && !local.isEmpty()) {
            gifts.setValue(new ArrayList<>(local));
        }
        List<GiftDtos.GiftCategoryDto> cats = c.getGiftRepository().peekCategoriesOrNull();
        if (cats != null && !cats.isEmpty()) {
            categories.setValue(new ArrayList<>(cats));
        }
    }

    public LiveData<List<GiftDtos.GiftDto>> getGifts() {
        return gifts;
    }

    public LiveData<List<GiftDtos.GiftCategoryDto>> getCategories() {
        return categories;
    }

    public LiveData<GiftDtos.SendGiftResult> getSent() {
        return sent;
    }

    /** Consume sticky send so re-opening the gift sheet does not re-fire FX / chat posts. */
    public void clearSent() {
        sent.setValue(null);
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Long> getCoinsBalance() {
        return coinsBalance;
    }

    public int getCombo() {
        return combo;
    }

    public boolean hasGiftsNow() {
        List<GiftDtos.GiftDto> v = gifts.getValue();
        return v != null && !v.isEmpty();
    }

    public void setCoinsBalance(long coins) {
        coinsBalance.postValue(coins);
    }

    public void load() {
        // Instant SWR: paint local first on the same call site.
        GiftDtos.GiftList local = c.getGiftRepository().peekLocalOrNull();
        if (local != null && !local.isEmpty()) {
            gifts.postValue(new ArrayList<>(local));
        }
        if (loadInFlight) return;
        final boolean hadUsableCatalog = hasGiftsNow() || (local != null && !local.isEmpty());
        loadInFlight = true;
        c.getIoExecutor().execute(() -> {
            try {
                Result<List<GiftDtos.GiftCategoryDto>> cats =
                        c.getGiftRepository().refreshCategories();
                if (cats.success && cats.data != null && !cats.data.isEmpty()) {
                    categories.postValue(new ArrayList<>(cats.data));
                }

                Result<GiftDtos.GiftList> r = c.getGiftRepository().refreshGifts();
                if (r.success && r.data != null && !r.data.isEmpty()) {
                    gifts.postValue(new ArrayList<>(r.data));
                    // GiftBottomSheet warms only a small visible priority window.
                    // Preloading the entire catalog here duplicated its Glide/video cache work
                    // and could exhaust memory on room devices while a gift was being sent.
                } else if (r.success && r.data != null) {
                    gifts.postValue(r.data);
                    if (r.data.isEmpty()) {
                        error.postValue("لا توجد هدايا حالياً");
                    }
                } else {
                    List<GiftDtos.GiftDto> current = gifts.getValue();
                    if ((current == null || current.isEmpty()) && !hadUsableCatalog) {
                        gifts.postValue(Collections.emptyList());
                        error.postValue("تعذر تحميل الهدايا من الخادم، حاول مجددًا بعد لحظات");
                    } else {
                        // A transient catalog request failure must not cover a valid
                        // cached gift sheet with a false server-error toast.
                        android.util.Log.w("GiftViewModel",
                                "Gift catalog refresh failed; keeping cached catalog: " +
                                        (r.error != null ? r.error : "unknown error"));
                    }
                }
            } catch (Exception failure) {
                List<GiftDtos.GiftDto> current = gifts.getValue();
                if ((current == null || current.isEmpty()) && !hadUsableCatalog) {
                    gifts.postValue(Collections.emptyList());
                    error.postValue("تعذر تحميل الهدايا من الخادم، حاول مجددًا بعد لحظات");
                }
                android.util.Log.w("GiftViewModel", "Gift catalog refresh exception", failure);
            } finally {
                loadInFlight = false;
            }
        });
    }

    public void loadWallet() {
        c.getIoExecutor().execute(() -> {
            Result<WalletDtos.WalletDto> r = c.getWalletUseCase.execute();
            if (r.success && r.data != null) {
                coinsBalance.postValue(r.data.coins);
            }
        });
    }

    public void send(String giftId, String receiverId, String roomId) {
        sendMany(giftId, receiverId == null ? Collections.emptyList()
                : Collections.singletonList(receiverId), 1, roomId);
    }

    public void sendMany(String giftId, List<String> receiverIds, int quantity, String roomId) {
        if (giftId == null || receiverIds == null || receiverIds.isEmpty()) {
            error.postValue("اختر مستلماً");
            return;
        }
        List<String> targets = new ArrayList<>();
        for (String id : receiverIds) {
            if (id != null && !id.isEmpty() && !targets.contains(id)) targets.add(id);
        }
        if (targets.isEmpty()) {
            error.postValue("اختر مستلماً");
            return;
        }
        int qty = Math.max(1, Math.min(177, quantity));
        long now = System.currentTimeMillis();
        String first = targets.get(0);
        // Gift combo streak is for rapid re-send of the same normal gift only.
        // Lucky / مردود must never inflate combo (user confuses ×combo with merdood).
        boolean lucky = isLuckyGift(giftId);
        boolean sameCombo = !lucky
                && giftId.equals(lastGiftId)
                && first != null && first.equals(lastReceiverId)
                && targets.size() == 1
                && (now - lastSendAt) < 4000;
        combo = lucky ? 1 : (sameCombo ? combo + 1 : 1);
        lastGiftId = giftId;
        lastReceiverId = first;
        lastSendAt = now;
        final int comboCount = combo;
        c.getIoExecutor().execute(() -> {
            Result<GiftDtos.SendGiftResult> r;
            try {
                if (targets.size() > 1 && roomId != null && !roomId.isEmpty()) {
                    r = c.sendGiftUseCase.executeAllMic(giftId, targets, qty, roomId, comboCount);
                } else {
                    r = c.sendGiftUseCase.execute(giftId, first, qty, roomId, comboCount);
                }
            } catch (RuntimeException failure) {
                android.util.Log.e("GiftViewModel", "Gift send request failed safely", failure);
                error.postValue("تعذر إرسال الهدية، حاول مرة أخرى");
                return;
            }
            if (!r.success) {
                error.postValue(r.error);
                return;
            }
            GiftDtos.SendGiftResult data = r.data;
            // Server may echo combo — never let lucky inflate local streak.
            if (data != null && data.comboCount > 0 && !isLuckyGift(giftId)) {
                combo = data.comboCount;
            } else if (isLuckyGift(giftId)) {
                combo = 1;
            }
            if (data != null) {
                if (data.wallet != null) {
                    coinsBalance.postValue(data.wallet.coins);
                } else if (data.senderBalance >= 0) {
                    coinsBalance.postValue(data.senderBalance);
                }
            }
            if (data != null && data.senderBalance < 0 && data.wallet == null) {
                Result<WalletDtos.WalletDto> w = c.getWalletUseCase.execute();
                if (w.success && w.data != null) coinsBalance.postValue(w.data.coins);
            }
            sent.postValue(data);
        });
    }

    private boolean isLuckyGift(String giftId) {
        if (giftId == null || giftId.isEmpty()) return false;
        List<GiftDtos.GiftDto> list = gifts.getValue();
        if (list == null) return false;
        for (GiftDtos.GiftDto g : list) {
            if (g == null || g.id == null || !giftId.equals(g.id)) continue;
            if (g.type != null && "lucky".equalsIgnoreCase(g.type.trim())) return true;
            if (g.category != null && "lucky".equalsIgnoreCase(g.category.trim())) return true;
            return false;
        }
        return false;
    }
}
