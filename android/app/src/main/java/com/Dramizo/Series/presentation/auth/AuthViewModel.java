package com.Dramizo.Series.presentation.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.Dramizo.Series.data.remote.dto.AuthDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;

public class AuthViewModel extends ViewModel {
    private final AppContainer container;
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<AuthDtos.AuthResult> authSuccess = new MutableLiveData<>();
    private final MutableLiveData<AuthDtos.OtpSentResult> otpSent = new MutableLiveData<>();

    public AuthViewModel(AppContainer container) { this.container = container; }

    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }
    public LiveData<AuthDtos.AuthResult> getAuthSuccess() { return authSuccess; }
    public LiveData<AuthDtos.OtpSentResult> getOtpSent() { return otpSent; }

    public void login(String identifier, String password) {
        loading.postValue(true);
        container.getIoExecutor().execute(() -> {
            Result<AuthDtos.AuthResult> r = container.loginUseCase.execute(identifier, password);
            loading.postValue(false);
            if (r.success) authSuccess.postValue(r.data); else error.postValue(r.error);
        });
    }

    public void register(String email, String password, String username, String displayName) {
        loading.postValue(true);
        container.getIoExecutor().execute(() -> {
            Result<AuthDtos.AuthResult> r = container.registerUseCase.execute(email, password, username, displayName);
            loading.postValue(false);
            if (r.success) authSuccess.postValue(r.data); else error.postValue(r.error);
        });
    }

    public void sendOtp(String phone) {
        loading.postValue(true);
        container.getIoExecutor().execute(() -> {
            Result<AuthDtos.OtpSentResult> r = container.sendOtpUseCase.execute(phone);
            loading.postValue(false);
            if (r.success) otpSent.postValue(r.data); else error.postValue(r.error);
        });
    }

    public void verifyOtp(String phone, String code, String username) {
        loading.postValue(true);
        container.getIoExecutor().execute(() -> {
            Result<AuthDtos.AuthResult> r = container.verifyOtpUseCase.execute(phone, code, username);
            loading.postValue(false);
            if (r.success) authSuccess.postValue(r.data); else error.postValue(r.error);
        });
    }

    public void guestLogin(String deviceId, String displayName) {
        loading.postValue(true);
        container.getIoExecutor().execute(() -> {
            Result<AuthDtos.AuthResult> r = container.guestLoginUseCase.execute(deviceId, displayName);
            loading.postValue(false);
            if (r.success) authSuccess.postValue(r.data); else error.postValue(r.error);
        });
    }

    public void socialLogin(String provider, String token) {
        socialLogin(provider, token, null, null, null, null);
    }

    public void socialLogin(String provider, String token, String email, String displayName,
                            String avatarUrl, String providerUserId) {
        loading.postValue(true);
        container.getIoExecutor().execute(() -> {
            Result<AuthDtos.AuthResult> r = container.socialLoginUseCase.execute(
                    provider, token, email, displayName, avatarUrl, providerUserId);
            loading.postValue(false);
            if (r.success) authSuccess.postValue(r.data); else error.postValue(r.error);
        });
    }
}
