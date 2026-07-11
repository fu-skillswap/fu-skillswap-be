package com.fptu.exe.skillswap.modules.mentor.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

@Data
public class MentorDiscoverySearchRequest {

    @Schema(description = "Số trang bắt đầu từ 0", example = "0", defaultValue = "0")
    private int page = 0;
    @Schema(description = "Số phần tử trên mỗi trang", example = "12", defaultValue = "12")
    private int size = 12;
    @Schema(description = "Trường sắp xếp", example = "relevance", defaultValue = "relevance")
    private String sortBy = "relevance";
    @Schema(description = "Chiều sắp xếp", example = "DESC", defaultValue = "DESC")
    private Sort.Direction direction = Sort.Direction.DESC;
    @Schema(description = "Từ khóa tìm theo headline, profile, môn - điểm, help topics, project, achievement và service", example = "OJT CV project")
    private String keyword;
    @Schema(description = "Danh sách help topic ID để lọc")
    private List<UUID> tagIds;
    @Schema(description = "Lọc theo campus ID")
    private UUID campusId;
    @Schema(description = "Lọc theo specialization ID")
    private UUID specializationId;
}
