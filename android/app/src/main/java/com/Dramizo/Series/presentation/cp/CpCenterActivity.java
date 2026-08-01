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
import com.Dramizo.Series.presentation.friends.RequestsActivity;
import com.Dramizo.Series.presentation.invite.InvitationActivity;
import com.Dramizo.Series.presentation.ranking.RankingActivity;
import com.Dramizo.Series.util.ApiCall;
import com.Dramizo.Series.util.AvatarImageLoader;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Mikoo-style CP hub with real relation bind via social requests. */
public class CpCenterActivity extends ThemedActivity {
    private ActivityCpCenterBinding binding;
    private AppContainer c;
    @Nullable private AuthDtos.UserDto partner;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCpCenterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        c = ContainerProvider.from(this);

        binding.btnBack.setOnClickListener(v -> navigateUp());
        binding.btnBecomeCp.setOnClickListener(v -> {
            if (partner != null) {
                Intent i = new Intent(this, RequestsActivity.class);
                i.putExtra(RequestsActivity.EXTRA_TAB, 2);
                startActivity(i);
            } else {
                showSendCpDialog();
            }
        });
        binding.btnOpenInvite.setOnClickListener(v ->
                startActivity(new Intent(this, InvitationActivity.class)));
        binding.btnCpWealth.setOnClickListener(v -> openRanking("rich"));
        binding.btnCpCharm.setOnClickListener(v -> openRanking("popular"));
        loadPartner();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPartner();
    }

    private void loadPartner() {
        c.getIoExecutor().execute(() -> {
            Result<List<MiscDtos.SocialRequestDto>> r =
                    ApiCall.execute(c.getUserApi().relations("relation"));
            runOnUiThread(() -> {
                if (isFinishing() || binding == null) return;
                partner = null;
                if (r.success && r.data != null) {
                    for (MiscDtos.SocialRequestDto row : r.data) {
                        if (row != null && row.user != null) {
                            partner = row.user;
                            break;
                        }
                    }
                }
                bindPartnerUi();
            });
        });
    }

    private void bindPartnerUi() {
        if (partner != null) {
            binding.cardCpPartner.setVisibility(View.VISIBLE);
            String name = partner.displayName != null && !partner.displayName.isEmpty()
                    ? partner.displayName
                    : (partner.username != null ? partner.username : "شريك");
            binding.tvCpPartnerName.setText(name);
            AvatarImageLoader.load(binding.imgCpPartner, partner.avatarUrl);
            binding.tvCpHeroTitle.setText("مساحة CP");
            binding.tvCpHint.setText("أنتم الآن CP. يمكنكم إدارة الطلبات والعلاقات من صفحة العلاقات.");
            binding.btnBecomeCp.setText("إدارة العلاقات");
        } else {
            binding.cardCpPartner.setVisibility(View.GONE);
            binding.tvCpHeroTitle.setText("كيف تصبح CP؟");
            binding.tvCpHint.setText(
                    "CP هو رابط خاص بينك وبين صديق/صديقة.\nأدخل معرّف الصديق لإرسال طلب CP، وبعد القبول تظهر مساحتكما هنا.");
            binding.btnBecomeCp.setText("أصبح CP · دعوة صديق");
        }
    }

    private void showSendCpDialog() {
        EditText input = new EditText(this);
        input.setHint("معرّف المستخدم أو الاسم");
        input.setPadding(48, 32, 48, 32);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("إرسال طلب CP")
                .setMessage("أدخل public ID أو اسم المستخدم للصديق")
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
                Toast.makeText(this, "تم إرسال طلب CP إلى " + name, Toast.LENGTH_LONG).show();
            });
        });
    }

    private void openRanking(String tab) {
        Intent i = new Intent(this, RankingActivity.class);
        i.putExtra(RankingActivity.EXTRA_CATEGORY, tab);
        startActivity(i);
    }
}
