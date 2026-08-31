package com.fptu.exe.skillswap.modules.identity.support;

import java.util.UUID;

/** Test fixture providing standardized identity test data, user snapshots, and role definitions. */
public final class IdentityTestFixture {

    private IdentityTestFixture() {}

    public static UUID randomUserId() {
        return UUID.randomUUID();
    }

    public static UserSnapshot createSampleUserSnapshot(String role) {
        UUID id = UUID.randomUUID();
        return new UserSnapshot(
                id,
                "user-" + id.toString().substring(0, 8) + "@fpt.edu.vn",
                "User " + id.toString().substring(0, 6),
                role != null ? role : "STUDENT",
                "ACTIVE",
                true
        );
    }

    public static UserSnapshot createStudentSnapshot() {
        return createSampleUserSnapshot("STUDENT");
    }

    public static UserSnapshot createMentorSnapshot() {
        return createSampleUserSnapshot("MENTOR");
    }

    public static UserSnapshot createAdminSnapshot() {
        return createSampleUserSnapshot("ADMIN");
    }

    public record UserSnapshot(
            UUID userId,
            String email,
            String fullName,
            String role,
            String status,
            boolean isEmailVerified
    ) {}
}
