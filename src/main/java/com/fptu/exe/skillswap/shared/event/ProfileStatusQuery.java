package com.fptu.exe.skillswap.shared.event;

import java.util.UUID;

/**
 * Synchronous request-reply event dùng để hỏi module academic:
 * "User này đã có StudentProfile chưa?"
 *
 * Module identity publish event này; module academic lắng nghe và set kết quả.
 * Vì event là mutable object nên kết quả được truyền ngược lại cho publisher.
 */
public class ProfileStatusQuery {

    private final UUID userId;
    private boolean hasStudentProfile = false;

    public ProfileStatusQuery(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }

    public boolean isHasStudentProfile() {
        return hasStudentProfile;
    }

    /**
     * Được gọi bởi module academic khi xử lý event.
     */
    public void setHasStudentProfile(boolean hasStudentProfile) {
        this.hasStudentProfile = hasStudentProfile;
    }
}
