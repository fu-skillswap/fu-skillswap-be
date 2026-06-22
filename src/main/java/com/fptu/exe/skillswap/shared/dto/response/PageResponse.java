package com.fptu.exe.skillswap.shared.dto.response;

import java.util.List;

import lombok.Builder;
import lombok.Data;

import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@Schema(description = "Standard pagination response wrapper")
public class PageResponse<T> {
    @Schema(description = "List of content items for the current page")
    private List<T> content;

    @Schema(description = "Current page index (0-based)", example = "0")
    private int page;

    @Schema(description = "Page size (number of items per page)", example = "10")
    private int size;

    @Schema(description = "Total number of elements matching the filter query across all pages", example = "101")
    private long totalElements;

    @Schema(description = "Total number of pages", example = "11")
    private int totalPages;

    @Schema(description = "Flag indicating if this page is the last page", example = "false")
    private boolean last;
}

