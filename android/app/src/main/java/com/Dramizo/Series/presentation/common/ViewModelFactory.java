package com.Dramizo.Series.presentation.common;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.presentation.agency.AgencyViewModel;
import com.Dramizo.Series.presentation.auth.AuthViewModel;
import com.Dramizo.Series.presentation.chat.ChatViewModel;
import com.Dramizo.Series.presentation.cosmetics.CosmeticsViewModel;
import com.Dramizo.Series.presentation.gifts.GiftViewModel;
import com.Dramizo.Series.presentation.home.HomeViewModel;
import com.Dramizo.Series.presentation.messages.MessagesViewModel;
import com.Dramizo.Series.presentation.notifications.NotificationsViewModel;
import com.Dramizo.Series.presentation.profile.ProfileViewModel;
import com.Dramizo.Series.presentation.ranking.RankingViewModel;
import com.Dramizo.Series.presentation.settings.SettingsViewModel;
import com.Dramizo.Series.presentation.vip.VipViewModel;
import com.Dramizo.Series.presentation.voiceroom.VoiceRoomViewModel;
import com.Dramizo.Series.presentation.wallet.WalletViewModel;

public class ViewModelFactory implements ViewModelProvider.Factory {
    private final AppContainer container;

    public ViewModelFactory(AppContainer container) {
        this.container = container;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(AuthViewModel.class)) {
            return (T) new AuthViewModel(container);
        } else if (modelClass.isAssignableFrom(HomeViewModel.class)) {
            return (T) new HomeViewModel(container);
        } else if (modelClass.isAssignableFrom(VoiceRoomViewModel.class)) {
            return (T) new VoiceRoomViewModel(container);
        } else if (modelClass.isAssignableFrom(MessagesViewModel.class)) {
            return (T) new MessagesViewModel(container);
        } else if (modelClass.isAssignableFrom(ChatViewModel.class)) {
            return (T) new ChatViewModel(container);
        } else if (modelClass.isAssignableFrom(ProfileViewModel.class)) {
            return (T) new ProfileViewModel(container);
        } else if (modelClass.isAssignableFrom(WalletViewModel.class)) {
            return (T) new WalletViewModel(container);
        } else if (modelClass.isAssignableFrom(GiftViewModel.class)) {
            return (T) new GiftViewModel(container);
        } else if (modelClass.isAssignableFrom(RankingViewModel.class)) {
            return (T) new RankingViewModel(container);
        } else if (modelClass.isAssignableFrom(AgencyViewModel.class)) {
            return (T) new AgencyViewModel(container);
        } else if (modelClass.isAssignableFrom(VipViewModel.class)) {
            return (T) new VipViewModel(container);
        } else if (modelClass.isAssignableFrom(SettingsViewModel.class)) {
            return (T) new SettingsViewModel(container);
        } else if (modelClass.isAssignableFrom(NotificationsViewModel.class)) {
            return (T) new NotificationsViewModel(container);
        } else if (modelClass.isAssignableFrom(CosmeticsViewModel.class)) {
            return (T) new CosmeticsViewModel(container);
        }
        throw new IllegalArgumentException("Unknown ViewModel: " + modelClass.getName());
    }
}
