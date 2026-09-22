package com.fptu.exe.skillswap.modules.catalog.dto;
import lombok.Builder;
import java.util.List;
import java.util.UUID;
@Builder public record EducationFieldGroupResponse(UUID id, String officialCode, String name, List<String> aliases) { }
