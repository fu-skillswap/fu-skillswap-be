package com.fptu.exe.skillswap.modules.identity.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Public value types for administration use cases exposed by Identity. */
public final class IdentityAdminPortModels {

    private IdentityAdminPortModels() {
    }

    public record AdminUserListQuery(
            String keyword, String role, String status,
            int page, int size, String sortBy, String direction
    ) {
    }

    public record UserListItem(
            UUID userId, String email, String fullName, String avatarUrl,
            String status, List<String> roles, LocalDateTime lastLoginAt,
            LocalDateTime createdAt
    ) {
    }

    public record AdminUserView(
            UUID userId, String email, String fullName, String avatarUrl,
            String status, UUID assignedBy, LocalDateTime assignedAt
    ) {
    }

    public record SystemUserView(
            UUID userId, String email, String fullName, String avatarUrl,
            String status, List<String> roles, LocalDateTime lastLoginAt,
            LocalDateTime createdAt
    ) {
    }

    public record VisibleUserSummary(
            UUID userId, String email, String fullName, String avatarUrl,
            String status, List<String> roles, LocalDateTime lastLoginAt, LocalDateTime createdAt
    ) {
    }

}
