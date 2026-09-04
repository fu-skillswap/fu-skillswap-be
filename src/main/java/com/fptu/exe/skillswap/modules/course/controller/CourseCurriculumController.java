package com.fptu.exe.skillswap.modules.course.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.course.dto.request.*;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseCurriculumResponse;
import com.fptu.exe.skillswap.modules.course.service.CourseCurriculumService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Course curriculum", description = "Cấu trúc khóa học gồm chương và nội dung video hoặc PDF.")
@SecurityRequirement(name = "bearerAuth")
public class CourseCurriculumController {
    private final CourseCurriculumService curriculumService;

    @Operation(summary = "Xem curriculum khóa học", description = "FE gọi sau khi user đăng nhập để hiển thị cây chương, tài liệu và tiến độ. Mentor của khóa học xem được toàn bộ curriculum; user chưa có enrollment hợp lệ vẫn xem được phần curriculum đã công bố nhưng material sẽ có `access=LOCKED`. Chỉ material có `access=AVAILABLE` mới được mở nội dung. API này không tạo enrollment và hiện không có endpoint public riêng để đăng ký khóa học.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Curriculum và tiến độ hiện tại", content = @Content(examples = @ExampleObject(
                    name = "Curriculum có tài liệu mở và khóa",
                    value = """
                            {
                              "status": 200,
                              "code": "SUCCESS_0200",
                              "message": "Thành công",
                              "data": {
                                "courseId": "019f1234-aaaa-bbbb-cccc-1234567890ab",
                                "progress": {
                                  "overallPercentage": 35,
                                  "lastStudiedMaterialId": "019f3234-aaaa-bbbb-cccc-1234567890ab"
                                },
                                "chapters": [
                                  {
                                    "chapterId": "019f2234-aaaa-bbbb-cccc-1234567890ab",
                                    "title": "Spring Boot cơ bản",
                                    "description": "Các khái niệm nền tảng",
                                    "sortOrder": 1,
                                    "published": true,
                                    "version": 3,
                                    "materials": [
                                      {
                                        "materialId": "019f3234-aaaa-bbbb-cccc-1234567890ab",
                                        "title": "Dependency Injection",
                                        "type": "VIDEO",
                                        "sortOrder": 1,
                                        "previewable": false,
                                        "published": true,
                                        "status": "READY",
                                        "access": "AVAILABLE",
                                        "progressPercentage": 50,
                                        "completed": false,
                                        "version": 2
                                      },
                                      {
                                        "materialId": "019f4234-aaaa-bbbb-cccc-1234567890ab",
                                        "title": "Bài tập thực hành",
                                        "type": "PDF",
                                        "sortOrder": 2,
                                        "previewable": false,
                                        "published": true,
                                        "status": "READY",
                                        "access": "LOCKED",
                                        "progressPercentage": null,
                                        "completed": false,
                                        "version": 1
                                      }
                                    ]
                                  }
                                ]
                              }
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy khóa học")
    })
    @GetMapping("/me/courses/{courseId}/curriculum")
    public ApiResponse<CourseCurriculumResponse> getCurriculum(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId) {
        return ApiResponse.success(curriculumService.getCurriculum(principal.getId(), courseId));
    }
    @Operation(summary = "Mentor tạo chương khóa học", description = "Chỉ mentor sở hữu khóa học đang đăng nhập mới dùng API này để thêm chapter. FE gửi thông tin hiển thị; courseId và mentor được xác định từ URL và tài khoản đăng nhập.")
    @PostMapping("/me/mentor/courses/{courseId}/chapters")
    public ApiResponse<UUID> createChapter(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @Valid @RequestBody CreateCourseChapterRequest request) {
        return ApiResponse.success(curriculumService.createChapter(principal.getId(), courseId, request).getId());
    }
    @Operation(summary = "Mentor cập nhật chương khóa học", description = "Chỉ mentor sở hữu khóa học mới được sửa chapter. Gửi `expectedVersion` lấy từ curriculum để tránh ghi đè thay đổi mới hơn; nếu version không khớp, tải lại curriculum rồi thử lại.")
    @PutMapping("/me/mentor/courses/{courseId}/chapters/{chapterId}")
    public ApiResponse<Void> updateChapter(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @PathVariable UUID chapterId, @Valid @RequestBody UpdateCourseChapterRequest request) {
        curriculumService.updateChapter(principal.getId(), courseId, chapterId, request); return ApiResponse.success(null);
    }
    @Operation(summary = "Mentor xóa chương rỗng", description = "Chỉ xóa được chapter chưa có material. Nếu chapter còn material hoặc người gọi không phải mentor sở hữu khóa học, FE không nên tự retry mà cần cập nhật lại curriculum hoặc kiểm tra quyền.")
    @DeleteMapping("/me/mentor/courses/{courseId}/chapters/{chapterId}")
    public ApiResponse<Void> deleteChapter(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @PathVariable UUID chapterId) {
        curriculumService.deleteEmptyChapter(principal.getId(), courseId, chapterId); return ApiResponse.success(null);
    }
    @Operation(summary = "Mentor sắp xếp lại chương", description = "FE gửi đủ chapter ID hiện có, mỗi ID đúng một lần, theo thứ tự muốn hiển thị. Dùng `expectedContainerVersion` để kiểm tra curriculum chưa bị thay đổi; lỗi xung đột cần tải lại danh sách.")
    @PutMapping("/me/mentor/courses/{courseId}/chapters/order")
    public ApiResponse<Void> reorderChapters(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @Valid @RequestBody ReorderCurriculumRequest request) {
        curriculumService.reorderChapters(principal.getId(), courseId, request); return ApiResponse.success(null);
    }
    @Operation(summary = "Mentor cập nhật tài liệu khóa học", description = "Chỉ mentor sở hữu khóa học mới được sửa tên, quyền preview hoặc trạng thái công bố của material. Gửi `expectedVersion` hiện tại; lỗi version là xung đột dữ liệu và cần tải lại curriculum.")
    @PutMapping("/me/mentor/courses/{courseId}/materials/{materialId}")
    public ApiResponse<Void> updateMaterial(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @PathVariable UUID materialId, @Valid @RequestBody UpdateCourseMaterialRequest request) {
        curriculumService.updateMaterial(principal.getId(), courseId, materialId, request); return ApiResponse.success(null);
    }
    @Operation(summary = "Mentor sắp xếp lại tài liệu", description = "FE gửi đủ material ID trong chapter, mỗi ID đúng một lần, theo thứ tự mới và kèm version hiện tại của chapter. Không gửi ID thuộc chapter khác.")
    @PutMapping("/me/mentor/courses/{courseId}/chapters/{chapterId}/materials/order")
    public ApiResponse<Void> reorderMaterials(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @PathVariable UUID chapterId, @Valid @RequestBody ReorderCurriculumRequest request) {
        curriculumService.reorderMaterials(principal.getId(), courseId, chapterId, request); return ApiResponse.success(null);
    }
    @Operation(summary = "Mentor xóa tài liệu khóa học", description = "Chỉ mentor sở hữu khóa học mới được xóa material. FE nên bỏ material khỏi danh sách sau khi thành công; nếu tài liệu không tồn tại hoặc version đã cũ, tải lại curriculum trước khi thử lại.")
    @DeleteMapping("/me/mentor/courses/{courseId}/materials/{materialId}")
    public ApiResponse<Void> deleteMaterial(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @PathVariable UUID materialId) {
        curriculumService.deleteMaterial(principal.getId(), courseId, materialId); return ApiResponse.success(null);
    }
}
