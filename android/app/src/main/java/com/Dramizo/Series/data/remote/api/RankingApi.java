package com.Dramizo.Series.data.remote.api;

import com.Dramizo.Series.data.remote.dto.ApiResponse;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface RankingApi {
    @GET("ranking/{period}/{category}")
    Call<ApiResponse<MiscDtos.ListResult<MiscDtos.RankingEntryDto>>> list(
            @Path("period") String period,
            @Path("category") String category,
            @Query("limit") int limit
    );
}
