package com.Dramizo.Series.presentation.wallet;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.Dramizo.Series.R;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.Dramizo.Series.util.AvatarCosmetics;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Internal sell-agent portal only (admin assign + float).
 * Paid membership / WhatsApp directory join flow was retired.
 */
public class RechargeAgentActivity extends ThemedActivity {
    private static final DecimalFormat MONEY = new DecimalFormat("0.##");

    private AppContainer container;
    private ProgressBar progress;
    private LinearLayout cardStatus;
    private LinearLayout cardSell;

    private Map<String, Object> pricing = new HashMap<>();
    private String verifiedRecipient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recharge_agent);
        EdgeToEdgeHelper.apply(this);
        EdgeToEdgeHelper.padSystemBars(findViewById(R.id.header));
        container = ContainerProvider.from(this);

        progress = findViewById(R.id.progress);
        cardStatus = findViewById(R.id.cardStatus);
        cardSell = findViewById(R.id.cardSell);

        findViewById(R.id.btnBack).setOnClickListener(v -> navigateUp());
        loadStatus();
    }

    private void loadStatus() {
        setLoading(true);
        hideAllPanels();
        container.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> result =
                    ApiCall.execute(container.getWalletApi().rechargeAgentMe());
            runOnUiThread(() -> {
                setLoading(false);
                if (!result.success || result.data == null) {
                    Toast.makeText(this,
                            result.error != null ? result.error : getString(R.string.error_generic),
                            Toast.LENGTH_LONG).show();
                    showStatusCard(
                            "لا يوجد حساب وكيل بيع داخلي",
                            "يُعيَّن وكلاء البيع الداخلي فقط من إدارة التطبيق.",
                            false);
                    return;
                }
                pricing = mutableMap(asMap(result.data.get("pricing")));
                Map<?, ?> agent = asMap(result.data.get("agent"));
                if (agent != null && "active".equals(String.valueOf(agent.get("status")))) {
                    setScreenTitle("لوحة وكيل البيع الداخلي");
                    showSellUi(agent);
                    return;
                }
                if (agent != null && "suspended".equals(String.valueOf(agent.get("status")))) {
                    setScreenTitle("وكيل البيع الداخلي");
                    showStatusCard("حساب الوكيل معلّق",
                            "السبب: " + string(agent.get("notes")), false);
                    return;
                }
                setScreenTitle("وكيل البيع الداخلي");
                showStatusCard(
                        "لا يوجد حساب وكيل بيع داخلي",
                        "يُعيَّن وكلاء البيع الداخلي فقط من إدارة التطبيق مع رصيد (float).",
                        false);
            });
        });
    }

    private void hideAllPanels() {
        cardStatus.setVisibility(View.GONE);
        cardSell.setVisibility(View.GONE);
        cardSell.removeAllViews();
        cardStatus.removeAllViews();
    }

    private void showStatusCard(String title, String body, boolean ok) {
        cardStatus.setVisibility(View.VISIBLE);
        cardStatus.removeAllViews();
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(18);
        t.setTextColor(ok ? getColor(R.color.aurora_gold) : getColor(R.color.aurora_coral));
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView b = new TextView(this);
        b.setText(body);
        b.setTextSize(14);
        b.setTextColor(0xFF1A1A1A);
        b.setPadding(0, dp(8), 0, 0);
        cardStatus.addView(t);
        cardStatus.addView(b);
    }

    private void showSellUi(Map<?, ?> agent) {
        cardSell.setVisibility(View.VISIBLE);
        cardSell.removeAllViews();
        View dash = getLayoutInflater().inflate(R.layout.include_recharge_agent_dashboard, cardSell, false);
        cardSell.addView(dash);

        TextView tvAgentFloat = dash.findViewById(R.id.tvAgentFloat);
        TextView tvAgentDaily = dash.findViewById(R.id.tvAgentDaily);
        TextView tabSell = dash.findViewById(R.id.tabSell);
        TextView tabWithdraws = dash.findViewById(R.id.tabWithdraws);
        TextView tabHistory = dash.findViewById(R.id.tabHistory);
        TextView tabLookup = dash.findViewById(R.id.tabLookup);
        View panelSell = dash.findViewById(R.id.panelSell);
        View panelLookup = dash.findViewById(R.id.panelLookup);
        View panelHistory = dash.findViewById(R.id.panelHistory);
        View panelWithdraws = dash.findViewById(R.id.panelWithdraws);

        if (tvAgentFloat != null) {
            tvAgentFloat.setText(formatLong(agent.get("floatCoins")) + " عملة");
        }
        if (tvAgentDaily != null) {
            tvAgentDaily.setText("مبيعات اليوم: " + formatLong(agent.get("dailySoldCoins"))
                    + " / " + formatLong(agent.get("dailyLimitCoins")));
        }

        View.OnClickListener onTab = v -> {
            int which = 0;
            if (v.getId() == R.id.tabWithdraws) which = 1;
            else if (v.getId() == R.id.tabHistory) which = 2;
            else if (v.getId() == R.id.tabLookup) which = 3;
            paintAgentTab(tabSell, which == 0);
            paintAgentTab(tabWithdraws, which == 1);
            paintAgentTab(tabHistory, which == 2);
            paintAgentTab(tabLookup, which == 3);
            if (panelSell != null) panelSell.setVisibility(which == 0 ? View.VISIBLE : View.GONE);
            if (panelWithdraws != null) panelWithdraws.setVisibility(which == 1 ? View.VISIBLE : View.GONE);
            if (panelHistory != null) panelHistory.setVisibility(which == 2 ? View.VISIBLE : View.GONE);
            if (panelLookup != null) panelLookup.setVisibility(which == 3 ? View.VISIBLE : View.GONE);
        };
        if (tabSell != null) tabSell.setOnClickListener(onTab);
        if (tabWithdraws != null) tabWithdraws.setOnClickListener(onTab);
        if (tabHistory != null) tabHistory.setOnClickListener(onTab);
        if (tabLookup != null) tabLookup.setOnClickListener(onTab);
        paintAgentTab(tabSell, true);

        TextInputEditText etRecipient = dash.findViewById(R.id.etRecipient);
        TextInputEditText etCoins = dash.findViewById(R.id.etCoins);
        TextInputEditText etRetail = dash.findViewById(R.id.etRetail);
        TextView tvVerified = dash.findViewById(R.id.tvVerified);
        TextView tvSellTotal = dash.findViewById(R.id.tvSellTotal);
        MaterialButton btnVerify = dash.findViewById(R.id.btnVerify);
        MaterialButton btnSell = dash.findViewById(R.id.btnSell);
        View cardRecipient = dash.findViewById(R.id.cardRecipient);
        if (etRetail != null) {
            etRetail.setText(String.valueOf(decimal(pricing.get("suggestedRetailPer100CoinsUsdt"))));
        }
        if (btnSell != null) btnSell.setEnabled(false);
        hideUserCard(cardRecipient);

        Runnable updateTotal = () -> {
            if (tvSellTotal == null) return;
            long coins = parseLong(value(etCoins));
            double retail = parseDouble(value(etRetail));
            tvSellTotal.setText("السعر النهائي للعميل: "
                    + MONEY.format((coins / 100.0) * retail) + " USDT");
        };
        if (etCoins != null) etCoins.addTextChangedListener(simpleWatcher(updateTotal));
        if (etRetail != null) etRetail.addTextChangedListener(simpleWatcher(updateTotal));
        updateTotal.run();

        if (btnVerify != null) {
            btnVerify.setOnClickListener(v -> {
                verifiedRecipient = null;
                if (btnSell != null) btnSell.setEnabled(false);
                hideUserCard(cardRecipient);
                btnVerify.setEnabled(false);
                container.getIoExecutor().execute(() -> {
                    Result<Map<String, Object>> result = ApiCall.execute(
                            container.getWalletApi().resolveRechargeRecipient(value(etRecipient)));
                    runOnUiThread(() -> {
                        btnVerify.setEnabled(true);
                        if (!result.success || result.data == null) {
                            hideUserCard(cardRecipient);
                            if (tvVerified != null) {
                                tvVerified.setText("لم يتم العثور على المستخدم");
                                tvVerified.setTextColor(getColor(R.color.aurora_coral));
                            }
                            return;
                        }
                        if (etRecipient != null) {
                            String pid = string(result.data.get("publicId"));
                            etRecipient.setText(pid != null && !pid.isEmpty()
                                    ? pid : string(result.data.get("username")));
                        }
                        verifiedRecipient = string(result.data.get("publicId"));
                        if (verifiedRecipient == null || verifiedRecipient.isEmpty()
                                || "—".equals(verifiedRecipient)) {
                            verifiedRecipient = string(result.data.get("id"));
                        }
                        bindUserCard(
                                cardRecipient,
                                string(result.data.get("displayName")),
                                string(result.data.get("publicId")),
                                string(result.data.get("username")),
                                string(result.data.get("avatarUrl")));
                        if (tvVerified != null) {
                            tvVerified.setText("تم التحقق — جاهز للشحن");
                            tvVerified.setTextColor(getColor(R.color.aurora_mint));
                        }
                        if (btnSell != null) btnSell.setEnabled(true);
                    });
                });
            });
        }
        if (etRecipient != null) {
            etRecipient.addTextChangedListener(simpleWatcher(() -> {
                verifiedRecipient = null;
                if (btnSell != null) btnSell.setEnabled(false);
                hideUserCard(cardRecipient);
                if (tvVerified != null) {
                    tvVerified.setText("اضغط تحقق من المستخدم");
                    tvVerified.setTextColor(0xFF595959);
                }
            }));
        }
        if (btnSell != null) {
            btnSell.setOnClickListener(v -> {
                int amount = (int) parseLong(value(etCoins));
                if (verifiedRecipient == null || amount <= 0) {
                    Toast.makeText(this, "تحقق من المستخدم وأدخل كمية صحيحة", Toast.LENGTH_SHORT)
                            .show();
                    return;
                }
                String summary = tvSellTotal != null ? String.valueOf(tvSellTotal.getText()) : "";
                String recipientName = "";
                if (cardRecipient != null) {
                    TextView n = cardRecipient.findViewById(R.id.tvUserDisplayName);
                    if (n != null && n.getText() != null) recipientName = n.getText().toString();
                }
                if (recipientName.isEmpty()) {
                    recipientName = verifiedRecipient;
                }
                AuraDialogHelper.confirm(this,
                        "تأكيد الشحن",
                        "سيتم تحويل " + amount + " عملة إلى " + recipientName + "\n" + summary,
                        "تأكيد",
                        () -> {
                            Map<String, Object> body = new HashMap<>();
                            body.put("recipientUsernameOrId", verifiedRecipient);
                            body.put("coins", amount);
                            body.put("idempotencyKey", UUID.randomUUID().toString());
                            btnSell.setEnabled(false);
                            container.getIoExecutor().execute(() -> {
                                Result<Map<String, Object>> result = ApiCall.execute(
                                        container.getWalletApi().sellAgentRecharge(body));
                                runOnUiThread(() -> {
                                    btnSell.setEnabled(true);
                                    Toast.makeText(this,
                                            result.success ? "تم شحن العملات بنجاح"
                                                    : (result.error != null ? result.error
                                                    : getString(R.string.error_generic)),
                                            Toast.LENGTH_LONG).show();
                                    if (result.success) loadStatus();
                                });
                            });
                        },
                        "إلغاء",
                        null);
            });
        }

        TextInputEditText etLookup = dash.findViewById(R.id.etLookup);
        MaterialButton btnLookup = dash.findViewById(R.id.btnLookup);
        TextView tvLookupResult = dash.findViewById(R.id.tvLookupResult);
        View cardLookupUser = dash.findViewById(R.id.cardLookupUser);
        View blockWalletStats = dash.findViewById(R.id.blockWalletStats);
        View blockGiftStats = dash.findViewById(R.id.blockGiftStats);
        TextView tvLookupCoins = dash.findViewById(R.id.tvLookupCoins);
        TextView tvLookupDiamonds = dash.findViewById(R.id.tvLookupDiamonds);
        TextView tvLookupGiftCount = dash.findViewById(R.id.tvLookupGiftCount);
        TextView tvLookupGiftDiamonds = dash.findViewById(R.id.tvLookupGiftDiamonds);
        TextView tvGiftsTitle = dash.findViewById(R.id.tvGiftsTitle);
        LinearLayout listRecentGifts = dash.findViewById(R.id.listRecentGifts);
        hideUserCard(cardLookupUser);
        setGone(blockWalletStats, blockGiftStats, tvGiftsTitle, listRecentGifts, tvLookupResult);

        if (btnLookup != null) {
            btnLookup.setOnClickListener(v -> {
                String q = value(etLookup);
                if (q.isEmpty()) {
                    Toast.makeText(this, "أدخل آي دي المستخدم", Toast.LENGTH_SHORT).show();
                    return;
                }
                btnLookup.setEnabled(false);
                hideUserCard(cardLookupUser);
                setGone(blockWalletStats, blockGiftStats, tvGiftsTitle, listRecentGifts);
                if (tvLookupResult != null) {
                    tvLookupResult.setVisibility(View.VISIBLE);
                    tvLookupResult.setText("جاري التحميل…");
                    tvLookupResult.setTextColor(getColor(R.color.text_secondary));
                }
                container.getIoExecutor().execute(() -> {
                    Result<Map<String, Object>> result = ApiCall.execute(
                            container.getWalletApi().agentUserOverview(q));
                    runOnUiThread(() -> {
                        btnLookup.setEnabled(true);
                        if (!result.success || result.data == null) {
                            hideUserCard(cardLookupUser);
                            setGone(blockWalletStats, blockGiftStats, tvGiftsTitle, listRecentGifts);
                            if (tvLookupResult != null) {
                                tvLookupResult.setVisibility(View.VISIBLE);
                                tvLookupResult.setText(result.error != null
                                        ? result.error : "تعذر التحميل");
                                tvLookupResult.setTextColor(getColor(R.color.aurora_coral));
                            }
                            return;
                        }
                        if (tvLookupResult != null) tvLookupResult.setVisibility(View.GONE);

                        Map<?, ?> user = mapOf(result.data.get("user"));
                        Map<?, ?> wallet = mapOf(result.data.get("wallet"));
                        Map<?, ?> gifts = mapOf(result.data.get("gifts"));

                        bindUserCard(
                                cardLookupUser,
                                string(user.get("displayName")),
                                string(user.get("publicId")),
                                string(user.get("username")),
                                string(user.get("avatarUrl")));

                        if (blockWalletStats != null) blockWalletStats.setVisibility(View.VISIBLE);
                        if (blockGiftStats != null) blockGiftStats.setVisibility(View.VISIBLE);
                        if (tvLookupCoins != null) {
                            tvLookupCoins.setText(formatLong(wallet.get("coins")));
                        }
                        if (tvLookupDiamonds != null) {
                            tvLookupDiamonds.setText(formatLong(wallet.get("diamonds")));
                        }
                        if (tvLookupGiftCount != null) {
                            tvLookupGiftCount.setText(formatLong(gifts.get("receivedCount")));
                        }
                        if (tvLookupGiftDiamonds != null) {
                            tvLookupGiftDiamonds.setText(formatLong(gifts.get("receivedDiamondsTotal")));
                        }

                        if (listRecentGifts != null) {
                            listRecentGifts.removeAllViews();
                            Object recentObj = gifts.get("recent");
                            if (recentObj instanceof java.util.List
                                    && !((java.util.List<?>) recentObj).isEmpty()) {
                                if (tvGiftsTitle != null) tvGiftsTitle.setVisibility(View.VISIBLE);
                                listRecentGifts.setVisibility(View.VISIBLE);
                                java.util.List<?> recent = (java.util.List<?>) recentObj;
                                int n = Math.min(10, recent.size());
                                for (int i = 0; i < n; i++) {
                                    Map<?, ?> g = mapOf(recent.get(i));
                                    String line = "• " + string(g.get("giftName"))
                                            + " من " + string(g.get("senderName"))
                                            + "  (+" + formatLong(g.get("diamondsAwarded")) + " 💎)";
                                    listRecentGifts.addView(label(line, 12, getColor(R.color.text_primary)));
                                }
                            } else {
                                if (tvGiftsTitle != null) tvGiftsTitle.setVisibility(View.VISIBLE);
                                listRecentGifts.setVisibility(View.VISIBLE);
                                listRecentGifts.addView(label("لا هدايا حديثة", 12, 0xFF595959));
                            }
                        }

                        String pid = string(user.get("publicId"));
                        if (!pid.isEmpty() && !"—".equals(pid) && etRecipient != null) {
                            etRecipient.setText(pid);
                            verifiedRecipient = null;
                            if (btnSell != null) btnSell.setEnabled(false);
                            hideUserCard(cardRecipient);
                        }
                    });
                });
            });
        }

        LinearLayout listHistory = dash.findViewById(R.id.listHistory);
        TextView btnRefreshHistory = dash.findViewById(R.id.btnRefreshHistory);
        final Runnable[] loadHistoryHolder = new Runnable[1];
        loadHistoryHolder[0] = () -> {
            if (btnRefreshHistory != null) btnRefreshHistory.setEnabled(false);
            container.getIoExecutor().execute(() -> {
                Result<Map<String, Object>> result = ApiCall.execute(
                        container.getWalletApi().agentRecharges());
                runOnUiThread(() -> {
                    if (btnRefreshHistory != null) btnRefreshHistory.setEnabled(true);
                    if (listHistory == null) return;
                    listHistory.removeAllViews();
                    if (!result.success || result.data == null) {
                        listHistory.addView(label(result.error != null ? result.error : "تعذر التحميل",
                                13, getColor(R.color.aurora_coral)));
                        return;
                    }
                    Object itemsObj = result.data.get("items");
                    if (!(itemsObj instanceof java.util.List) || ((java.util.List<?>) itemsObj).isEmpty()) {
                        listHistory.addView(label("لا توجد عمليات شحن بعد", 13, 0xFF595959));
                        return;
                    }
                    for (Object row : (java.util.List<?>) itemsObj) {
                        if (!(row instanceof Map)) continue;
                        Map<?, ?> item = (Map<?, ?>) row;
                        Map<?, ?> recipientMap = item.get("recipient") instanceof Map
                                ? (Map<?, ?>) item.get("recipient") : null;
                        String name = recipientMap != null
                                ? string(recipientMap.get("displayName")) : "مستخدم";
                        String line = formatLong(item.get("coins")) + " عملة → " + name
                                + " · " + string(item.get("createdAt"));
                        listHistory.addView(label(line, 12, 0xFF1A1A1A));
                    }
                });
            });
        };
        if (btnRefreshHistory != null) {
            btnRefreshHistory.setOnClickListener(v -> loadHistoryHolder[0].run());
        }
        loadHistoryHolder[0].run();

        LinearLayout listWithdraws = dash.findViewById(R.id.listWithdraws);
        TextView btnRefreshWithdraws = dash.findViewById(R.id.btnRefreshWithdraws);
        final Runnable[] loadWithdrawsHolder = new Runnable[1];
        loadWithdrawsHolder[0] = () -> {
            if (btnRefreshWithdraws != null) btnRefreshWithdraws.setEnabled(false);
            container.getIoExecutor().execute(() -> {
                Result<Map<String, Object>> result = ApiCall.execute(
                        container.getWalletApi().agentWithdraws());
                runOnUiThread(() -> {
                    if (btnRefreshWithdraws != null) btnRefreshWithdraws.setEnabled(true);
                    if (listWithdraws == null) return;
                    listWithdraws.removeAllViews();
                    if (!result.success || result.data == null) {
                        listWithdraws.addView(label(result.error != null ? result.error : "تعذر التحميل",
                                13, getColor(R.color.aurora_coral)));
                        return;
                    }
                    Object itemsObj = result.data.get("items");
                    if (!(itemsObj instanceof java.util.List) || ((java.util.List<?>) itemsObj).isEmpty()) {
                        listWithdraws.addView(label("لا توجد طلبات سحب حالياً", 13, 0xFF595959));
                        return;
                    }
                    for (Object rowObj : (java.util.List<?>) itemsObj) {
                        Map<?, ?> row = mapOf(rowObj);
                        Map<?, ?> u = mapOf(row.get("user"));
                        String status = string(row.get("status"));
                        String line = string(u.get("displayName"))
                                + " · ID " + string(u.get("publicId"))
                                + "\n" + formatLong(row.get("diamonds")) + " ألماس · "
                                + status;
                        listWithdraws.addView(label(line, 13, 0xFF1A1A1A));
                        if ("pending".equalsIgnoreCase(status) || "approved".equalsIgnoreCase(status)) {
                            LinearLayout actions = new LinearLayout(this);
                            actions.setOrientation(LinearLayout.HORIZONTAL);
                            Button ok = goldButton("تم الدفع");
                            Button no = outlinedButton("رفض");
                            String wid = string(row.get("id"));
                            ok.setOnClickListener(v2 ->
                                    agentReviewWithdraw(wid, true, loadWithdrawsHolder[0]));
                            no.setOnClickListener(v2 ->
                                    agentReviewWithdraw(wid, false, loadWithdrawsHolder[0]));
                            actions.addView(ok);
                            actions.addView(no);
                            listWithdraws.addView(actions);
                        }
                        View spacer = new View(this);
                        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, dp(10)));
                        listWithdraws.addView(spacer);
                    }
                });
            });
        };
        if (btnRefreshWithdraws != null) {
            btnRefreshWithdraws.setOnClickListener(v -> loadWithdrawsHolder[0].run());
        }
        loadWithdrawsHolder[0].run();
    }

    private void paintAgentTab(@Nullable TextView tab, boolean on) {
        if (tab == null) return;
        tab.setBackgroundResource(on
                ? R.drawable.bg_wallet_channel_on
                : R.drawable.bg_form_sheet_input);
        tab.setTextColor(on ? 0xFF1A1200 : getColor(R.color.text_primary));
    }

    private void setScreenTitle(@Nullable String title) {
        TextView tv = findViewById(R.id.tvScreenTitle);
        if (tv != null && title != null) tv.setText(title);
    }

    private void bindUserCard(
            @Nullable View card,
            @Nullable String displayName,
            @Nullable String publicId,
            @Nullable String username,
            @Nullable String avatarUrl) {
        if (card == null) return;
        card.setVisibility(View.VISIBLE);
        TextView tvName = card.findViewById(R.id.tvUserDisplayName);
        TextView tvId = card.findViewById(R.id.tvUserPublicId);
        TextView tvUser = card.findViewById(R.id.tvUserUsername);
        ImageView img = card.findViewById(R.id.imgUserAvatar);
        String name = displayName != null ? displayName.trim() : "";
        if (name.isEmpty() && username != null) name = username.trim();
        if (name.isEmpty() || "—".equals(name)) name = "مستخدم";
        if (tvName != null) tvName.setText(name);
        if (tvId != null) {
            String pid = publicId != null ? publicId.trim() : "";
            if ("—".equals(pid)) pid = "";
            tvId.setText(pid.isEmpty() ? "ID: —" : ("ID: " + pid));
        }
        if (tvUser != null) {
            String un = username != null ? username.trim() : "";
            if (!un.isEmpty() && !"—".equals(un) && !un.equalsIgnoreCase(name)) {
                tvUser.setVisibility(View.VISIBLE);
                tvUser.setText("@" + un);
            } else {
                tvUser.setVisibility(View.GONE);
            }
        }
        if (img != null) {
            AvatarCosmetics.bindAvatar(img, avatarUrl);
        }
    }

    private void hideUserCard(@Nullable View card) {
        if (card != null) card.setVisibility(View.GONE);
    }

    private void setGone(View... views) {
        if (views == null) return;
        for (View v : views) {
            if (v != null) v.setVisibility(View.GONE);
        }
    }

    private static String value(@Nullable TextInputEditText et) {
        if (et == null || et.getText() == null) return "";
        return et.getText().toString().trim();
    }

    private void agentReviewWithdraw(String id, boolean complete, Runnable onDone) {
        container.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> r = ApiCall.execute(complete
                    ? container.getWalletApi().agentCompleteWithdraw(id, new HashMap<>())
                    : container.getWalletApi().agentRejectWithdraw(id, new HashMap<>()));
            runOnUiThread(() -> {
                Toast.makeText(this,
                        r.success ? (complete ? "تم تأكيد الدفع" : "تم الرفض وإرجاع الألماس")
                                : (r.error != null ? r.error : getString(R.string.error_generic)),
                        Toast.LENGTH_LONG).show();
                if (r.success && onDone != null) onDone.run();
            });
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> mapOf(Object o) {
        if (o instanceof Map) return (Map<?, ?>) o;
        return new HashMap<>();
    }

    private Button outlinedButton(String text) {
        MaterialButton b = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(text);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    private TextView label(String value, float size, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setPadding(0, dp(4), 0, dp(4));
        return v;
    }

    private Button goldButton(String title) {
        MaterialButton b = new MaterialButton(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                getColor(R.color.aurora_gold)));
        b.setTextColor(getColor(R.color.aurora_night));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private TextWatcher simpleWatcher(Runnable action) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                action.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        };
    }

    private static Map<?, ?> asMap(Object value) {
        return value instanceof Map ? (Map<?, ?>) value : null;
    }

    private static Map<String, Object> mutableMap(Map<?, ?> source) {
        Map<String, Object> out = new HashMap<>();
        if (source != null) {
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                out.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return out;
    }

    private static String string(Object value) {
        return value == null ? "—" : String.valueOf(value);
    }

    private static long number(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        try { return Long.parseLong(String.valueOf(value)); } catch (Exception ignored) { return 0; }
    }

    private static long parseLong(String value) {
        try { return Long.parseLong(value); } catch (Exception ignored) { return 0; }
    }

    private static double decimal(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        return parseDouble(String.valueOf(value));
    }

    private static double parseDouble(String value) {
        try { return Double.parseDouble(value); } catch (Exception ignored) { return 0; }
    }

    private static String formatLong(Object value) {
        return String.format(Locale.US, "%,d", number(value));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
