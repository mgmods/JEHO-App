package com.Dramizo.Series.data.repository;

import com.Dramizo.Series.data.remote.api.AgencyApi;
import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.domain.model.Result;
import com.Dramizo.Series.domain.repository.AgencyRepository;
import com.Dramizo.Series.util.ApiCall;
import java.util.concurrent.ExecutorService;

public class AgencyRepositoryImpl implements AgencyRepository {
    private final AgencyApi api;
    public AgencyRepositoryImpl(AgencyApi api, ExecutorService io) { this.api = api; }

    @Override public Result<MiscDtos.ListResult<MiscDtos.AgencyDto>> list(int page) {
        // Public API requires a code/id — empty query returns an empty list.
        return ApiCall.execute(api.list(page, ""));
    }

    @Override public Result<MiscDtos.ListResult<MiscDtos.AgencyDto>> list(int page, String query) {
        return ApiCall.execute(api.list(page, query));
    }

    @Override public Result<MiscDtos.AgencyMineDto> mine() {
        return ApiCall.execute(api.mine());
    }

    @Override public Result<MiscDtos.AgencyPricingDto> pricing() {
        return ApiCall.execute(api.pricing());
    }

    @Override public Result<Object> joinByCode(String code) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("code", code);
        return ApiCall.execute(api.joinByCode(body));
    }

    @Override public Result<Object> leave(String id) {
        return ApiCall.execute(api.leave(id));
    }

    @Override public Result<Object> delete(String id) {
        return ApiCall.execute(api.delete(id));
    }

    @Override public Result<MiscDtos.AgencyApplicationDto> apply(
            MiscDtos.AgencyApplicationRequest request) {
        return ApiCall.execute(api.apply(request));
    }

    @Override public Result<RoomDtos.JoinRoomResult> openRoom(
            String agencyId, String title, String coverUrl) {
        return ApiCall.execute(
                api.openRoom(agencyId, RoomDtos.CreateRoomRequest.agencyRoom(
                        agencyId, title, coverUrl)));
    }
}
