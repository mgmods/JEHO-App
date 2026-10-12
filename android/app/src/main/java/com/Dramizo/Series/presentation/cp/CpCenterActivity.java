package com.Dramizo.Series.presentation.cp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.databinding.ActivityCpCenterBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.EdgeToEdgeHelper;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.presentation.common.UserProfileCardSheet;
import com.Dramizo.Series.presentation.friends.RequestsActivity;
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
        binding.btnOpenInvite.setOnLongClickListener(v -> {
            Intent i = new Intent(this, RequestsActivity.class);
            i.putExtra(RequestsActivity.EXTRA_TAB, 2);
            startActivity(i);
            return true;
        });
        binding.btnCpWealth.setOnClickListener(v -> openRanking("rich"));
        binding.btnCpCharm.setOnClickListener(v -> openRanking("popular"));
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
        try { return value == null ? 0L : Long.parseLong(String.valueOf(value)); }
        catch (Exception ignored) { return 0L; }
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
                    Result<Object> r = ApiCall.execute(
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
                            .setMessage("لا توجد طلبات معلّقة حالياً.")
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
                            + " · " + valueOf(row.get("priceCoins")) + " عملة";
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
                        .setPositiveButton("إغلاق", null).show();
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
                Toast.makeText(this, result.success
                        ? (accept ? "تم قبول الطلب وإنشاء العلاقة" : "تم رفض الطلب وإرجاع المبلغ")
                        : (result.error != null ? result.error : "تعذرت معالجة الطلب"), Toast.LENGTH_LONG).show();
                if (result.success) loadPartner();
            });
        });
    }

    private void showSendCpDialog() {
        EditText input = new EditText(this);
        input.setHint("معرّف المستخدم أو الاسم");
        input.setPadding(48, 32, 48, 32);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("إرسال طلب CP")
                .setMessage("أدخل public ID أو اسم المستخدم. الطرف يقبل من الطلبات.")
                .setView(input)
                .setPositiveButton("إرسال", (d, w) -> {
                    String q = input.getText() != null ? input.getText().toString().trim() : "";
                    if (q.isEmpty()) {
                        Toast.makeText(this, "أدخل معرّفاً صالحاً", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    sendCpRequest(q);
                })
                .setNegativeButton("إلغاء", null)
                .setNeutralButton("طلباتي", (d, w) -> {
                    Intent i = new Intent(this, RequestsActivity.class);
                    i.putExtra(RequestsActivity.EXTRA_TAB, 2);
                    startActivity(i);
                })
                .show();
    }

    private void sendCpRequest(String query) {
        Toast.makeText(this, "جارٍ البحث...", Toast.LENGTH_SHORT).show();
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.ListResult<AuthDtos.UserDto>> search =
                    ApiCall.execute(c.getUserApi().search(query, 1));
            AuthDtos.UserDto target = null;
            if (search.success && search.data != null && search.data.items != null) {
                for (AuthDtos.UserDto u : search.data.items) {
                    if (u == null) continue;
                    if (query.equals(u.publicId) || query.equalsIgnoreCase(u.username)
                            || query.equals(u.id)) {
                        target = u;
                        break;
                    }
                }
                if (target == null && !search.data.items.isEmpty()) {
                    target = search.data.items.get(0);
                }
            }
            if (target == null || target.id == null) {
                runOnUiThread(() -> Toast.makeText(this, "لم يتم العثور على المستخدم",
                        Toast.LENGTH_SHORT).show());
                return;
            }
            String myId = c.getSessionManager().getUserId();
            if (myId != null && myId.equals(target.id)) {
                runOnUiThread(() -> Toast.makeText(this, "لا يمكن إرسال طلب لنفسك",
                        Toast.LENGTH_SHORT).show());
                return;
            }
            Map<String, String> body = new HashMap<>();
            body.put("type", "relation");
            Result<Object> sent = ApiCall.execute(c.getUserApi().sendRequest(target.id, body));
            final String name = target.displayName != null ? target.displayName : target.username;
            runOnUiThread(() -> {
                if (!sent.success) {
                    Toast.makeText(this,
                            sent.error != null ? sent.error : "فشل إرسال الطلب",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(this,
                        "تم إرسال طلب CP إلى " + name + " — ينتظر القبول",
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    private void openRanking(String tab) {
        Intent i = new Intent(this, RankingActivity.class);
        i.putExtra(RankingActivity.EXTRA_CATEGORY, tab);
        startActivity(i);
    }
}
