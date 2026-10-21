package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.PATCH;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** Native super-admin API. Requests use the app's authenticated Retrofit client;
 * server-side JwtAuthGuard/AdminGuard remain authoritative. */
public interface AdminApi {
    @GET("admin/dashboard/overview")
    Call<ApiResponse<Object>> overview();

    @GET("admin/users")
    Call<ApiResponse<Object>> users(@Query("page") int page, @Query("limit") int limit,
                                    @Query("search") String search);

    @POST("admin/users/{id}/ban")
    Call<ApiResponse<Object>> banUser(@Path("id") String id);

    @POST("admin/users/{id}/unban")
    Call<ApiResponse<Object>> unbanUser(@Path("id") String id);

    @PATCH("admin/users/{id}")
    Call<ApiResponse<Object>> updateUser(@Path("id") String id, @Body Map<String, Object> body);

    @DELETE("admin/users/{id}")
    Call<ApiResponse<Object>> deleteUser(@Path("id") String id);

    @GET("admin/rooms")
    Call<ApiResponse<Object>> rooms(@Query("page") int page, @Query("limit") int limit);

    @POST("admin/rooms/{id}/close")
    Call<ApiResponse<Object>> closeRoom(@Path("id") String id);

    @PATCH("admin/rooms/{id}")
    Call<ApiResponse<Object>> updateRoom(@Path("id") String id, @Body Map<String, Object> body);

    @DELETE("admin/rooms/{id}")
    Call<ApiResponse<Object>> deleteRoom(@Path("id") String id, @Query("force") String force);

    @GET("admin/streams")
    Call<ApiResponse<Object>> streams(@Query("page") int page, @Query("limit") int limit);

    @POST("admin/streams/{id}/end")
    Call<ApiResponse<Object>> endStream(@Path("id") String id);

    @POST("admin/streams/{id}/force-end")
    Call<ApiResponse<Object>> forceEndStream(@Path("id") String id, @Body Map<String, Object> body);

    @GET("admin/agencies")
    Call<ApiResponse<Object>> agencies(@Query("page") int page, @Query("limit") int limit);

    @POST("admin/agencies")
    Call<ApiResponse<Object>> createAgency(@Body Map<String, Object> body);

    @POST("admin/agencies/{id}/approve")
    Call<ApiResponse<Object>> approveAgency(@Path("id") String id);

    @DELETE("admin/agencies/{id}")
    Call<ApiResponse<Object>> deleteAgency(@Path("id") String id);

    @POST("admin/agencies/{id}/suspend")
    Call<ApiResponse<Object>> suspendAgency(@Path("id") String id, @Body Map<String, String> body);

    @GET("admin/gifts")
    Call<ApiResponse<Object>> gifts();

    @PATCH("admin/gifts/{id}")
    Call<ApiResponse<Object>> updateGift(@Path("id") String id, @Body Map<String, Object> body);

    @GET("admin/vip")
    Call<ApiResponse<Object>> vipMembers(@Query("page") int page, @Query("limit") int limit);

    @GET("admin/vip/plans")
    Call<ApiResponse<Object>> vipPlans();

    @POST("admin/vip/plans")
    Call<ApiResponse<Object>> createVipPlan(@Body Map<String, Object> body);

    @PATCH("admin/vip/plans/{id}")
    Call<ApiResponse<Object>> updateVipPlan(@Path("id") String id, @Body Map<String, Object> body);

    @POST("admin/vip/assign")
    Call<ApiResponse<Object>> assignVip(@Body Map<String, Object> body);

    /** Direct messages use the existing authenticated chat API and preserve the admin sender identity. */
    @POST("chat/conversations")
    Call<ApiResponse<Object>> startDirectConversation(@Body Map<String, Object> body);

    @POST("chat/conversations/{id}/messages")
    Call<ApiResponse<Object>> sendDirectMessage(@Path("id") String conversationId,
                                                @Body Map<String, Object> body);

    @GET("admin/notifications")
    Call<ApiResponse<Object>> notifications(@Query("page") int page, @Query("limit") int limit);

    @POST("notifications/admin-messages/send")
    Call<ApiResponse<Object>> sendAdminMessage(@Body Map<String, String> body);

    @POST("admin/notifications/send")
    Call<ApiResponse<Object>> sendNotification(@Body Map<String, String> body);

    @GET("admin/operators")
    Call<ApiResponse<Object>> operators();
    @POST("admin/operators")
    Call<ApiResponse<Object>> createOperator(@Body Map<String, Object> body);
    @PATCH("admin/operators/{id}")
    Call<ApiResponse<Object>> updateOperator(@Path("id") String id, @Body Map<String, Object> body);
    @DELETE("admin/operators/{id}")
    Call<ApiResponse<Object>> deleteOperator(@Path("id") String id);
    @GET("admin/agencies/applications")
    Call<ApiResponse<Object>> agencyApplications(@Query("page") int page, @Query("limit") int limit);
    @POST("admin/agencies/applications/{id}/approve")
    Call<ApiResponse<Object>> approveAgencyApplication(@Path("id") String id, @Body Map<String, Object> body);
    @POST("admin/agencies/applications/{id}/reject")
    Call<ApiResponse<Object>> rejectAgencyApplication(@Path("id") String id, @Body Map<String, Object> body);
    @GET("admin/recharge-agents/applications")
    Call<ApiResponse<Object>> rechargeAgentApplications();
    @POST("admin/recharge-agents/applications/{id}/approve")
    Call<ApiResponse<Object>> approveRechargeAgentApplication(@Path("id") String id, @Body Map<String, Object> body);
    @POST("admin/recharge-agents/applications/{id}/reject")
    Call<ApiResponse<Object>> rejectRechargeAgentApplication(@Path("id") String id, @Body Map<String, Object> body);
    @GET("admin/recharge-agents")
    Call<ApiResponse<Object>> rechargeAgents();

