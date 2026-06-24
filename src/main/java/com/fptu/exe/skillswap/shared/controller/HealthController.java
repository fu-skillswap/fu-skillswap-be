package com.fptu.exe.skillswap.shared.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "System", description = "Server health check and diagnostic tools")
public class HealthController {

    @Operation(
        summary = "Kiểm tra trạng thái service",
        description = "Trả về phản hồi health đơn giản để đội vận hành kiểm tra service còn hoạt động hay không. Theo cấu hình security hiện tại, endpoint này vẫn đang yêu cầu xác thực, nên FE hoặc đội vận hành cần xem đây là API kỹ thuật có bảo vệ auth."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Server đang hoạt động bình thường")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("OK");
    }
}
