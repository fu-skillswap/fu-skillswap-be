package com.fptu.exe.skillswap.shared.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
public enum ErrorCode {
    // System & General
    UNCATEGORIZED_EXCEPTION(500, "SYS_9999", "error.sys.unknown", "Lỗi hệ thống không xác định"),
    INVALID_KEY(400, "SYS_0001", "error.sys.invalid_key", "Khóa không hợp lệ"),
    BAD_REQUEST(400, "SYS_0002", "error.sys.bad_request", "Yêu cầu không hợp lệ"),
    CONFIGURATION_ERROR(500, "SYS_0004", "error.sys.configuration", "Cấu hình hệ thống chưa hợp lệ"),
    STORAGE_ERROR(500, "SYS_0005", "error.sys.storage", "Hệ thống lưu trữ hiện không khả dụng"),
    DATABASE_ERROR(500, "SYS_0006", "error.sys.database", "Hệ thống dữ liệu gặp sự cố"),
    RESOURCE_CONFLICT(409, "SYS_0007", "error.sys.conflict", "Dữ liệu xung đột với trạng thái hiện tại"),
    PAYLOAD_TOO_LARGE(413, "SYS_0008", "error.sys.payload_too_large", "Dữ liệu tải lên vượt quá giới hạn cho phép"),
    UNSUPPORTED_MEDIA_TYPE(415, "SYS_0009", "error.sys.unsupported_media_type", "Kiểu dữ liệu gửi lên không được hỗ trợ"),

    // Auth
    UNAUTHENTICATED(401, "AUTH_1001", "error.auth.unauthenticated", "Chưa xác thực người dùng"),
    UNAUTHORIZED(403, "AUTH_1002", "error.auth.unauthorized", "Bạn không có quyền truy cập tài nguyên này"),
    ACCESS_DENIED(403, "AUTH_1007", "error.auth.access_denied", "Bạn không có quyền thực hiện hành động này"),
    SESSION_EXPIRED(401, "AUTH_1003", "error.auth.session_expired", "Phiên đăng nhập đã hết hạn hoặc không hợp lệ"),
    USER_BANNED(403, "AUTH_1004", "error.auth.user_banned", "Tài khoản của bạn đã bị khóa"),
    USER_INACTIVE(403, "AUTH_1005", "error.auth.user_inactive", "Tài khoản của bạn chưa hoạt động"),
    OAUTH_VERIFICATION_FAILED(400, "AUTH_1006", "error.auth.oauth_failed", "Xác thực tài khoản Google thất bại"),

    // Business
    USER_EXISTED(400, "USER_2001", "error.user.existed", "Người dùng đã tồn tại"),
    EMAIL_EXISTED(400, "USER_2002", "error.user.email_existed", "Email đã tồn tại"),
    USER_NOT_FOUND(404, "USER_2003", "error.user.not_found", "Không tìm thấy người dùng"),
    NOT_FOUND(404, "SYS_0003", "error.sys.not_found", "Không tìm thấy tài nguyên"),

    // Validation
    INVALID_INPUT(400, "VAL_3001", "error.val.invalid_input", "Dữ liệu đầu vào không hợp lệ");

    private final int status;
    private final String code;
    private final String key;
    private final String message;

    ErrorCode(int status, String code, String key, String message) {
        this.status = status;
        this.code = code;
        this.key = key;
        this.message = message;
    }
}

