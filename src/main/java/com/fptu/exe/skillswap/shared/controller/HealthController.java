package com.fptu.exe.skillswap.shared.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "System", description = "Server health check and diagnostic tools")
public class HealthController {

    @Operation(
        summary = "Kiểm tra trạng thái server",
        description = "Dùng để xác nhận server đang hoạt động bình thường. " +
            "Thường được dùng bởi load balancer, monitoring tools hoặc CI/CD pipeline để health check. " +
            "API này không yêu cầu xác thực."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Server đang hoạt động bình thường")
    })
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("OK");
    }
}
