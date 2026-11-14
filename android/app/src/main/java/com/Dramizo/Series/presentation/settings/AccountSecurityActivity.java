package com.Dramizo.Series.presentation.settings;
import com.Dramizo.Series.presentation.common.ThemedActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.Dramizo.Series.R;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.databinding.ActivityAccountSecurityBinding;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.util.AuraDialogHelper;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class AccountSecurityActivity extends ThemedActivity {
    private static final String SUPPORT_EMAIL = "support@adnova.bbs.tr";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityAccountSecurityBinding binding = ActivityAccountSecurityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());

        AuthDtos.UserDto u = ContainerProvider.from(this).getSessionManager().getUser();
        String email = u != null && u.email != null && !u.email.isEmpty() ? u.email : "—";
        String phone = u != null && u.phone != null && !u.phone.isEmpty() ? u.phone : "—";
        binding.tvEmail.setText(getString(R.string.account_email_label, email));
        binding.tvPhone.setText(getString(R.string.account_phone_label, phone));

        binding.btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        binding.btnResetPassword.setOnClickListener(v -> {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse("mailto:" + SUPPORT_EMAIL));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.forgot_password_email_subject));
            emailIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.forgot_password_email_body, email));
            try {
                startActivity(Intent.createChooser(emailIntent, getString(R.string.contact_support)));
            } catch (Exception e) {
                Toast.makeText(this, getString(R.string.email_support_hint, SUPPORT_EMAIL), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showChangePasswordDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, pad);
        TextView title = new TextView(this);
        title.setText(R.string.change_password);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTextSize(16);
        title.setPadding(0, 0, 0, pad / 2);
        EditText current = new EditText(this);
        current.setHint(getString(R.string.current_password));
        current.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText neu = new EditText(this);
        neu.setHint(getString(R.string.new_password));
        neu.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText confirm = new EditText(this);
        confirm.setHint(getString(R.string.confirm_password));
        confirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextView btnSave = new TextView(this);
        btnSave.setText(R.string.save);
        btnSave.setTextColor(getColor(R.color.aurora_mint));
        btnSave.setTextSize(15);
        btnSave.setPadding(pad, pad / 2, pad, pad / 2);
        btnSave.setGravity(android.view.Gravity.CENTER);
        TextView btnCancel = new TextView(this);
        btnCancel.setText(android.R.string.cancel);
        btnCancel.setTextColor(getColor(R.color.text_secondary));
        btnCancel.setTextSize(15);
        btnCancel.setPadding(pad, pad / 2, pad, pad / 2);
        btnCancel.setGravity(android.view.Gravity.CENTER);
        box.addView(title);
        box.addView(current);
        box.addView(neu);
        box.addView(confirm);
        box.addView(btnSave);
        box.addView(btnCancel);

        BottomSheetDialog dialog = AuraDialogHelper.bottomSheet(this);
        AuraDialogHelper.applyContent(box);
        dialog.setContentView(box);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String cur = text(current);
            String n = text(neu);
            String c = text(confirm);
            if (cur.length() < 6 || n.length() < 6) {
                Toast.makeText(this, R.string.password_min_length, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!n.equals(c)) {
                Toast.makeText(this, R.string.password_mismatch, Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            AppContainer container = ContainerProvider.from(this);
            container.getIoExecutor().execute(() -> {
                Result<Object> r = container.getAuthRepository().changePassword(cur, n);
                runOnUiThread(() -> {
                    if (r.success) {
                        Toast.makeText(this, R.string.password_changed, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this,
                                r.error != null ? r.error : getString(R.string.error_generic),
                                Toast.LENGTH_LONG).show();
                    }
                });
            });
        });
        dialog.show();
    }

    private static String text(EditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
