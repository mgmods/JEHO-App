package com.Dramizo.Series.presentation.cp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.databinding.ActivityCpCenterBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.common.UserProfileCardSheet;
import com.Dramizo.Series.presentation.invite.InvitationActivity;
import com.Dramizo.Series.presentation.ranking.RankingActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.Dramizo.Series.util.AvatarImageLoader;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Full CP hub: request / accept path / intimacy / end bond / invite code. */
public class CpCenterActivity extends ThemedActivity {
    private ActivityCpCenterBinding binding;
    private AppContainer c;
    @Nullable private AuthDtos.UserDto partner;
    private long bondScore;
    private int bondLevel;
    private String activeRelationshipId;
    private String activeRelationshipType;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCpCenterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // Ensure toolbar clears status bar (root contentRoot is padded by ThemedActivity;
        // extra explicit pad is safe and covers any race before onPostCreate).
        EdgeToEdgeHelper.padSystemBars(binding.contentRoot);
        c = ContainerProvider.from(this);

        binding.btnBack.setOnClickListener(v -> navigateUp());
        binding.btnDiscoverCp.setOnClickListener(v -> startActivity(new Intent(this, CpDiscoveryActivity.class)));
        binding.btnCpRequests.setOnClickListener(v -> showCpRequestsDialog());
        binding.btnBecomeCp.setOnClickListener(v -> {
            if (partner != null) {
                confirmEndCp();
            } else {
                startActivity(new Intent(this, CpDiscoveryActivity.class));
            }
        });
        binding.btnOpenInvite.setOnClickListener(v ->
                startActivity(new Intent(this, InvitationActivity.class)));
        binding.cardCpPartner.setOnClickListener(v -> {
            if (partner == null || partner.id == null) return;
            UserProfileCardSheet.show(
                    this,
                    partner.id,
                    partner.displayName != null ? partner.displayName : partner.username,
                    partner.avatarUrl,
                    Math.max(0, partner.vipLevel),
                    Math.max(1, partner.level));
        });
        loadPartner();
    }

    /** Manual edge padding — avoid ThemedActivity targeting only the fixed 56dp header. */
    @Override
    protected boolean wantsContentSystemPadding() {
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPartner();
    }

    private void loadPartner() {
        c.getIoExecutor().execute(() -> {
            Result<List<Map<String, Object>>> relationships = ApiCall.execute(c.getUserApi().myCpRelationships());
            Result<List<Map<String, Object>>> pending = ApiCall.execute(c.getUserApi().cpRequests());
            int pendingCount = pending.success && pending.data != null ? pending.data.size() : 0;
            AuthDtos.UserDto found = null;
            String relationId = null;
            String relationType = null;
            long score = 0;
            int level = 1;
            if (relationships.success && relationships.data != null) {
                for (Map<String, Object> row : relationships.data) {
                    if (row == null || !"active".equals(String.valueOf(row.get("status")))) continue;
                    Object raw = row.get("partner");
                    if (!(raw instanceof Map)) continue;
                    @SuppressWarnings("unchecked")
                    Map<String, Object> peer = (Map<String, Object>) raw;
                    found = new AuthDtos.UserDto();
                    found.id = valueOf(peer.get("id"));
                    found.username = valueOf(peer.get("username"));
                    found.displayName = valueOf(peer.get("displayName"));
                    found.avatarUrl = valueOf(peer.get("avatarUrl"));
                    relationId = valueOf(row.get("id"));
                    relationType = valueOf(row.get("type"));
                    score = longOf(row.get("points"));
                    level = Math.max(1, (int) longOf(row.get("level")));
                    break;
                }
            }
            final AuthDtos.UserDto partnerResult = found;
            final String relationIdResult = relationId;
            final String relationTypeResult = relationType;
            final long scoreResult = score;
            final int levelResult = level;
            final int pendingResult = pendingCount;
            runOnUiThread(() -> {
                if (isFinishing() || binding == null) return;
                partner = partnerResult;
                activeRelationshipId = relationIdResult;
                activeRelationshipType = relationTypeResult;
                bondScore = scoreResult;
                bondLevel = levelResult;
                binding.btnCpRequests.setText(pendingResult > 0 ? "طلبات علاقات CP (" + pendingResult + ")" : "طلبات علاقات CP");
                bindPartnerUi(pendingResult);
            });
        });
    }

    private String valueOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private long longOf(Object value) {
        try {
            if (value == null) return 0L;
            if (value instanceof Number) return ((Number) value).longValue();
            return (long) Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) { return 0L; }
    }

    private void bindPartnerUi(int pendingIncoming) {
        if (partner != null) {
            binding.cardCpPartner.setVisibility(View.VISIBLE);
            String name = partner.displayName != null && !partner.displayName.isEmpty()
                    ? partner.displayName
                    : (partner.username != null ? partner.username : "شريك");
            binding.tvCpPartnerName.setText(name);
            AvatarImageLoader.load(binding.imgCpPartner, partner.avatarUrl);
            binding.tvCpHeroTitle.setText("مساحة CP");
            binding.tvCpHint.setText("علاقة " + cpLabel(activeRelationshipType)
                    + " · المستوى LV." + bondLevel + "\nنقاط العلاقة: " + bondScore
                    + "\nاضغط البطاقة لعرض ملف الطرف الآخر.");
            binding.btnBecomeCp.setText("إنهاء CP");
        } else {
            binding.cardCpPartner.setVisibility(View.GONE);
            binding.tvCpHeroTitle.setText("كيف تصبح CP؟");
            String pendingHint = pendingIncoming > 0
                    ? ("\nلديك " + pendingIncoming + " طلب CP معلّق — افتح الطلبات للقبول.")
                    : "";
            binding.tvCpHint.setText(
                    "CP = زوج خاص (طلب → قبول).\n"
                            + "1) أدخل معرّف الصديق (public ID)\n"
                            + "2) يقبلك من «الطلبات → CP»\n"
                            + "3) تبادلوا هدايا تبويب CP في الغرفة\n"
                            + "يمكنكم رابطاً واحداً فقط في نفس الوقت."
                            + pendingHint
                            + "\n\nاضغط مطوّلاً على «رمز دعوتي» لفتح طلبات CP.");
            binding.btnBecomeCp.setText("اكتشف العلاقات");
        }
    }

    private String cpLabel(String type) {
        if (type == null) return "CP";
        switch (type) {
            case "friendship": return "الصداقة";
            case "brotherhood": return "الأخوّة";
            case "love": return "الحب";
            case "engagement": return "الخطوبة";
            case "marriage": return "الزواج";
            default: return "CP";
        }
    }

    private void confirmEndCp() {
        if (partner == null || partner.id == null || activeRelationshipId == null) return;
        String name = partner.displayName != null ? partner.displayName : "الشريك";
        AuraDialogHelper.confirm(this,
                "إنهاء CP",
                "هل تريد إنهاء ارتباط CP مع " + name + "؟",
                "إنهاء",
                () -> c.getIoExecutor().execute(() -> {
                    Result<Map<String, Object>> r = ApiCall.execute(
                            c.getUserApi().endCpRelationship(activeRelationshipId));
                    runOnUiThread(() -> {
                        Toast.makeText(this,
                                r.success ? "تم إنهاء CP"
                                        : (r.error != null ? r.error : "فشل"),
                                Toast.LENGTH_SHORT).show();
                        if (r.success) loadPartner();
                    });
                }),
                getString(android.R.string.cancel),
                null);
    }


    private void showCpRequestsDialog() {
        c.getIoExecutor().execute(() -> {
            Result<List<Map<String, Object>>> result = ApiCall.execute(c.getUserApi().cpRequests());
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (!result.success || result.data == null) {
                    Toast.makeText(this, result.error != null ? result.error : "تعذر تحميل الطلبات", Toast.LENGTH_LONG).show();
                    return;
                }
                if (result.data.isEmpty()) {
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("طلبات علاقات CP")
                            .setMessage("لا توجد طلبات واردة معلّقة حالياً.")
                            .setNeutralButton("الطلبات المرسلة", (d, w) -> showCpSentRequestsDialog())
                            .setPositiveButton("حسناً", null).show();
                    return;
                }
                List<Map<String, Object>> rows = result.data;
                String[] labels = new String[rows.size()];
                for (int i = 0; i < rows.size(); i++) {
                    Map<String, Object> row = rows.get(i);
                    Object rawSender = row.get("sender");
                    String senderName = "عضو";
                    if (rawSender instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> sender = (Map<String, Object>) rawSender;
                        senderName = valueOf(sender.get("displayName"));
                        if (senderName.isEmpty()) senderName = valueOf(sender.get("username"));
                    }
                    labels[i] = senderName + " — " + cpLabel(valueOf(row.get("type")))
                            + " · " + valueOf(row.get("giftName")) + " · " + valueOf(row.get("priceCoins")) + " عملة";
                }
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("طلبات علاقات CP")
                        .setItems(labels, (dialog, which) -> {
                            Map<String, Object> row = rows.get(which);
                            String id = valueOf(row.get("id"));
                            String senderName = labels[which];
                            new androidx.appcompat.app.AlertDialog.Builder(this)
                                    .setTitle("طلب علاقة جديد")
                                    .setMessage(senderName + "\\nبقبولك، ستُنشأ العلاقة. إذا رفضت أو انتهت المهلة يُعاد المبلغ كاملاً لصاحب الطلب.")
                                    .setPositiveButton("قبول", (d, w) -> respondCpRequest(id, true))
                                    .setNegativeButton("رفض وإرجاع المبلغ", (d, w) -> respondCpRequest(id, false))
                                    .setNeutralButton("لاحقاً", null).show();
                        })
                        .setNeutralButton("الطلبات المرسلة", (d, w) -> showCpSentRequestsDialog())
                        .setPositiveButton("إغلاق", null).show();
            });
        });
    }


    private void showCpSentRequestsDialog() {
        c.getIoExecutor().execute(() -> {
            Result<List<Map<String, Object>>> result = ApiCall.execute(c.getUserApi().cpSentRequests());
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (!result.success || result.data == null) {
                    Toast.makeText(this, result.error != null ? result.error : "تعذر تحميل الطلبات المرسلة", Toast.LENGTH_LONG).show();
                    return;
                }
                if (result.data.isEmpty()) {
                    new androidx.appcompat.app.AlertDialog.Builder(this).setTitle("الطلبات المرسلة")
                            .setMessage("لم ترسل أي طلب CP بعد.")
                            .setPositiveButton("حسناً", null).show();
                    return;
                }
                List<Map<String, Object>> rows = result.data;
                List<Map<String, Object>> cancellable = new java.util.ArrayList<>();
                List<String> labels = new java.util.ArrayList<>();
                for (Map<String, Object> row : rows) {
                    String status = valueOf(row.get("status"));
                    String name = "عضو";
                    Object raw = row.get("recipient");
                    if (raw instanceof Map) {
                        @SuppressWarnings("unchecked") Map<String, Object> peer = (Map<String, Object>) raw;
                        name = valueOf(peer.get("displayName"));
                        if (name.isEmpty()) name = valueOf(peer.get("username"));
                    }
                    String label = name + " — " + cpLabel(valueOf(row.get("type"))) + " · " + status;
                    if ("pending".equals(status)) {
                        label += " · إلغاء واسترداد";
                        cancellable.add(row);
                        labels.add(label);
                    } else {
                        labels.add(label);
                        cancellable.add(null);
                    }
                }
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("الطلبات المرسلة")
                        .setItems(labels.toArray(new String[0]), (d, which) -> {
                            Map<String, Object> row = cancellable.get(which);
                            if (row == null) return;
                            String id = valueOf(row.get("id"));
                            new androidx.appcompat.app.AlertDialog.Builder(this)
                                    .setTitle("إلغاء طلب CP؟")
                                    .setMessage("سيتم إلغاء الطلب وإرجاع المبلغ كاملاً إذا لم تتم معالجته.")
                                    .setPositiveButton("إلغاء واسترداد", (a, b) -> cancelCpRequest(id))
                                    .setNegativeButton("رجوع", null).show();
                        })
                        .setPositiveButton("إغلاق", null).show();
            });
        });
    }

    private void cancelCpRequest(String id) {
        c.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> result = ApiCall.execute(c.getUserApi().cancelCpRequest(id));
            runOnUiThread(() -> {
                if (isFinishing()) return;
                String status = result.data == null ? "" : valueOf(result.data.get("status"));
                String message = !result.success
                        ? (result.error != null ? result.error : "تعذر الإلغاء")
                        : "expired".equals(status) ? "انتهت مهلة الطلب وتم إرجاع المبلغ"
                        : "تم إلغاء الطلب وإرجاع المبلغ";
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                if (result.success) loadPartner();
            });
        });
    }

    private void respondCpRequest(String requestId, boolean accept) {
        Map<String, Boolean> body = new HashMap<>();
        body.put("accept", accept);
        c.getIoExecutor().execute(() -> {
            Result<Map<String, Object>> result = ApiCall.execute(c.getUserApi().respondCpRequest(requestId, body));
            runOnUiThread(() -> {
                if (isFinishing()) return;
                String status = result.data == null ? "" : valueOf(result.data.get("status"));
                String message = !result.success
                        ? (result.error != null ? result.error : "تعذرت معالجة الطلب")
                        : "expired".equals(status) ? "انتهت مهلة الطلب وتم إرجاع المبلغ"
                        : accept ? "تم قبول الطلب وإنشاء العلاقة" : "تم رفض الطلب وإرجاع المبلغ";
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                if (result.success) loadPartner();
            });
        });
    }

    private void openRanking(String tab) {
        Intent i = new Intent(this, RankingActivity.class);
        i.putExtra(RankingActivity.EXTRA_CATEGORY, tab);
        startActivity(i);
    }
}
