package com.Dramizo.Series.presentation.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.Dramizo.Series.R;
import com.Dramizo.Series.databinding.ActivityLanguageBinding;
import com.Dramizo.Series.presentation.common.ContainerProvider;
import com.Dramizo.Series.presentation.common.ThemedActivity;
import com.Dramizo.Series.util.LocaleHelper;

import java.util.ArrayList;
import java.util.List;

/** Mikoo-style in-app language picker (settings → Language). */
public class LanguageActivity extends ThemedActivity {

    static final class LangOption {
        final String tag;
        final int labelRes;

        LangOption(String tag, int labelRes) {
            this.tag = tag;
            this.labelRes = labelRes;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityLanguageBinding binding = ActivityLanguageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.btnBack.setOnClickListener(v -> navigateUp());

        String current = "ar";
        try {
            current = LocaleHelper.normalizeTag(
                    ContainerProvider.from(this).getSessionManager().getLanguage());
        } catch (Exception ignored) {
        }

        List<LangOption> options = new ArrayList<>();
        options.add(new LangOption("system", R.string.system_language));
        options.add(new LangOption("ar", R.string.lang_name_ar));
        options.add(new LangOption("en", R.string.lang_name_en));
        options.add(new LangOption("fr", R.string.lang_name_fr));
        options.add(new LangOption("tr", R.string.lang_name_tr));
        options.add(new LangOption("de", R.string.lang_name_de));
        options.add(new LangOption("es", R.string.lang_name_es));
        options.add(new LangOption("pt-BR", R.string.lang_name_pt));
        options.add(new LangOption("ru", R.string.lang_name_ru));
        options.add(new LangOption("id", R.string.lang_name_id));
        options.add(new LangOption("hi", R.string.lang_name_hi));
        options.add(new LangOption("ur", R.string.lang_name_ur));
        options.add(new LangOption("zh-CN", R.string.lang_name_zh));

        binding.recyclerLanguages.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerLanguages.setAdapter(new Adapter(options, current, tag -> {
            SettingsViewModel.applyLanguage(this, tag);
            // Recreate task so all screens pick the new resources.
            recreate();
            setResult(RESULT_OK);
            finish();
        }));
    }

    private interface PickListener {
        void onPick(String tag);
    }

    private static final class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private final List<LangOption> items;
        private final String selected;
        private final PickListener listener;

        Adapter(List<LangOption> items, String selected, PickListener listener) {
            this.items = items;
            this.selected = selected != null ? selected : "ar";
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_language_row, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            LangOption opt = items.get(position);
            h.name.setText(opt.labelRes);
            boolean checked = selected.equalsIgnoreCase(opt.tag)
                    || ("en".equalsIgnoreCase(opt.tag) && "en-US".equalsIgnoreCase(selected));
            h.check.setVisibility(checked ? View.VISIBLE : View.GONE);
            h.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onPick(opt.tag);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static final class VH extends RecyclerView.ViewHolder {
            final TextView name;
            final ImageView check;

            VH(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.tvLangName);
                check = itemView.findViewById(R.id.imgLangCheck);
            }
        }
    }
}
