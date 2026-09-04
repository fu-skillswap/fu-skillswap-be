package com.fptu.exe.skillswap.modules.course.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.course.dto.request.CreateCourseAnnouncementRequest;
import com.fptu.exe.skillswap.modules.course.dto.response.CourseAnnouncementResponse;
import com.fptu.exe.skillswap.modules.course.service.CourseAnnouncementService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/courses/{courseId}/announcements")
@RequiredArgsConstructor
@Tag(name = "Course announcements", description = "Thông báo của khóa học để người học theo dõi các cập nhật quan trọng.")
@SecurityRequirement(name = "bearerAuth")
public class CourseAnnouncementController {

    private final CourseAnnouncementService announcementService;

    @GetMapping
    @Operation(summary = "Đọc thông báo của khóa học", description = "Mentor của khóa học hoặc người học có enrollment ACTIVE/COMPLETED gọi để đọc cập nhật từ mentor. Nếu chưa có thông báo, `data.content` là mảng rỗng và không phải lỗi. Người dùng chưa có quyền nhận 403; courseId không tồn tại nhận 404.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Danh sách thông báo dạng page", content = @Content(examples = @ExampleObject(
                    name = "Có thông báo",
                    value = """
                            {
                              "status": 200,
                              "code": "SUCCESS_0200",
                              "message": "Thành công",
                              "data": {
                                "content": [
                                  {
                                    "id": "019f7234-aaaa-bbbb-cccc-1234567890ab",
                                    "courseId": "019f1234-aaaa-bbbb-cccc-1234567890ab",
                                    "authorUserId": "019f1234-bbbb-cccc-dddd-1234567890ab",
                                    "title": "Cập nhật bài tập tuần này",
                                    "content": "Mentor đã bổ sung một bài tập thực hành.",
                                    "createdAt": "2026-09-04T03:20:00Z",
                                    "updatedAt": "2026-09-04T03:20:00Z",
                                    "publishedAt": "2026-09-04T03:20:00Z"
                                  }
                                ],
                                "number": 0,
                                "size": 20,
                                "totalElements": 1,
                                "totalPages": 1,
                                "first": true,
                                "last": true,
                                "empty": false
                              }
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc access token không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Người học chưa enrollment hoặc không có quyền xem thông báo"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy khóa học")
    })
    public ApiResponse<Page<CourseAnnouncementResponse>> getAnnouncements(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return ApiResponse.success(announcementService.getAnnouncements(principal.getId(), courseId, page, size));
    }

    @PostMapping
    @Operation(summary = "Mentor tạo thông báo khóa học", description = "Chỉ mentor đang sở hữu khóa học gọi khi cần gửi cập nhật cho người học. FE chỉ gửi title và content; authorUserId, courseId và các timestamp do backend xác định. Chỉ tạo được khi khóa học còn ở trạng thái cho phép thông báo; trạng thái khác không được tự retry.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Tạo thông báo thành công", content = @Content(examples = @ExampleObject(
                    name = "Tạo thông báo thành công",
                    value = """
                            {
                              "status": 201,
                              "code": "CREATED_0201",
                              "message": "Tạo mới thành công",
                              "data": {
                                "id": "019f7234-aaaa-bbbb-cccc-1234567890ab",
                                "courseId": "019f1234-aaaa-bbbb-cccc-1234567890ab",
                                "authorUserId": "019f1234-bbbb-cccc-dddd-1234567890ab",
                                "title": "Cập nhật bài tập tuần này",
                                "content": "Mentor đã bổ sung một bài tập thực hành.",
                                "createdAt": "2026-09-04T03:20:00Z",
                                "updatedAt": "2026-09-04T03:20:00Z",
                                "publishedAt": "2026-09-04T03:20:00Z"
                              }
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Title hoặc content không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc access token không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "User không phải mentor sở hữu khóa học hoặc khóa học không cho phép tạo thông báo"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy khóa học")
    })
    public ApiResponse<CourseAnnouncementResponse> createAnnouncement(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID courseId,
            @Valid @RequestBody
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Nội dung FE nhập cho thông báo. title tối đa 200 ký tự, content tối đa 20.000 ký tự.",
                    content = @Content(examples = @ExampleObject(
                            name = "Request tạo thông báo",
                            value = """
                                    {
                                      "title": "Cập nhật bài tập tuần này",
                                      "content": "Mentor đã bổ sung một bài tập thực hành."
                                    }
                                    """)))
            CreateCourseAnnouncementRequest request) {
        return ApiResponse.created(announcementService.createAnnouncement(principal.getId(), courseId, request));
    }
}
