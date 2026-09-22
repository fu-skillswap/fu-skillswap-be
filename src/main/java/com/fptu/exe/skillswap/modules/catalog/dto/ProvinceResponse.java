package com.fptu.exe.skillswap.modules.catalog.dto;
import lombok.Builder;
import java.util.UUID;
@Builder public record ProvinceResponse(UUID id, String code, String name, String unitType) { }
