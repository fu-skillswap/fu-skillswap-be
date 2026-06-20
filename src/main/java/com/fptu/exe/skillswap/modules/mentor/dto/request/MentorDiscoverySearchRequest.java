package com.fptu.exe.skillswap.modules.mentor.dto.request;

import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import lombok.Data;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

@Data
public class MentorDiscoverySearchRequest {

    private int page = 0;
    private int size = 12;
    private String sortBy = "relevance";
    private Sort.Direction direction = Sort.Direction.DESC;
    private String keyword;
    private List<UUID> tagIds;
    private UUID campusId;
    private UUID specializationId;
    private TeachingMode teachingMode;
}
