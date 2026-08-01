package com.Dramizo.Series.data.repository;

import com.Dramizo.Series.data.remote.api.RankingApi;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.RankingRepository;
import com.Dramizo.Series.util.ApiCall;

public class RankingRepositoryImpl implements RankingRepository {
    private final RankingApi api;

    public RankingRepositoryImpl(RankingApi api, java.util.concurrent.ExecutorService io) {
        this.api = api;
    }

    @Override
    public Result<MiscDtos.ListResult<MiscDtos.RankingEntryDto>> list(String period, String type) {
        String category = mapCategory(type);
        String p = period == null || period.isEmpty() ? "daily" : period;
        return ApiCall.execute(api.list(p, category, 50));
    }

    private static String mapCategory(String type) {
        if (type == null) return "rich";
        switch (type.toLowerCase()) {
            case "coins":
            case "rich":
                return "rich";
            case "popular":
            case "charm":
                return "popular";
            case "host":
            case "hosts":
                return "host";
            case "agency":
                return "agency";
            case "room":
            case "rooms":
                return "room";
            case "gifts":
            case "gift":
                return "gifts";
            default:
                return "rich";
        }
    }
}
