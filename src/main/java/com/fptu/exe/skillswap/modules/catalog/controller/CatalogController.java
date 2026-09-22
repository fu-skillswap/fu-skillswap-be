package com.fptu.exe.skillswap.modules.catalog.controller;

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
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestParam;
import com.fptu.exe.skillswap.modules.catalog.service.EducationCatalogService;
import com.fptu.exe.skillswap.modules.catalog.dto.*;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
@Tag(name = "Catalog", description = "Nhóm API master data dùng cho các form và contract hiện hành.")
public class CatalogController {

    private final CatalogService catalogService;
    private final EducationCatalogService educationCatalogService;

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

    @Operation(
            summary = "Lấy danh sách tỉnh thành",
            description = "Trả về danh mục tỉnh/thành phố phục vụ việc chọn tỉnh thành hoặc chọn trường trực thuộc tỉnh thành."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Danh sách tỉnh thành")
    })
    @GetMapping("/provinces")
    public ApiResponse<List<ProvinceResponse>> provinces(@RequestParam(required=false) String q) {
        return ApiResponse.success(educationCatalogService.provinces(q));
    }

    @Operation(
            summary = "Tìm kiếm cơ sở giáo dục",
            description = "Tìm kiếm trường đại học, học viện theo từ khóa hoặc tỉnh thành phục vụ hoàn thiện hồ sơ."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Danh sách cơ sở giáo dục phân trang")
    })
    @GetMapping("/institutions")
    public ApiResponse<PageResponse<InstitutionResponse>> institutions(@RequestParam(required=false) String q,
            @RequestParam(required=false) UUID provinceId, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="10") int size) {
        return ApiResponse.success(educationCatalogService.institutions(q,provinceId,page,size));
    }

    @Operation(
            summary = "Tìm kiếm nhóm ngành đào tạo",
            description = "Tìm kiếm danh mục nhóm ngành đào tạo phục vụ hoàn thiện hồ sơ người học."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Danh sách nhóm ngành đào tạo phân trang")
    })
    @GetMapping("/education-field-groups")
    public ApiResponse<PageResponse<EducationFieldGroupResponse>> educationFieldGroups(@RequestParam(required=false) String q,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size) {
        return ApiResponse.success(educationCatalogService.fieldGroups(q,page,size));
    }

    private void applyCacheHeader(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=86400");
        response.setHeader(HttpHeaders.ETAG, "\"catalog-v1\"");
    }
}
