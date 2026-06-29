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
        summary = "Kiểm tra trạng thái service",
        description = "Trả về phản hồi health đơn giản để Docker/reverse proxy/đội vận hành kiểm tra service còn hoạt động hay không. Endpoint này không trả dữ liệu nghiệp vụ hoặc thông tin nhạy cảm."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Server đang hoạt động bình thường")
    })
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("OK");
    }
}
