package com.fptu.exe.skillswap.modules.identity.dto.response;

import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserMeResponse {
    private UUID publicId;
    private String email;
    private String fullName;
    private String avatarUrl;
    private UserStatus status;
    private List<RoleCode> roles;
}
