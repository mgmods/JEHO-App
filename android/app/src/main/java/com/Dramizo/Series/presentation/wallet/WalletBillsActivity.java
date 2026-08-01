package com.Dramizo.Series.presentation.wallet;

import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.databinding.ActivityWalletBillsBinding;
import com.Dramizo.Series.presentation.profile.GiftHistoryActivity;

/** Mikoo-style bill hub: gifts, diamonds, coins, recharge agent. */
public class WalletBillsActivity extends ThemedActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityWalletBillsBinding binding = ActivityWalletBillsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());

        bindRow(binding.rowGiftsIn.getRoot(), R.drawable.icon_bills_gift,
                R.string.bill_gifts_received,
                v -> startActivity(new Intent(this, GiftHistoryActivity.class)));
        bindRow(binding.rowGiftsOut.getRoot(), R.drawable.icon_bill_gift_expends,
                R.string.bill_gifts_sent,
                v -> startActivity(new Intent(this, GiftHistoryActivity.class)));
        bindRow(binding.rowDiamonds.getRoot(), R.drawable.icon_bills_withdraw,
                R.string.bill_diamond_record,
                v -> openBagTab(1));
        bindRow(binding.rowDiamondTransfer.getRoot(), R.drawable.icon_bills_withdraw1,
                R.string.bill_diamond_transfer,
                v -> openBagTab(1));
        bindRow(binding.rowGirlCategories.getRoot(), R.drawable.icon_bills_red,
                R.string.bill_girl_categories,
                v -> startActivity(new Intent(this, EarningsActivity.class)));
        bindRow(binding.rowDebris.getRoot(), R.mipmap.ic_debris,
                R.string.bill_debris,
                v -> openBagTab(0));
        binding.rowDebris.getRoot().setVisibility(View.GONE);
        bindRow(binding.rowCoins.getRoot(), R.drawable.icon_bills_charge,
                R.string.bill_coins_record,
                v -> openBagTab(0));
        bindRow(binding.rowRechargeAgent.getRoot(), R.drawable.icon_bills_charge,
                R.string.recharge_agent,
                v -> startActivity(new Intent(this, RechargeAgentActivity.class)));
    }

    private void openBagTab(int tab) {
        Intent i = new Intent(this, BagActivity.class);
        i.putExtra(BagActivity.EXTRA_TAB, tab);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private static void bindRow(View row, int iconRes, int titleRes, View.OnClickListener click) {
        ImageView icon = row.findViewById(R.id.imgIcon);
        TextView title = row.findViewById(R.id.tvTitle);
        if (icon != null) icon.setImageResource(iconRes);
        if (title != null) title.setText(titleRes);
        row.setOnClickListener(click);
    }
}
