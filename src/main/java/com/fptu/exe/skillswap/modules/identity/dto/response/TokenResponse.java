package com.fptu.exe.skillswap.modules.identity.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "JWT Token credentials response")
public class TokenResponse {
    @Schema(description = "JWT access token used for accessing protected endpoints", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;

    @JsonIgnore
    @Schema(hidden = true)
    private String refreshToken;

    @Schema(description = "The authorization header scheme type", example = "Bearer")
    @Builder.Default
    private String tokenType = "Bearer";
}
