package com.fptu.exe.skillswap.modules.catalog.controller;

import com.fptu.exe.skillswap.modules.catalog.dto.response.HelpTopicResponse;
import com.fptu.exe.skillswap.modules.catalog.service.CatalogService;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorProfileOptionsResponse;
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
@Tag(name = "Help Topic Catalog", description = "Nhóm API trả help topics dùng trong mentor profile, mentor services và bộ lọc discovery. FE dùng khi cần danh sách chủ đề hỗ trợ để hiển thị dưới dạng dropdown hoặc chips.")
public class CatalogController {

    private final CatalogService catalogService;

    @Operation(
            summary = "Lấy danh sách help topics",
            description = "Trả về danh sách help topics đang hoạt động mà mentor có thể chọn trong mentor profile hoặc mentor service. FE dùng API này khi cần đổ dropdown/chips cho form onboarding mentor hoặc bộ lọc discovery."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Danh sách help topics")
    })
    @GetMapping("/help-topics")
    public ApiResponse<List<HelpTopicResponse>> getHelpTopics(HttpServletResponse response) {
        applyCacheHeader(response);
        return ApiResponse.success(catalogService.getHelpTopics());
    }

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
    }
}
