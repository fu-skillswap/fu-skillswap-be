package com.fptu.exe.skillswap.shared.exception;

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
    TOO_MANY_REQUESTS(429, "SYS_0010", "error.sys.too_many_requests", "Bạn đang thao tác quá nhanh, vui lòng thử lại sau"),
    METHOD_NOT_ALLOWED(405, "SYS_0011", "error.sys.method_not_allowed", "Phương thức HTTP không được hỗ trợ cho endpoint này"),

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

    // Payment
    PAYMENT_PROVIDER_ERROR(502, "PAY_5001", "error.pay.provider_error", "Cổng thanh toán đang có sự cố, vui lòng thử lại sau"),
    INSUFFICIENT_BALANCE(400, "PAY_5002", "error.pay.insufficient_balance", "Số dư không đủ để thực hiện thao tác này"),

    // Blog
    BLOG_POST_VERSION_CONFLICT(409, "BLOG_4001", "error.blog.post_version_conflict", "Bài viết đã được quản trị viên khác cập nhật"),
    BLOG_FOLLOW_LIMIT_REACHED(409, "BLOG_4002", "error.blog.follow_limit_reached", "Đã đạt giới hạn theo dõi Blog"),

    // Forum moderation
    FORUM_CONTENT_PROHIBITED(400, "FORUM_4201", "error.forum.content_prohibited", "Nội dung chứa cụm từ không được phép"),
    FORUM_PROHIBITED_PHRASE_DUPLICATE(409, "FORUM_4202", "error.forum.prohibited_phrase_duplicate", "Cụm từ cấm đã tồn tại"),

    // Chat
    CHAT_CLIENT_MESSAGE_CONFLICT(409, "CHAT_4101", "error.chat.client_message_conflict", "Client message ID đã được dùng cho nội dung khác"),
    CHAT_MESSAGE_CURSOR_INVALID(400, "CHAT_4102", "error.chat.cursor_invalid", "Cursor tin nhắn không hợp lệ"),
    CHAT_CONVERSATION_READ_ONLY(403, "CHAT_4103", "error.chat.read_only", "Cuộc hội thoại hiện chỉ cho phép xem"),
    CHAT_MESSAGE_NOT_EDITABLE(403, "CHAT_4104", "error.chat.message_not_editable", "Tin nhắn không thể chỉnh sửa"),
    CHAT_MESSAGE_EDIT_WINDOW_EXPIRED(403, "CHAT_4105", "error.chat.edit_window_expired", "Đã hết thời gian chỉnh sửa tin nhắn"),
    CHAT_MESSAGE_VERSION_CONFLICT(409, "CHAT_4106", "error.chat.message_version_conflict", "Tin nhắn đã được cập nhật"),
    CHAT_REPLY_TARGET_INVALID(400, "CHAT_4107", "error.chat.reply_target_invalid", "Tin nhắn được trả lời không hợp lệ"),
    CHAT_ATTACHMENT_INVALID(400, "CHAT_4108", "error.chat.attachment_invalid", "Tệp đính kèm không hợp lệ"),
    CHAT_ATTACHMENT_QUOTA_EXCEEDED(409, "CHAT_4109", "error.chat.attachment_quota", "Đã đạt giới hạn tệp đính kèm"),
    CHAT_ATTACHMENT_EXPIRED(404, "CHAT_4110", "error.chat.attachment_expired", "Tệp đính kèm đã hết hạn"),
    CHAT_ATTACHMENT_REVOKED(404, "CHAT_4111", "error.chat.attachment_revoked", "Tệp đính kèm không còn khả dụng"),
    CHAT_UPLOAD_INTENT_INVALID(400, "CHAT_4112", "error.chat.upload_intent_invalid", "Upload intent không hợp lệ"),

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
