package com.fptu.exe.skillswap.modules.catalog.controller;

import com.fptu.exe.skillswap.modules.catalog.service.CatalogService;
import com.fptu.exe.skillswap.modules.catalog.dto.response.MentorProfileOptionsResponse;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
@Tag(name = "Catalog", description = "Nhóm API master data dùng cho các form và contract hiện hành.")
public class CatalogController {

    private final CatalogService catalogService;

    @Operation(
            summary = "Lấy option cho mentor profile",
            description = "Trả về label mức support 1..4 cho foundation, output review và direction. FE dùng để render form mentor profile mà không cần fix cứng wording."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Danh sách option mentor profile")
    })
    @GetMapping("/mentor-profile-options")
    public ApiResponse<MentorProfileOptionsResponse> getMentorProfileOptions(HttpServletResponse response) {
        applyCacheHeader(response);
        return ApiResponse.success(catalogService.getMentorProfileOptions());
    }

    private void applyCacheHeader(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=86400");
        response.setHeader(HttpHeaders.ETAG, "\"catalog-v1\"");
    }
}
