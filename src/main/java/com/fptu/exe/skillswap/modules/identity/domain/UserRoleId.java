package com.fptu.exe.skillswap.modules.identity.domain;

import com.fptu.exe.skillswap.shared.constant.RoleCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserRoleId {
    private UUID userId;
    private RoleCode role;
}