    @POST("admin/recharge-agents/assign")
    Call<ApiResponse<Object>> assignRechargeAgent(@Body Map<String, Object> body);

    @DELETE("admin/recharge-agents/{id}")
    Call<ApiResponse<Object>> deleteRechargeAgent(@Path("id") String id);

    @POST("admin/recharge-agents/{id}/float")
    Call<ApiResponse<Object>> adjustRechargeAgentFloat(@Path("id") String id, @Body Map<String, Object> body);

    @GET("admin/gender-verifications")
    Call<ApiResponse<Object>> genderVerifications(@Query("status") String status);

    @POST("admin/gender-verifications/{id}/approve")
    Call<ApiResponse<Object>> approveGenderVerification(@Path("id") String id, @Body Map<String, Object> body);

    @POST("admin/gender-verifications/{id}/reject")
    Call<ApiResponse<Object>> rejectGenderVerification(@Path("id") String id, @Body Map<String, Object> body);
    @PATCH("admin/recharge-agents/{id}")
    Call<ApiResponse<Object>> updateRechargeAgent(@Path("id") String id, @Body Map<String, Object> body);
    @GET("admin/reports")
    Call<ApiResponse<Object>> reports(@Query("page") int page, @Query("limit") int limit);
    @POST("admin/reports/{id}/resolve")
    Call<ApiResponse<Object>> resolveReport(@Path("id") String id, @Body Map<String, Object> body);
    @POST("admin/reports/{id}/dismiss")
    Call<ApiResponse<Object>> dismissReport(@Path("id") String id, @Body Map<String, Object> body);
    @GET("admin/cosmetics")
    Call<ApiResponse<Object>> cosmetics();

    @POST("admin/cosmetics/grant")
    Call<ApiResponse<Object>> grantCosmetic(@Body Map<String, Object> body);

    @GET("vanity-ids/admin/all")
    Call<ApiResponse<Object>> vanityIds();

    @POST("vanity-ids/admin")
    Call<ApiResponse<Object>> createVanityId(@Body Map<String, Object> body);

    @POST("vanity-ids/admin/assign")
    Call<ApiResponse<Object>> assignVanityId(@Body Map<String, Object> body);

    @POST("vanity-ids/admin/{id}/revoke")
    Call<ApiResponse<Object>> revokeVanityId(@Path("id") String id);

    @POST("vanity-ids/admin/{id}/enable")
    Call<ApiResponse<Object>> enableVanityId(@Path("id") String id);

    @POST("vanity-ids/admin/{id}/disable")
    Call<ApiResponse<Object>> disableVanityId(@Path("id") String id);
    @PATCH("admin/cosmetics/{id}")
    Call<ApiResponse<Object>> updateCosmetic(@Path("id") String id, @Body Map<String, Object> body);
    @POST("admin/wallet/{userId}/adjust")
    Call<ApiResponse<Object>> adjustWallet(@Path("userId") String userId, @Body Map<String, Object> body);

    @POST("admin/vip/{id}/revoke")
    Call<ApiResponse<Object>> revokeVip(@Path("id") String id);

    @GET("admin/wallet/transactions")
    Call<ApiResponse<Object>> walletTransactions(@Query("page") int page, @Query("limit") int limit);
    @GET("admin/wallet/withdraws")
    Call<ApiResponse<Object>> withdrawals(@Query("page") int page, @Query("limit") int limit);
    @POST("admin/wallet/withdraws/{id}/approve")
    Call<ApiResponse<Object>> approveWithdrawal(@Path("id") String id);
    @POST("admin/wallet/withdraws/{id}/reject")
    Call<ApiResponse<Object>> rejectWithdrawal(@Path("id") String id, @Body Map<String, Object> body);
    @GET("admin/wallet/recharges")
    Call<ApiResponse<Object>> recharges(@Query("page") int page, @Query("limit") int limit);
    @POST("admin/wallet/recharges/{id}/complete")
    Call<ApiResponse<Object>> completeRecharge(@Path("id") String id, @Body Map<String, Object> body);
    @POST("admin/wallet/recharges/{id}/cancel")
    Call<ApiResponse<Object>> cancelRecharge(@Path("id") String id);

    @GET("admin/staff-identity/public")
    Call<ApiResponse<Object>> publicStaffIdentity();

    @GET("admin/staff-identity")
    Call<ApiResponse<Object>> staffIdentity();

    @PATCH("admin/staff-identity")
    Call<ApiResponse<Object>> updateStaffIdentity(@Body Map<String, Object> body);

    @GET("admin/logs")
    Call<ApiResponse<Object>> logs(@Query("page") int page, @Query("limit") int limit);
}
