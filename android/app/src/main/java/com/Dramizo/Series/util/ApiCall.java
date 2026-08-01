package com.Dramizo.Series.util;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.domain.model.Result;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;

import androidx.annotation.Nullable;

import retrofit2.Call;
import retrofit2.Response;

public final class ApiCall {
    private ApiCall() {}

    public static <T> Result<T> execute(Call<ApiResponse<T>> call) {
        try {
            Response<ApiResponse<T>> response = call.execute();
            if (response.isSuccessful() && response.body() != null) {
                ApiResponse<T> body = response.body();
                if (body.success) {
                    return Result.ok(body.data);
                }
                return Result.err(friendly(body.message, response.code()));
            }
            String raw = response.errorBody() != null ? response.errorBody().string() : null;
            return Result.err(parseError(raw, response.code()));
        } catch (IOException e) {
            return Result.err(e.getMessage() != null ? e.getMessage() : "خطأ في الشبكة");
        } catch (RuntimeException e) {
            // e.g. Gson JsonSyntaxException — never crash background threads
            String msg = e.getMessage();
            return Result.err(msg != null && !msg.isEmpty() ? "خطأ في البيانات من السيرفر" : "حدث خطأ غير متوقع");
        }
    }

    private static String parseError(String raw, int code) {
        if (raw != null && !raw.isEmpty()) {
            try {
                JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
                if (obj.has("message") && !obj.get("message").isJsonNull()) {
                    JsonElement messageEl = obj.get("message");
                    if (messageEl.isJsonObject()) {
                        JsonObject msgObj = messageEl.getAsJsonObject();
                        String codeKey = msgObj.has("code") && !msgObj.get("code").isJsonNull()
                                ? msgObj.get("code").getAsString() : "";
                        String text = msgObj.has("message") && !msgObj.get("message").isJsonNull()
                                ? msgObj.get("message").getAsString() : "";
                        if (!codeKey.isEmpty() && !text.isEmpty()) return codeKey + ": " + text;
                        if (!text.isEmpty()) return text;
                        if (!codeKey.isEmpty()) return codeKey;
                    } else if (messageEl.isJsonArray()) {
                        JsonArray arr = messageEl.getAsJsonArray();
                        StringBuilder sb = new StringBuilder();
                        for (JsonElement el : arr) {
                            if (el == null || el.isJsonNull()) continue;
                            String part = el.isJsonPrimitive() ? el.getAsString() : el.toString();
                            if (part == null || part.isEmpty()) continue;
                            if (sb.length() > 0) sb.append(" · ");
                            sb.append(part);
                        }
                        if (sb.length() > 0) return friendly(sb.toString(), code);
                    } else if (messageEl.isJsonPrimitive()) {
                        return friendly(messageEl.getAsString(), code);
                    }
                }
                if (obj.has("error") && !obj.get("error").isJsonNull()) {
                    return friendly(obj.get("error").getAsString(), code);
                }
            } catch (Exception ignored) {
            }
        }
        return friendly(null, code);
    }

    private static String friendly(String message, int code) {
        if (message != null && (message.contains("DM_GIFT_REQUIRED") || message.contains("أرسل هدية"))) {
            return message;
        }
        if (code == 429
                || (message != null && (
                message.toLowerCase().contains("too many")
                        || message.contains("تقييد")
                        || message.contains("عدد كبير")))) {
            return "RATE_LIMIT"; // swallowed by UI helpers — soft backoff, no spam toast
        }
        if (code == 401 || (message != null && message.toLowerCase().contains("unauthorized"))) {
            return "انتهت الجلسة، سجّل الدخول مرة أخرى";
        }
        if (message != null) {
            String lower = message.toLowerCase();
            if (lower.contains("kick permission") || lower.contains("ban permission")
                    || lower.contains("mute permission")) {
                return "لا تملك صلاحية لهذا الإجراء";
            }
            if (lower.contains("must allow your microphone")
                    || lower.contains("moderator must allow")) {
                return "يحتاج المضيف لفك كتم المايك أولاً";
            }
            if (lower.contains("agency is suspended") || lower.contains("agency room is unavailable")) {
                return "الوكالة غير نشطة حالياً";
            }
            if (lower.contains("invalid identity claim")) {
                return "تعذّر تنفيذ الإجراء، حدّث التطبيق وحاول مرة أخرى";
            }
            if (lower.contains("must be a uuid") || lower.contains("userId")) {
                if (lower.contains("uuid")) return "معرّف المستخدم غير صالح";
            }
            if (lower.equals("bad request") || lower.equals("failed") || lower.contains("bad request")) {
                return code == 400 ? "طلب غير صالح" : "تعذّر تنفيذ العملية";
            }
        }
        if (code == 403) {
            return message != null && !message.isEmpty() ? message : "لا تملك صلاحية لهذا الإجراء";
        }
        if (code == 404) {
            return message != null && !message.isEmpty() ? message : "العنصر غير موجود";
        }
        if (code >= 500) {
            if (message != null && !message.isEmpty()
                    && !message.trim().startsWith("{")
                    && !message.toLowerCase().contains("internal server")) {
                return message;
            }
            return "خطأ في السيرفر، حاول لاحقاً";
        }
        if (message != null && message.toLowerCase().contains("insufficient coins")) {
            return "رصيد العملات غير كافٍ";
        }
        if (message != null && !message.isEmpty() && !message.trim().startsWith("{")) {
            return message;
        }
        return code > 0 ? ("خطأ " + code) : "حدث خطأ غير متوقع";
    }

    public static boolean isRateLimited(@Nullable String error) {
        return error != null && (error.equals("RATE_LIMIT")
                || error.toLowerCase().contains("too many")
                || error.contains("عدد كبير"));
    }
}
