package com.Dramizo.Series.presentation.agency;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.di.AppContainer;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.util.ApiCall;
import java.util.Collections;
import java.util.List;

public class AgencyViewModel extends ViewModel {
    private final AppContainer c;
    private final MutableLiveData<List<MiscDtos.AgencyDto>> agencies =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<MiscDtos.AgencyEarningsDto> earnings = new MutableLiveData<>();
    private final MutableLiveData<MiscDtos.AgencyMineDto> mine = new MutableLiveData<>();
    private final MutableLiveData<MiscDtos.AgencyPricingDto> pricing = new MutableLiveData<>();
    private final MutableLiveData<Boolean> submitting = new MutableLiveData<>(false);
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    public AgencyViewModel(AppContainer c) { this.c = c; }
    public LiveData<List<MiscDtos.AgencyDto>> getAgencies() { return agencies; }
    public LiveData<MiscDtos.AgencyEarningsDto> getEarnings() { return earnings; }
    public LiveData<MiscDtos.AgencyMineDto> getMine() { return mine; }
    public LiveData<MiscDtos.AgencyPricingDto> getPricing() { return pricing; }
    public LiveData<Boolean> getSubmitting() { return submitting; }
    public LiveData<String> getMessage() { return message; }
    public LiveData<String> getError() { return error; }

