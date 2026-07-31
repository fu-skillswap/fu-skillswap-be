package com.fptu.exe.skillswap.modules.booking.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateGroupSessionRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.GroupSessionVersionRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.UpdateGroupSessionRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.UpdateGroupSessionCapacityRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.GroupSessionMeetingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.GroupSessionAttendanceRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.GroupSessionAttendeeResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.GroupSessionResponse;
import com.fptu.exe.skillswap.modules.booking.dto.response.GroupSessionExperienceResponse;
import com.fptu.exe.skillswap.modules.booking.service.GroupSessionCommerceService;
import com.fptu.exe.skillswap.modules.booking.service.GroupSessionExperienceService;
import com.fptu.exe.skillswap.modules.booking.service.GroupSessionManagementService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MENTOR')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Group Sessions", description = "Mentor group-session supply, capacity and attendee roster APIs.")
@ConditionalOnProperty(prefix = "application.group-sessions", name = "enabled", havingValue = "true")
public class GroupSessionController {

    private final GroupSessionManagementService groupSessionManagementService;
    private final GroupSessionCommerceService groupSessionCommerceService;
    private final GroupSessionExperienceService groupSessionExperienceService;

    @PostMapping("/mentor-services/{serviceId}/group-sessions")
    @Operation(summary = "Create a group-session draft")
    public ApiResponse<GroupSessionResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID serviceId,
            @Valid @RequestBody CreateGroupSessionRequest request
    ) {
        return ApiResponse.success(groupSessionManagementService.create(requireUser(principal), serviceId, request));
    }

    @GetMapping("/mentor-services/{serviceId}/group-sessions")
    @Operation(summary = "List my group sessions for a service")
    public ApiResponse<List<GroupSessionResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID serviceId
    ) {
        return ApiResponse.success(groupSessionManagementService.listOwned(requireUser(principal), serviceId));
    }

    @GetMapping("/group-sessions/{groupSessionId}")
    @Operation(summary = "Get my group-session detail")
    public ApiResponse<GroupSessionResponse> detail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupSessionId
    ) {
        return ApiResponse.success(groupSessionManagementService.getOwned(requireUser(principal), groupSessionId));
    }

    @PutMapping("/group-sessions/{groupSessionId}")
    @Operation(summary = "Update a draft group session")
    public ApiResponse<GroupSessionResponse> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupSessionId,
            @Valid @RequestBody UpdateGroupSessionRequest request
    ) {
        return ApiResponse.success(groupSessionManagementService.update(requireUser(principal), groupSessionId, request));
    }

    @PostMapping("/group-sessions/{groupSessionId}/publish")
    @Operation(summary = "Publish a group-session reservation")
    public ApiResponse<GroupSessionResponse> publish(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupSessionId,
            @Valid @RequestBody GroupSessionVersionRequest request
    ) {
        return ApiResponse.success(groupSessionManagementService.publish(requireUser(principal), groupSessionId, request));
    }

    @PostMapping("/group-sessions/{groupSessionId}/close-registration")
    @Operation(summary = "Close group-session registration")
    public ApiResponse<GroupSessionResponse> closeRegistration(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupSessionId,
            @Valid @RequestBody GroupSessionVersionRequest request
    ) {
        return ApiResponse.success(groupSessionManagementService.closeRegistration(requireUser(principal), groupSessionId, request));
    }

    @PostMapping("/group-sessions/{groupSessionId}/increase-capacity")
    @Operation(summary = "Increase group-session capacity without reopening registration")
    public ApiResponse<GroupSessionResponse> increaseCapacity(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupSessionId,
            @Valid @RequestBody UpdateGroupSessionCapacityRequest request
    ) {
        return ApiResponse.success(groupSessionManagementService.increaseCapacity(requireUser(principal), groupSessionId, request));
    }

    @GetMapping("/group-sessions/{groupSessionId}/attendees")
    @Operation(summary = "List group-session attendee roster")
    public ApiResponse<CursorPageResponse<GroupSessionAttendeeResponse>> attendees(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupSessionId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success(groupSessionCommerceService.listRoster(requireUser(principal), groupSessionId, limit));
    }

    @GetMapping("/group-sessions/{groupSessionId}/experience")
    @Operation(summary = "Get shared group meeting and chat experience")
    public ApiResponse<GroupSessionExperienceResponse> experience(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID groupSessionId) {
        return ApiResponse.success(groupSessionExperienceService.getExperience(requireUser(principal), groupSessionId));
    }

    @PutMapping("/group-sessions/{groupSessionId}/meeting")
    @Operation(summary = "Configure the shared group meeting")
    public ApiResponse<GroupSessionExperienceResponse> updateMeeting(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID groupSessionId,
            @Valid @RequestBody GroupSessionMeetingRequest request) {
        return ApiResponse.success(groupSessionExperienceService.updateMeeting(requireUser(principal), groupSessionId, request));
    }

    @PostMapping("/group-sessions/{groupSessionId}/attendance")
    @Operation(summary = "Submit the immutable group attendance roster")
    public ApiResponse<GroupSessionExperienceResponse> submitAttendance(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID groupSessionId,
            @Valid @RequestBody GroupSessionAttendanceRequest request) {
        return ApiResponse.success(groupSessionExperienceService.submitAttendance(requireUser(principal), groupSessionId, request));
    }

    @PostMapping("/group-sessions/{groupSessionId}/cancel")
    @Operation(summary = "Cancel a draft or open group session")
    public ApiResponse<GroupSessionResponse> cancel(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID groupSessionId,
            @Valid @RequestBody GroupSessionVersionRequest request
    ) {
        return ApiResponse.success(groupSessionManagementService.cancel(requireUser(principal), groupSessionId, request));
    }

    private UUID requireUser(UserPrincipal principal) {
        if (principal == null) {
            throw new BaseException(ErrorCode.UNAUTHENTICATED, "Chưa xác thực người dùng");
        }
        return principal.getPublicId();
    }
}
