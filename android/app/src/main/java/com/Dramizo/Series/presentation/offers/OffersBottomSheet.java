package com.Dramizo.Series.presentation.offers;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.BottomSheetOffersBinding;
import com.Dramizo.Series.databinding.ItemOfferBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AssetCatalog;
import com.Dramizo.Series.util.RemoteTheme;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OffersBottomSheet extends BottomSheetDialogFragment {
  public static void show(FragmentManager fm) {
    OffersBottomSheet sheet = new OffersBottomSheet();
    sheet.show(fm, "offers");
  }

  @Override
  public int getTheme() {
    return com.google.android.material.R.style.ThemeOverlay_Material3_BottomSheetDialog;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
    dialog.setOnShowListener(d -> com.Dramizo.Series.util.AuraDialogHelper.configureShown(dialog));
    return dialog;
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    BottomSheetOffersBinding binding = BottomSheetOffersBinding.inflate(inflater, container, false);
    AppContainer c = ContainerProvider.from(requireActivity());
    OfferAdapter adapter = new OfferAdapter(offer -> OfferDetailDialog.show(getParentFragmentManager(), offer));
    binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
    binding.recycler.setAdapter(adapter);
    binding.progress.setVisibility(View.VISIBLE);
    c.getIoExecutor().execute(() -> {
      Result<List<MiscDtos.OfferDto>> r = ApiCall.execute(c.getConfigApi().offers());
      requireActivity().runOnUiThread(() -> {
        binding.progress.setVisibility(View.GONE);
        if (r.success && r.data != null && !r.data.isEmpty()) {
          List<MiscDtos.OfferDto> offers = new ArrayList<>(r.data);
          adapter.submit(offers);
          List<String> skus = new ArrayList<>();
          for (MiscDtos.OfferDto o : offers) {
            if (o != null && o.sku != null && !o.sku.isEmpty()) skus.add(o.sku);
          }
          if (!skus.isEmpty()) {
            c.getBillingHelper().queryProducts(skus, () -> {
              c.getBillingHelper().applyPlayPricesToOffers(offers);
              adapter.submit(offers);
            });
          }
        } else {
          Toast.makeText(requireContext(),
              r.error != null ? r.error : getString(R.string.no_data), Toast.LENGTH_SHORT).show();
        }
      });
    });
    return binding.getRoot();
  }

  private static final class OfferAdapter extends RecyclerView.Adapter<OfferAdapter.VH> {
    private final List<MiscDtos.OfferDto> items = new ArrayList<>();
    private final java.util.function.Consumer<MiscDtos.OfferDto> onClick;

    OfferAdapter(java.util.function.Consumer<MiscDtos.OfferDto> onClick) {
      this.onClick = onClick;
    }

    void submit(List<MiscDtos.OfferDto> list) {
      items.clear();
      if (list != null) items.addAll(list);
      notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      ItemOfferBinding b = ItemOfferBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
      return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
      holder.bind(items.get(position), onClick);
    }

    @Override
    public int getItemCount() {
      return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
      private final ItemOfferBinding b;

      VH(ItemOfferBinding b) {
        super(b.getRoot());
        this.b = b;
      }

      void bind(MiscDtos.OfferDto offer, java.util.function.Consumer<MiscDtos.OfferDto> onClick) {
        b.tvTitle.setText(offer.title != null ? offer.title : "عرض");
        int total = offer.coins + Math.max(0, offer.bonusCoins);
        b.tvCoins.setText(String.format(Locale.US, "%,d كوينز", total));
        b.tvPrice.setText(offer.displayPrice());

        if (offer.bonusCoins > 0) {
          b.tvSubtitle.setVisibility(View.VISIBLE);
          String bonus = String.format(Locale.US, "%,d", offer.bonusCoins);
          CharSequence label = offer.subtitle != null && !offer.subtitle.isEmpty()
              ? offer.subtitle
              : b.getRoot().getContext().getString(R.string.store_offer_bonus_gift, bonus);
          b.tvSubtitle.setText(label);
          b.imgOfferGiftBadge.setVisibility(View.VISIBLE);
        } else {
          b.tvSubtitle.setVisibility(View.GONE);
          b.imgOfferGiftBadge.setVisibility(View.GONE);
        }
        b.tvPopular.setVisibility(offer.popular ? View.VISIBLE : View.GONE);

        bindOfferArt(b.imgOfferArt, offer);
        b.getRoot().setOnClickListener(v -> onClick.accept(offer));
      }
    }
  }

  static void bindOfferArt(ImageView view, MiscDtos.OfferDto offer) {
    if (view == null || offer == null) return;
    int fallback = AssetCatalog.offerArtForCoins(offer.coins);
    String url = offer.imageUrl != null && !offer.imageUrl.isEmpty() ? offer.imageUrl : null;
    if (url == null || url.isEmpty()) {
      view.setImageResource(fallback);
    } else {
      view.setImageResource(fallback);
      RemoteTheme.loadInto(view, url, fallback);
    }
  }

  static void bindLottie(com.airbnb.lottie.LottieAnimationView view, String url) {
    if (view == null) return;
    if (url != null && !url.isEmpty()) {
      view.setAnimationFromUrl(url);
      view.playAnimation();
    } else {
      view.setAnimation(R.raw.lottie_gift_burst);
      view.playAnimation();
    }
  }
}
