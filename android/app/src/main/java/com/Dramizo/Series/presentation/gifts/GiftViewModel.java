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
                    ArrayList<String> urls = new ArrayList<>();
                    for (GiftDtos.GiftDto g : r.data) {
                        if (g == null) continue;
                        if (g.animationUrl != null && !g.animationUrl.isEmpty()) {
                            urls.add(g.animationUrl);
                        }
                        if (g.iconUrl != null && !g.iconUrl.isEmpty()) {
                            urls.add(g.iconUrl);
                        }
                    }
                    try {
                        com.Dramizo.Series.util.NativeRoomEffectsView.preloadGiftUrls(
                                c.getAppContext(), urls);
                    } catch (Exception ignored) {
                    }
                } else if (r.success && r.data != null) {
                    gifts.postValue(r.data);
                    if (r.data.isEmpty()) {
                        error.postValue("لا توجد هدايا حالياً");
                    }
                } else {
                    List<GiftDtos.GiftDto> current = gifts.getValue();
                    if (current == null || current.isEmpty()) {
                        gifts.postValue(Collections.emptyList());
                        error.postValue(r.error != null ? r.error : "لا توجد هدايا");
                    }
                }
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
        boolean sameCombo = giftId.equals(lastGiftId)
                && first != null && first.equals(lastReceiverId)
                && targets.size() == 1
                && (now - lastSendAt) < 4000;
        combo = sameCombo ? combo + 1 : 1;
        lastGiftId = giftId;
        lastReceiverId = first;
        lastSendAt = now;
        final int comboCount = combo;
        c.getIoExecutor().execute(() -> {
            Result<GiftDtos.SendGiftResult> r;
            if (targets.size() > 1 && roomId != null && !roomId.isEmpty()) {
                r = c.sendGiftUseCase.executeAllMic(giftId, targets, qty, roomId, comboCount);
            } else {
                r = c.sendGiftUseCase.execute(giftId, first, qty, roomId, comboCount);
            }
            if (!r.success) {
                error.postValue(r.error);
                return;
            }
            GiftDtos.SendGiftResult data = r.data;
            if (data != null && data.comboCount > 0) combo = data.comboCount;
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
}
