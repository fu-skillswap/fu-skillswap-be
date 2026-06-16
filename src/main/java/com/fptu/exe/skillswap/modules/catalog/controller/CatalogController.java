package com.fptu.exe.skillswap.modules.catalog.controller;

import com.fptu.exe.skillswap.modules.catalog.dto.response.HelpTopicResponse;
import com.fptu.exe.skillswap.modules.catalog.service.CatalogService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
@Tag(name = "Danh mục", description = "Tra cứu dữ liệu danh mục dùng cho form và bộ lọc của SkillSwap")
public class CatalogController {

    private final CatalogService catalogService;

    @Operation(
            summary = "Lấy danh sách help topics",
            description = "Trả về toàn bộ help topic đang hoạt động để FE hiển thị dropdown/chips khi mentor chọn chủ đề hỗ trợ. "
                    + "Danh sách được sắp theo độ ưu tiên của hệ thống và chỉ gồm các mục có thể chọn."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Danh sách help topics")
    })
    @GetMapping("/help-topics")
    public ApiResponse<List<HelpTopicResponse>> getHelpTopics() {
        return ApiResponse.success(catalogService.getHelpTopics());
    }
}
