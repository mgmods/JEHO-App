package com.Dramizo.Series.presentation.createroom;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.Dramizo.Series.R;
import com.Dramizo.Series.databinding.FragmentCreateRoomBinding;
import com.Dramizo.Series.util.RoomOpenChooser;

/**
 * Legacy create-room tab — redirected to one-tap {@link MyRoomLauncher}.
 * No title/cover form; room uses profile name + avatar.
 */
public class CreateRoomFragment extends Fragment {
    private FragmentCreateRoomBinding binding;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentCreateRoomBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        com.Dramizo.Series.util.AppLoadingOverlay.hide(requireActivity());
        view.setVisibility(View.GONE);
        view.post(() -> {
            if (!isAdded()) return;
            RoomOpenChooser.open(requireActivity());
            if (requireActivity() instanceof com.Dramizo.Series.presentation.main.MainActivity) {
                ((com.Dramizo.Series.presentation.main.MainActivity) requireActivity()).go(R.id.nav_home);
            }
        });
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
