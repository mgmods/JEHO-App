package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import java.util.Map;

public interface RoomApi {
    @GET("rooms/music-library")
    Call<ApiResponse<RoomDtos.MusicLibraryDto>> musicLibrary();

    @GET("rooms/music-search")
    Call<ApiResponse<RoomDtos.InternetMusicSearchDto>> musicSearch(@Query("q") String q);

    @GET("rooms/music-resolve")
    Call<ApiResponse<RoomDtos.InternetMusicResolvedDto>> musicResolve(@Query("id") String id);

    @GET("rooms")
    Call<ApiResponse<MiscDtos.ListResult<RoomDtos.RoomDto>>> list(
            @Query("page") int page,
            @Query("limit") int limit);

    @GET("rooms")
    Call<ApiResponse<MiscDtos.ListResult<RoomDtos.RoomDto>>> list(
            @Query("page") int page,
            @Query("search") String search);

    @GET("rooms")
    Call<ApiResponse<MiscDtos.ListResult<RoomDtos.RoomDto>>> list(@Query("page") int page);

    @POST("rooms")
    Call<ApiResponse<RoomDtos.JoinRoomResult>> create(@Body RoomDtos.CreateRoomRequest body);

    @GET("rooms/{id}")
    Call<ApiResponse<RoomDtos.RoomDto>> get(@Path("id") String id);

    @GET("rooms/{id}/supporters")
    Call<ApiResponse<RoomDtos.SupportersResult>> supporters(@Path("id") String id);

    /** Mikoo contribute board: type=wealth|charm, period=day|week|month */
    @GET("rooms/{id}/contribute")
    Call<ApiResponse<RoomDtos.ContributeResult>> contribute(
            @Path("id") String id,
            @Query("type") String type,
            @Query("period") String period,
            @Query("limit") int limit);

    @POST("rooms/{id}/reports")
    Call<ApiResponse<Object>> reportUser(
            @Path("id") String id,
            @Body Map<String, String> body);

    @GET("rooms/{id}/zego-token")
    Call<ApiResponse<RoomDtos.JoinRoomResult>> zegoToken(@Path("id") String id);

    @POST("rooms/{id}/music")
    Call<ApiResponse<Map<String, Object>>> music(
            @Path("id") String id,
            @Body Map<String, Object> body);

    @POST("rooms/{id}/join")
    Call<ApiResponse<RoomDtos.JoinRoomResult>> join(@Path("id") String id, @Body Map<String, String> body);

    @POST("rooms/{id}/leave")
    Call<ApiResponse<Object>> leave(@Path("id") String id);

    @POST("rooms/{id}/close")
    Call<ApiResponse<Object>> close(@Path("id") String id);

    @POST("rooms/{id}/follow")
    Call<ApiResponse<Object>> followRoom(@Path("id") String id);

    @DELETE("rooms/{id}/follow")
    Call<ApiResponse<Object>> unfollowRoom(@Path("id") String id);

    @GET("rooms/{id}/follow")
    Call<ApiResponse<java.util.Map<String, Object>>> roomFollowStatus(@Path("id") String id);

    @PATCH("rooms/{id}")
    Call<ApiResponse<RoomDtos.RoomDto>> update(@Path("id") String id, @Body Map<String, String> body);

    @PATCH("rooms/{id}/background")
    Call<ApiResponse<Object>> setBackground(@Path("id") String id, @Body Map<String, String> body);

    @PATCH("rooms/{id}/frame")
    Call<ApiResponse<Object>> setFrame(@Path("id") String id, @Body Map<String, String> body);

    @POST("rooms/{id}/raise-hand")
    Call<ApiResponse<Object>> raiseHand(@Path("id") String id, @Body RoomDtos.RaiseHandRequest body);

    @GET("rooms/{id}/seat-requests")
    Call<ApiResponse<RoomDtos.SeatRequestsResult>> seatRequests(@Path("id") String id);

    @POST("rooms/{id}/seat-requests/approve")
    Call<ApiResponse<RoomDtos.RoomDto>> approveSeat(@Path("id") String id, @Body Map<String, Object> body);

