package com.fptu.exe.skillswap.modules.course.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.course.dto.request.*;
import com.fptu.exe.skillswap.modules.course.dto.response.*;
import com.fptu.exe.skillswap.modules.course.service.CourseProgressService;
import com.fptu.exe.skillswap.modules.course.service.CourseVaultService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Course curriculum", description = "Quản lý chương và tài liệu học tập dạng video hoặc PDF trong khóa học.")
@SecurityRequirement(name = "bearerAuth")
public class CourseVaultController {
    private final CourseVaultService courseVaultService;
    private final CourseProgressService courseProgressService;

    @Operation(summary = "Mentor khởi tạo upload video", description = "Mentor sở hữu khóa học gọi trước khi upload video. Backend trả upload intent có thời hạn; FE upload theo URL được cấp rồi chờ material chuyển sang `READY`. Không lưu hoặc tự sửa thông tin provider.")
    @PostMapping("/me/mentor/courses/{courseId}/chapters/{chapterId}/materials/video/upload-intent")
    public ApiResponse<CourseVideoUploadInitResponse> createVideoUpload(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @PathVariable UUID chapterId, @Valid @RequestBody CreateVideoMaterialRequest request) {
        return ApiResponse.success(courseVaultService.createVideoUpload(principal.getId(), courseId, chapterId, request));
    }