    public void load() {
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.AgencyPricingDto> p = c.getAgencyRepository().pricing();
            if (p.success && p.data != null) pricing.postValue(p.data);

            Result<MiscDtos.AgencyMineDto> m = c.getAgencyRepository().mine();
            if (m.success && m.data != null) {
                mine.postValue(m.data);
                if (m.data.earnings != null) earnings.postValue(m.data.earnings);
                else earnings.postValue(null);
                // App shows ONLY this user's agency — never the full active-agencies directory.
                if (m.data.agency != null) {
                    agencies.postValue(Collections.singletonList(m.data.agency));
                } else {
                    agencies.postValue(Collections.emptyList());
                }
            } else {
                mine.postValue(null);
                earnings.postValue(null);
                agencies.postValue(Collections.emptyList());
                if (!m.success && m.error != null) error.postValue(m.error);
            }
        });
    }

    public void loadPricing() {
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.AgencyPricingDto> p = c.getAgencyRepository().pricing();
            if (p.success && p.data != null) pricing.postValue(p.data);
        });
    }

    public void joinByCode(String code) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = c.getAgencyRepository().joinByCode(code);
            if (r.success) {
                message.postValue("تم إرسال طلب الانضمام — بانتظار موافقة إدارة الوكالة");
                load();
            } else {
                String err = r.error != null ? r.error : "تعذر الانضمام";
                // Nest may wrap GoneException as "CODE: text" — keep the Arabic text.
                int colon = err.indexOf(": ");
                if (colon > 0 && colon < 48 && err.length() > colon + 2) {
                    String maybe = err.substring(colon + 2).trim();
                    if (!maybe.isEmpty()) err = maybe;
                }
                error.postValue(err);
            }
        });
    }

    public void updateNotificationStyle(String agencyId, String style) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("notificationStyle", style);
        updateSettings(agencyId, body, "تم حفظ إشعار الوكالة");
    }

    public void updateAgencyBranding(String agencyId, String name, String description) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        if (name != null) body.put("name", name);
        if (description != null) body.put("description", description);
        updateSettings(agencyId, body, "تم حفظ اسم الوكالة والترحيب");
    }

    private void updateSettings(String agencyId, java.util.Map<String, String> body, String okMsg) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = ApiCall.execute(c.getAgencyApi().updateSettings(agencyId, body));
            if (r.success) {
                message.postValue(okMsg);
                load();
            } else {
                error.postValue(r.error);
            }
        });
    }

    public void approveJoin(String agencyId, String userId) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = ApiCall.execute(c.getAgencyApi().approveJoin(agencyId, userId));
            if (r.success) {
                message.postValue("تم قبول العضو");
                load();
            } else {
                error.postValue(r.error);
            }
        });
    }

    public void rejectJoin(String agencyId, String userId) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = ApiCall.execute(c.getAgencyApi().rejectJoin(agencyId, userId));
            if (r.success) {
                message.postValue("تم رفض الطلب");
                load();
            } else {
                error.postValue(r.error);
            }
        });
    }

    public void leave(String id) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = c.getAgencyRepository().leave(id);
            if (r.success) {
                MiscDtos.AgencyMineDto current = mine.getValue();
                boolean wasPending = current != null
                        && current.membershipStatus != null
                        && "pending".equalsIgnoreCase(current.membershipStatus.trim());
                message.postValue(wasPending ? "تم إلغاء طلب الانضمام" : "غادرت الوكالة");
                load();
            } else {
                error.postValue(r.error);
            }
        });
    }

    public void deleteAgency(String id) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = c.getAgencyRepository().delete(id);
            if (r.success) {
                message.postValue("agency_deleted");
                load();
            } else {
                error.postValue(r.error);
            }
        });
    }

    public void addMember(String agencyId, String userId) {
        addMember(agencyId, userId, "host");
    }

    public void addMember(String agencyId, String userId, String role) {
        c.getIoExecutor().execute(() -> {
            java.util.Map<String, String> body = new java.util.HashMap<>();
            body.put("userId", userId);
            if (role != null && !role.isEmpty()) body.put("role", role);
            Result<Object> r = ApiCall.execute(c.getAgencyApi().addMember(agencyId, body));
            if (r.success) {
                message.postValue("تمت إضافة العضو");
                load();
            } else {
                error.postValue(r.error);
            }
        });
    }

    public void removeMember(String agencyId, String userId) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = ApiCall.execute(c.getAgencyApi().removeMember(agencyId, userId));
            if (r.success) {
                message.postValue("تم طرد العضو");
                load();
            } else {
                error.postValue(r.error);
            }
        });
    }

    public void updateMemberRole(String agencyId, String userId, String role) {
        c.getIoExecutor().execute(() -> {
            java.util.Map<String, String> body = new java.util.HashMap<>();
            body.put("role", role);
            Result<Object> r = ApiCall.execute(
                    c.getAgencyApi().updateMemberRole(agencyId, userId, body));
            if (r.success) {
                message.postValue("تم تحديث صلاحية العضو");
                load();
            } else {
                error.postValue(r.error);
            }
        });
    }

    public void suspendMember(String agencyId, String userId) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = ApiCall.execute(c.getAgencyApi().suspendMember(agencyId, userId));
            if (r.success) {
                message.postValue("تم تعليق العضو");
                load();
            } else {
                error.postValue(r.error);
            }
        });
    }

    public void unsuspendMember(String agencyId, String userId) {
        c.getIoExecutor().execute(() -> {
            Result<Object> r = ApiCall.execute(c.getAgencyApi().unsuspendMember(agencyId, userId));
            if (r.success) {
                message.postValue("تم إلغاء تعليق العضو");
                load();
            } else {
                error.postValue(r.error);
            }
        });
    }

    public void updateCommission(String agencyId, double percent) {
        c.getIoExecutor().execute(() -> {
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("commissionPercent", percent);
            Result<Object> r = ApiCall.execute(c.getAgencyApi().updateCommission(agencyId, body));
            if (r.success) {
                message.postValue("تم حفظ نسبة العمولة");
                load();
            } else {
                error.postValue(r.error);
            }
        });
    }

    public void submitApplication(MiscDtos.AgencyApplicationRequest request) {
        if (Boolean.TRUE.equals(submitting.getValue())) return;
        submitting.postValue(true);
        c.getIoExecutor().execute(() -> {
            Result<MiscDtos.AgencyApplicationDto> r =
                    c.getAgencyRepository().apply(request);
            submitting.postValue(false);
            if (r.success) {
                message.postValue("application_submitted");
                load();
            } else {
                error.postValue(r.error);
            }
        });
    }
}
