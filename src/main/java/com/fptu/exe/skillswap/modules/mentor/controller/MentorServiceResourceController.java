package com.fptu.exe.skillswap.modules.mentor.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.mentor.dto.request.*;
import com.fptu.exe.skillswap.modules.mentor.dto.response.*;
import com.fptu.exe.skillswap.modules.mentor.service.MentorServiceResourceService;
import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import jakarta.validation.Valid; import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*; import java.util.*;
import org.springframework.http.*;
import org.springframework.core.io.InputStreamResource;
import com.fptu.exe.skillswap.modules.mentor.service.LocalPrivateDownloadCredentialService;
import org.springframework.beans.factory.ObjectProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController @RequiredArgsConstructor
@Tag(name = "Mentor Services")
public class MentorServiceResourceController {
  private final MentorServiceResourceService service;
  private final ObjectProvider<LocalPrivateDownloadCredentialService> localCredentials;
  @PostMapping("/api/me/mentor-services/{serviceId}/resources/upload-url") @PreAuthorize("hasRole('MENTOR')") @SecurityRequirement(name = "bearerAuth") @Operation(summary = "Create mentor resource upload intent", description = "Creates a private, purpose-scoped upload intent. The client never submits a storage key.")
  public ApiResponse<MentorServiceResourceUploadUrlResponse> uploadUrl(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID serviceId,@Valid @RequestBody MentorServiceResourceUploadUrlRequest r){return ApiResponse.success(service.createUploadUrl(p.getPublicId(),serviceId,r));}
  @PostMapping("/api/me/mentor-services/{serviceId}/resources") @PreAuthorize("hasRole('MENTOR')") @SecurityRequirement(name = "bearerAuth") @Operation(summary = "Confirm mentor service resource", description = "Verifies a private upload intent and creates the resource metadata.")
  public ApiResponse<MentorServiceResourceResponse> confirm(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID serviceId,@Valid @RequestBody MentorServiceResourceCreateRequest r){return ApiResponse.created(service.confirm(p.getPublicId(),serviceId,r));}
  @GetMapping("/api/me/mentor-services/{serviceId}/resources") @PreAuthorize("hasRole('MENTOR')") @SecurityRequirement(name = "bearerAuth") @Operation(summary = "List managed mentor service resources")
  public ApiResponse<List<MentorServiceResourceResponse>> manage(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID serviceId){return ApiResponse.success(service.manage(p.getPublicId(),serviceId));}
  @PutMapping("/api/me/mentor-services/{serviceId}/resources/{resourceId}") @PreAuthorize("hasRole('MENTOR')") @SecurityRequirement(name = "bearerAuth") @Operation(summary = "Update mentor service resource", description = "Updates metadata with optimistic versioning; file content is immutable.")
  public ApiResponse<MentorServiceResourceResponse> update(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID serviceId,@PathVariable UUID resourceId,@Valid @RequestBody MentorServiceResourceUpdateRequest r){return ApiResponse.success(service.update(p.getPublicId(),serviceId,resourceId,r));}
  @DeleteMapping("/api/me/mentor-services/{serviceId}/resources/{resourceId}") @PreAuthorize("hasRole('MENTOR')") @SecurityRequirement(name = "bearerAuth") @Operation(summary = "Soft delete mentor service resource", description = "Revokes download access immediately and requires the expected resource version.")
  public ApiResponse<Void> delete(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID serviceId,@PathVariable UUID resourceId,@RequestParam Integer expectedVersion){service.delete(p.getPublicId(),serviceId,resourceId,expectedVersion);return ApiResponse.success(null);}
  @GetMapping("/api/mentor-services/{serviceId}/resources") @PreAuthorize("isAuthenticated()") @SecurityRequirement(name = "bearerAuth") @Operation(summary = "List accessible mentor service resources")
  public ApiResponse<List<MentorServiceResourceResponse>> reader(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID serviceId){return ApiResponse.success(service.reader(p.getPublicId(),serviceId));}
  @PostMapping("/api/mentor-service-resources/{resourceId}/download-url") @PreAuthorize("isAuthenticated()") @SecurityRequirement(name = "bearerAuth") @Operation(summary = "Create private mentor resource download URL", description = "Re-authorizes service entitlement and returns a short-lived private credential with no-store response headers.")
  public ResponseEntity<ApiResponse<MentorServiceResourceDownloadResponse>> download(@AuthenticationPrincipal UserPrincipal p,@PathVariable UUID resourceId){
    return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).header(HttpHeaders.PRAGMA,"no-cache").body(ApiResponse.success(service.download(p.getPublicId(),resourceId)));
  }
  @GetMapping("/api/private-download/{token}")
  @Operation(summary = "Local private resource download", description = "Local/test-only bearer credential endpoint. Production uses a storage-provider private download URL.")
  public ResponseEntity<InputStreamResource> privateDownload(@PathVariable String token){
    var credentials=localCredentials.getIfAvailable();
    if(credentials==null) return ResponseEntity.notFound().build();
    var credential=credentials.credential(token);
    if(credential==null) return ResponseEntity.notFound().build();
    var content=service.privateContent(credential.userId(),credential.resourceId());
    String disposition="INLINE".equals(content.presentationMode())?"inline":"attachment";
    return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).contentType(MediaType.parseMediaType(content.contentType()))
      .header(HttpHeaders.CONTENT_DISPOSITION,disposition+"; filename=\"resource\"").body(new InputStreamResource(content.stream()));
  }
}