    @POST("rooms/{id}/seat-requests/reject")
    Call<ApiResponse<Object>> rejectSeat(@Path("id") String id, @Body Map<String, Object> body);

    @POST("rooms/{id}/seat-invites")
    Call<ApiResponse<Object>> inviteSeat(@Path("id") String id, @Body Map<String, Object> body);

    @POST("rooms/{id}/task-invites")
    Call<ApiResponse<Map<String, Object>>> inviteTaskGuest(
            @Path("id") String id,
            @Body Map<String, Object> body);

    @POST("rooms/{id}/seat-invites/respond")
    Call<ApiResponse<RoomDtos.RoomDto>> respondSeatInvite(
            @Path("id") String id,
            @Body Map<String, Boolean> body);

    @POST("rooms/{id}/gift-sounds")
    Call<ApiResponse<RoomDtos.RoomDto>> setGiftSounds(
            @Path("id") String id,
            @Body Map<String, Boolean> body);

    @POST("rooms/{id}/display-settings")
    Call<ApiResponse<RoomDtos.RoomDto>> setDisplaySettings(
            @Path("id") String id,
            @Body Map<String, Boolean> body);

    @POST("rooms/{id}/chat/clear")
    Call<ApiResponse<Map<String, Object>>> clearPublicChat(@Path("id") String id);

    @POST("rooms/{id}/chat/auto-clear")
    Call<ApiResponse<RoomDtos.RoomDto>> setChatAutoClear(
            @Path("id") String id,
            @Body Map<String, Integer> body);

    @POST("rooms/{id}/lock")
    Call<ApiResponse<RoomDtos.RoomDto>> lock(@Path("id") String id, @Body RoomDtos.LockRoomRequest body);

    @POST("rooms/{id}/mic")
    Call<ApiResponse<Object>> setMic(@Path("id") String id, @Body Map<String, Object> body);

    @POST("rooms/{id}/seats/take")
    Call<ApiResponse<RoomDtos.RoomDto>> takeSeat(@Path("id") String id, @Body Map<String, Integer> body);

    @POST("rooms/{id}/seats/leave")
    Call<ApiResponse<RoomDtos.RoomDto>> leaveSeat(@Path("id") String id);

    @POST("rooms/{id}/seats/lock")
    Call<ApiResponse<RoomDtos.RoomDto>> lockSeat(@Path("id") String id, @Body Map<String, Object> body);

    @POST("rooms/{id}/seats/resize")
    Call<ApiResponse<RoomDtos.RoomDto>> resizeSeats(@Path("id") String id, @Body Map<String, Integer> body);

    @POST("rooms/{id}/kick")
    Call<ApiResponse<Object>> kick(@Path("id") String id, @Body Map<String, Object> body);

    @POST("rooms/{id}/ban")
    Call<ApiResponse<Object>> ban(@Path("id") String id, @Body Map<String, Object> body);

    @GET("rooms/{id}/bans")
    Call<ApiResponse<RoomDtos.RoomBanListResult>> listBans(@Path("id") String id);

    @GET("rooms/{id}/bans/{userId}")
    Call<ApiResponse<Map<String, Object>>> banStatus(@Path("id") String id, @Path("userId") String userId);

    @DELETE("rooms/{id}/bans/{userId}")
    Call<ApiResponse<Object>> unban(@Path("id") String id, @Path("userId") String userId);

    @POST("rooms/{id}/cohost")
    Call<ApiResponse<Object>> setCohost(@Path("id") String id, @Body Map<String, String> body);

    @POST("rooms/{id}/moderators/{userId}")
    Call<ApiResponse<Object>> addModerator(@Path("id") String id, @Path("userId") String userId);

    @DELETE("rooms/{id}/moderators/{userId}")
    Call<ApiResponse<Object>> removeModerator(@Path("id") String id, @Path("userId") String userId);

    @PATCH("rooms/{id}/moderators/{userId}/permissions")
    Call<ApiResponse<Object>> moderatorPermissions(
            @Path("id") String id,
            @Path("userId") String userId,
            @Body Map<String, Boolean> body);
}
