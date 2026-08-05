package com.Dramizo.Series.domain.repository;

import com.Dramizo.Series.data.remote.dto.MiscDtos;
import com.Dramizo.Series.data.remote.dto.RoomDtos;
import com.Dramizo.Series.domain.model.Result;

public interface AgencyRepository {
    Result<MiscDtos.ListResult<MiscDtos.AgencyDto>> list(int page);
    Result<MiscDtos.ListResult<MiscDtos.AgencyDto>> list(int page, String query);
    Result<MiscDtos.AgencyMineDto> mine();
    Result<MiscDtos.AgencyPricingDto> pricing();
    Result<Object> joinByCode(String code);
    Result<Object> leave(String id);
    Result<Object> delete(String id);
    Result<MiscDtos.AgencyApplicationDto> apply(MiscDtos.AgencyApplicationRequest request);
    Result<RoomDtos.JoinRoomResult> openRoom(
            String agencyId, String title, String coverUrl);
}
