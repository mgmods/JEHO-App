package com.Dramizo.Series.presentation.offers;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.data.remote.dto.WalletDtos;
import com.Dramizo.Series.databinding.DialogOfferDetailBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Collections;
import java.util.Locale;

public class OfferDetailDialog extends DialogFragment {
  private static final String ARG_OFFER = "offer";

  public static void show(FragmentManager fm, MiscDtos.OfferDto offer) {
    if (fm == null || offer == null) return;
    OfferDetailDialog d = new OfferDetailDialog();
    Bundle args = new Bundle();
    args.putString(ARG_OFFER + ".id", offer.id);
    args.putString(ARG_OFFER + ".title", offer.title);
    args.putString(ARG_OFFER + ".subtitle", offer.subtitle);
    args.putString(ARG_OFFER + ".sku", offer.sku);
    args.putInt(ARG_OFFER + ".coins", offer.coins);
    args.putInt(ARG_OFFER + ".bonusCoins", offer.bonusCoins);
    args.putDouble(ARG_OFFER + ".priceUsd", offer.priceUsd);
    args.putString(ARG_OFFER + ".lottieUrl", offer.lottieUrl);
    d.setArguments(args);
    d.show(fm, "offer_detail");
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    DialogOfferDetailBinding binding = DialogOfferDetailBinding.inflate(getLayoutInflater());
    MiscDtos.OfferDto offer = readOffer();
    AppContainer c = ContainerProvider.from(requireActivity());

    binding.tvTitle.setText(offer.title != null ? offer.title : "عرض");
    binding.tvSubtitle.setText(offer.subtitle != null ? offer.subtitle : "");
    int total = offer.coins + Math.max(0, offer.bonusCoins);
    binding.tvCoins.setText(String.format(Locale.US, "%,d عملة", total));
    binding.btnBuy.setText("اشترِ · …");
    OffersBottomSheet.bindLottie(binding.lottieOffer, offer.lottieUrl);

    BottomSheetDialog dialog = AuraDialogHelper.bottomSheet(requireContext());
    AuraDialogHelper.applyContent(binding.getRoot());
    dialog.setContentView(binding.getRoot());

    binding.btnClose.setOnClickListener(v -> dismiss());
    binding.btnBuy.setOnClickListener(v -> purchase(offer, binding, c));

    if (offer.sku != null && !offer.sku.isEmpty()) {
      c.getBillingHelper().queryProducts(Collections.singletonList(offer.sku), () -> {
        c.getBillingHelper().applyPlayPricesToOffers(Collections.singletonList(offer));
        binding.btnBuy.setText("اشترِ · " + offer.displayPrice());
      });
    } else {
      binding.btnBuy.setText("اشترِ · " + offer.displayPrice());
    }
    return dialog;
  }

  private void purchase(MiscDtos.OfferDto offer, DialogOfferDetailBinding binding, AppContainer c) {
    if (offer.sku == null || offer.sku.isEmpty()) {
      Toast.makeText(requireContext(), R.string.error_generic, Toast.LENGTH_SHORT).show();
      return;
    }
    if (!(requireActivity() instanceof AppCompatActivity)) return;
    binding.btnBuy.setEnabled(false);
    c.getBillingHelper().queryProducts(Collections.singletonList(offer.sku), () ->
        c.getBillingHelper().launchPurchase(requireActivity(), offer.sku,
            new com.Dramizo.Series.billing.BillingHelper.PurchaseCallback() {
              @Override
              public void onPurchaseSuccess(String sku, String purchaseToken, String orderId) {
                int total = offer.coins + Math.max(0, offer.bonusCoins);
                c.getIoExecutor().execute(() -> {
                  Result<WalletDtos.WalletDto> r = c.getWalletRepository().verifyPurchase(
                      sku, purchaseToken, orderId, total, offer.amountForVerify());
                  requireActivity().runOnUiThread(() -> {
                    binding.btnBuy.setEnabled(true);
                    if (r.success) {
                      c.getBillingHelper().consumePendingPurchase();
                      Toast.makeText(requireContext(), R.string.purchase, Toast.LENGTH_SHORT).show();
                      dismiss();
                    } else {
                      Toast.makeText(requireContext(),
                          r.error != null ? r.error : getString(R.string.error_generic),
                          Toast.LENGTH_LONG).show();
                    }
                  });
                });
              }

              @Override
              public void onPurchaseError(String message) {
                requireActivity().runOnUiThread(() -> {
                  binding.btnBuy.setEnabled(true);
                  Toast.makeText(requireContext(),
                      message != null ? message : getString(R.string.error_generic),
                      Toast.LENGTH_LONG).show();
                });
              }
            }));
  }

  private MiscDtos.OfferDto readOffer() {
    Bundle args = getArguments();
    MiscDtos.OfferDto offer = new MiscDtos.OfferDto();
    if (args == null) return offer;
    offer.id = args.getString(ARG_OFFER + ".id");
    offer.title = args.getString(ARG_OFFER + ".title");
    offer.subtitle = args.getString(ARG_OFFER + ".subtitle");
    offer.sku = args.getString(ARG_OFFER + ".sku");
    offer.coins = args.getInt(ARG_OFFER + ".coins");
    offer.bonusCoins = args.getInt(ARG_OFFER + ".bonusCoins");
    offer.priceUsd = args.getDouble(ARG_OFFER + ".priceUsd");
    offer.lottieUrl = args.getString(ARG_OFFER + ".lottieUrl");
    return offer;
  }
}
