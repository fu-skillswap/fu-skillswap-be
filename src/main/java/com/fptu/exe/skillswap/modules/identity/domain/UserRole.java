package com.fptu.exe.skillswap.modules.identity.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRole {
    private UserRoleId id;
    private User user;
    private LocalDateTime assignedAt;
    private User assignedBy;
}