    @Operation(summary = "Mentor khởi tạo upload video lên R2", description = "API dùng cho video MP4 provider-neutral. Mentor sở hữu khóa học gọi để nhận presigned upload URL, upload trực tiếp bằng đúng Content-Type, rồi gọi confirm-video-upload. API Bunny cũ vẫn giữ nguyên cho client hiện tại; video R2 đã sẵn sàng playback qua route streaming của VPS sau khi material chuyển sang READY.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đã tạo upload intent; FE upload trực tiếp bằng uploadUrl trước thời điểm expiresAt."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Thông tin file hoặc lifecycle upload không hợp lệ."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "User không sở hữu khóa học/chapter."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Vị trí material đã được sử dụng."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "Video vượt giới hạn kích thước."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "MVP chỉ hỗ trợ video/mp4.")
    })
    @PostMapping("/me/mentor/courses/{courseId}/chapters/{chapterId}/materials/video/r2-upload-intent")
    public ApiResponse<CourseR2VideoUploadIntentResponse> createR2VideoUploadIntent(@AuthenticationPrincipal UserPrincipal principal,
                                                                                      @PathVariable UUID courseId,
                                                                                      @PathVariable UUID chapterId,
                                                                                      @Valid @RequestBody CreateR2VideoUploadIntentRequest request) {
        return ApiResponse.success(courseVaultService.createR2VideoUploadIntent(principal.getId(), courseId, chapterId, request));
    }

    @Operation(summary = "Mentor xác nhận upload video R2", description = "Gọi sau khi FE PUT file thành công lên uploadUrl. Backend kiểm tra intent, thời hạn, object tồn tại, MIME type và kích thước rồi chuyển material sang READY. Gọi lại sau khi đã READY là an toàn và không tạo thay đổi mới.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Video đã được xác nhận và sẵn sàng cho playback."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Intent hết hạn, object chưa tồn tại hoặc metadata file không hợp lệ."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "User không sở hữu material."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy course hoặc material."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Material không ở trạng thái có thể xác nhận.")
    })
    @PostMapping("/me/mentor/courses/{courseId}/materials/{materialId}/confirm-video-upload")
    public ApiResponse<Void> confirmR2VideoUpload(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable UUID courseId,
                                                   @PathVariable UUID materialId) {
        courseVaultService.confirmR2VideoUpload(principal.getId(), courseId, materialId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "Mentor khởi tạo upload PDF", description = "Mentor sở hữu khóa học gọi trước khi upload PDF. Upload URL có thời hạn; nếu hết hạn, khởi tạo intent mới. Sau khi upload thành công, gọi confirm với đúng giá trị backend đã cấp.")
    @PostMapping("/me/mentor/courses/{courseId}/chapters/{chapterId}/materials/pdf/upload-intent")
    public ApiResponse<CoursePdfUploadInitResponse> createPdfUpload(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @PathVariable UUID chapterId, @Valid @RequestBody CreatePdfMaterialUploadRequest request) {
        return ApiResponse.success(courseVaultService.createPdfUpload(principal.getId(), courseId, chapterId, request));
    }

    @Operation(summary = "Mentor xác nhận upload PDF", description = "Gọi sau khi upload PDF thành công bằng intent trước đó. FE gửi đúng `objectKey` được trả về trong intent; không tự tạo object key. Sau khi xác nhận, material được backend kiểm tra và xử lý.")
    @PostMapping("/me/mentor/courses/{courseId}/materials/{materialId}/confirm-pdf-upload")
    public ApiResponse<Void> confirmPdfUpload(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @PathVariable UUID materialId, @Valid @RequestBody ConfirmCoursePdfUploadRequest request) {
        courseVaultService.confirmPdfUpload(principal.getId(), courseId, materialId, request.objectKey());
        return ApiResponse.success(null);
    }

    @Operation(summary = "Lấy danh sách tài liệu khóa học", description = "User đã đăng nhập gọi sau khi mở course detail. Mentor xem toàn bộ tài liệu; người học xem các material đã công bố. `available=true` mới cho phép gọi playback/download; nếu false, hiển thị `lockedReason` hoặc `userActionMessage`. User chưa enrollment vẫn nhận được danh sách và trạng thái khóa, không phải lỗi.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Danh sách tài liệu theo thứ tự chương", content = @Content(examples = @ExampleObject(
                    name = "Tài liệu đã mở và bị khóa",
                    value = """
                            {
                              "status": 200,
                              "code": "SUCCESS_0200",
                              "message": "Thành công",
                              "data": [
                                {
                                  "materialId": "019f3234-aaaa-bbbb-cccc-1234567890ab",
                                  "chapterId": "019f2234-aaaa-bbbb-cccc-1234567890ab",
                                  "title": "Dependency Injection",
                                  "materialType": "VIDEO",
                                  "storageProviderType": "BUNNY_VIDEO",
                                  "status": "READY",
                                  "durationSeconds": 420,
                                  "thumbnailUrl": "https://cdn.example/thumbnail.jpg",
                                  "uploadedAt": "2026-09-04T03:15:30Z",
                                  "available": true,
                                  "lockedReason": null,
                                  "userActionMessage": null,
                                  "retryable": false
                                },
                                {
                                  "materialId": "019f4234-aaaa-bbbb-cccc-1234567890ab",
                                  "chapterId": "019f2234-aaaa-bbbb-cccc-1234567890ab",
                                  "title": "Bài tập thực hành",
                                  "materialType": "PDF",
                                  "storageProviderType": "LOCAL",
                                  "status": "READY",
                                  "durationSeconds": null,
                                  "thumbnailUrl": null,
                                  "uploadedAt": "2026-09-04T03:16:00Z",
                                  "available": false,
                                  "lockedReason": "NOT_ENROLLED",
                                  "userActionMessage": "Bạn chưa có quyền truy cập tài liệu này.",
                                  "retryable": false
                                }
                              ]
                            }
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Chưa có quyền truy cập khóa học"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy khóa học")
    })
    @GetMapping("/me/courses/{courseId}/materials")
    public ApiResponse<List<CourseMaterialSummaryResponse>> getCourseMaterials(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId) {
        return ApiResponse.success(courseVaultService.getCourseMaterials(principal.getId(), courseId));
    }

    @Operation(summary = "Lấy quyền xem video khóa học", description = "Gọi khi material là video đã công bố và `status=READY`. Material Bunny tiếp tục trả URL playback Bunny; material R2 trả URL ngắn hạn tới route streaming của VPS. Material previewable hoặc user có enrollment ACTIVE/COMPLETED mới được cấp quyền. FE không gọi R2 trực tiếp và cần gọi lại API khi URL hết hạn.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Trả URL playback tạm thời; browser dùng URL này để phát MP4 và gửi HTTP Range khi seek"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Material không phải video hoặc video chưa ở trạng thái READY"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc access token không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "User chưa có quyền xem material này"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy khóa học, material hoặc video storage reference")
    })
    @GetMapping("/me/courses/{courseId}/materials/{materialId}/playback")
    public ApiResponse<CourseVideoPlaybackResponse> getPlaybackUrl(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @PathVariable UUID materialId, HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String clientIp = forwarded == null || forwarded.isBlank() || "unknown".equalsIgnoreCase(forwarded) ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        return ApiResponse.success(courseVaultService.getPlaybackAuthorization(principal.getId(), courseId, materialId, clientIp));
    }

    @Operation(summary = "Lấy URL tải PDF khóa học", description = "Gọi khi material là PDF, đã công bố và `status=READY`. Material previewable hoặc user có enrollment ACTIVE/COMPLETED mới được cấp URL tải tạm thời. Nếu URL hết hạn, gọi lại API khi user vẫn còn quyền.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Trả URL download tạm thời cho PDF đã sẵn sàng"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Material không phải PDF hoặc PDF chưa ở trạng thái READY"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc access token không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "User chưa có quyền tải material này"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy khóa học hoặc material")
    })
    @GetMapping("/me/courses/{courseId}/materials/{materialId}/download")
    public ApiResponse<CourseMaterialDownloadResponse> getPdfDownload(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @PathVariable UUID materialId) {
        return ApiResponse.success(courseVaultService.getPdfDownload(principal.getId(), courseId, materialId));
    }

    @Operation(summary = "Cập nhật tiến độ xem video", description = "User có quyền học gửi số giây đã xem. Backend tự xác định user từ JWT; FE không gửi studentId. Khi video đạt ngưỡng hoàn thành, curriculum sẽ phản ánh progress mới. Nếu user không có enrollment ACTIVE/COMPLETED, trả 403.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đã lưu tiến độ video"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Số giây xem không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc access token không hợp lệ"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "User chưa có quyền học khóa học"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Không tìm thấy khóa học hoặc material")
    })
    @PutMapping("/me/courses/{courseId}/materials/{materialId}/progress")
    public ApiResponse<Void> updateProgress(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID courseId, @PathVariable UUID materialId, @Valid @RequestBody UpdateCourseMaterialProgressRequest request) {
        courseProgressService.updateMaterialProgress(principal.getId(), courseId, materialId, request.watchedSeconds());
        return ApiResponse.success(null);
    }
}
