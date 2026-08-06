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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCpCenterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        c = ContainerProvider.from(this);

        binding.btnBack.setOnClickListener(v -> navigateUp());
        binding.btnBecomeCp.setOnClickListener(v -> {
            if (partner != null) {
                confirmEndCp();
            } else {
                showSendCpDialog();
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

    @Override
    protected void onResume() {
        super.onResume();
        loadPartner();
    }

    private void loadPartner() {
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.CpStatusDto> status = ApiCall.execute(c.getUserApi().myCp());
            Result<List<MiscDtos.SocialRequestDto>> pending =
                    ApiCall.execute(c.getUserApi().requests("relation"));
            final int pendingCount = (pending.success && pending.data != null)
                    ? pending.data.size() : 0;
            AuthDtos.UserDto found = null;
            long score = 0;
            int level = 0;
            if (status.success && status.data != null && status.data.hasCp
                    && status.data.partner != null) {
                found = status.data.partner;
                score = Math.max(0, status.data.bondScore);
                level = Math.max(0, status.data.level);
            } else if (!status.success) {
                Result<List<MiscDtos.SocialRequestDto>> r =
                        ApiCall.execute(c.getUserApi().relations("relation"));
                if (r.success && r.data != null) {
                    for (MiscDtos.SocialRequestDto row : r.data) {
                        if (row != null && row.user != null) {
                            found = row.user;
                            break;
                        }
                    }
                }
            }
            final AuthDtos.UserDto partnerRes = found;
            final long scoreRes = score;
            final int levelRes = level;
            runOnUiThread(() -> {
                if (isFinishing() || binding == null) return;
                partner = partnerRes;
                bondScore = scoreRes;
                bondLevel = levelRes;
                bindPartnerUi(pendingCount);
            });
        });
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
            binding.tvCpHint.setText(String.format(Locale.US,
                    "مرتبطان كـ CP · المستوى LV.%d\nنقاط الألفة: %,d (من هدايا CP بينكما)\n"
                            + "أرسل هدايا تبويب CP في الغرفة لرفع المستوى.\n"
                            + "اضغط البطاقة لملف الشريك.",
                    bondLevel, bondScore));
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
            binding.btnBecomeCp.setText("أرسل طلب CP");
        }
    }

    private void confirmEndCp() {
        if (partner == null || partner.id == null) return;
        String name = partner.displayName != null ? partner.displayName : "الشريك";
        AuraDialogHelper.confirm(this,
                "إنهاء CP",
                "هل تريد إنهاء ارتباط CP مع " + name + "؟",
                "إنهاء",
                () -> c.getIoExecutor().execute(() -> {
                    Result<Object> r = ApiCall.execute(
                            c.getUserApi().endBond(partner.id, "relation"));
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
