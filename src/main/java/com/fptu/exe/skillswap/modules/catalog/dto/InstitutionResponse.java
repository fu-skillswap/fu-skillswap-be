package com.fptu.exe.skillswap.modules.catalog.dto;
import lombok.Builder;
import java.util.UUID;
@Builder public record InstitutionResponse(UUID id, String slug, String name, String shortName, UUID provinceId, String provinceName, String institutionType) { }
