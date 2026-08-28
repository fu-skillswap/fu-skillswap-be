package com.fptu.exe.skillswap.modules.mentor.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.data.domain.Sort;

import java.util.UUID;

@Data
public class MentorDiscoverySearchRequest {

    public static final int MAX_PAGE_SIZE = 50;

    @Schema(description = "Số trang bắt đầu từ 0", example = "0", defaultValue = "0")
    private int page = 0;
    @Schema(description = "Số phần tử trên mỗi trang (tối đa 50)", example = "12", defaultValue = "12", maximum = "50")
    private int size = 12;
    @Schema(description = "Trường sắp xếp", example = "relevance", defaultValue = "relevance")
    private String sortBy = "relevance";
    @Schema(description = "Chiều sắp xếp", example = "DESC", defaultValue = "DESC")
    private Sort.Direction direction = Sort.Direction.DESC;
    @Schema(description = "Từ khóa tìm theo headline, profile, môn - điểm, project, achievement và service", example = "OJT CV project")
    private String keyword;
    @Schema(description = "Lọc theo campus ID")
    private UUID campusId;
    @Schema(description = "Lọc theo specialization ID")
    private UUID specializationId;
}
