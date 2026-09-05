package com.fptu.exe.skillswap.shared.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // Hệ thống và chung.
    UNCATEGORIZED_EXCEPTION(500, "SYS_9999", "error.sys.unknown", "Lỗi hệ thống không xác định"),
    INVALID_KEY(400, "SYS_0001", "error.sys.invalid_key", "Key không hợp lệ"),
    BAD_REQUEST(400, "SYS_0002", "error.sys.bad_request", "Yêu cầu không hợp lệ"),
    CONFIGURATION_ERROR(500, "SYS_0004", "error.sys.configuration", "Lỗi cấu hình hệ thống"),
    STORAGE_ERROR(500, "SYS_0005", "error.sys.storage", "Lỗi hệ thống lưu trữ"),
    DATABASE_ERROR(500, "SYS_0006", "error.sys.database", "Lỗi hệ thống dữ liệu"),
    RESOURCE_CONFLICT(409, "SYS_0007", "error.sys.conflict", "Dữ liệu xung đột với trạng thái hiện tại"),
    PAYLOAD_TOO_LARGE(413, "SYS_0008", "error.sys.payload_too_large", "Dữ liệu tải lên vượt quá giới hạn"),
    UNSUPPORTED_MEDIA_TYPE(415, "SYS_0009", "error.sys.unsupported_media_type", "Kiểu dữ liệu gửi lên không được hỗ trợ"),
    TOO_MANY_REQUESTS(429, "SYS_0010", "error.sys.too_many_requests", "Thao tác quá nhanh, thử lại sau"),
    METHOD_NOT_ALLOWED(405, "SYS_0011", "error.sys.method_not_allowed", "Phương thức không được hỗ trợ"),
    UNPROCESSABLE_ENTITY(422, "SYS_0012", "error.sys.unprocessable_entity", "Dữ liệu đúng định dạng nhưng không thể xử lý"),
    LEGACY_WEBSOCKET_GONE(410, "WS_0410", "error.websocket.legacy_gone", "Raw WebSocket endpoint /ws đã ngừng hỗ trợ. Vui lòng chuyển sang STOMP /ws-stomp."),

    // Xác thực.
    UNAUTHENTICATED(401, "AUTH_1001", "error.auth.unauthenticated", "Chưa xác thực người dùng"),
    UNAUTHORIZED(403, "AUTH_1002", "error.auth.unauthorized", "Bạn không có quyền truy cập tài nguyên"),
    ACCESS_DENIED(403, "AUTH_1007", "error.auth.access_denied", "Bạn không có quyền thực hiện hành động"),
    SESSION_EXPIRED(401, "AUTH_1003", "error.auth.session_expired", "Phiên đăng nhập đã hết hạn"),
    USER_BANNED(403, "AUTH_1004", "error.auth.user_banned", "Tài khoản đã bị khóa"),
    USER_INACTIVE(403, "AUTH_1005", "error.auth.user_inactive", "Tài khoản chưa hoạt động"),
    OAUTH_VERIFICATION_FAILED(400, "AUTH_1006", "error.auth.oauth_failed", "Xác thực Google thất bại"),

    // Google Calendar.
    GOOGLE_CALENDAR_CONNECTION_REQUIRED(409, "CAL_4401", "error.calendar.connection_required", "Cần kết nối Google Calendar trước"),
    GOOGLE_CALENDAR_MENTOR_VERIFICATION_REQUIRED(409, "CAL_4402", "error.calendar.mentor_verification_required", "Chỉ dành cho mentor đã được duyệt"),
    GOOGLE_CALENDAR_DISCONNECT_BLOCKED(409, "CAL_4403", "error.calendar.disconnect_blocked", "Phải tắt toàn bộ dịch vụ mentoring trước"),
    GOOGLE_CALENDAR_PROVIDER_ERROR(503, "CAL_5001", "error.calendar.provider_error", "Google Calendar tạm thời không khả dụng, vui lòng thử lại sau"),

    // Nghiệp vụ.
    USER_EXISTED(400, "USER_2001", "error.user.existed", "Người dùng đã tồn tại"),
    EMAIL_EXISTED(400, "USER_2002", "error.user.email_existed", "Email đã tồn tại"),
    USER_NOT_FOUND(404, "USER_2003", "error.user.not_found", "Không tìm thấy người dùng"),
    NOT_FOUND(404, "SYS_0003", "error.sys.not_found", "Không tìm thấy tài nguyên"),

    // Thanh toán.
    PAYMENT_PROVIDER_ERROR(502, "PAY_5001", "error.pay.provider_error", "Cổng thanh toán đang gặp sự cố, vui lòng thử lại sau"),
    INSUFFICIENT_BALANCE(400, "PAY_5002", "error.pay.insufficient_balance", "Số dư không đủ để thực hiện thao tác này"),
    PAYMENT_EXPIRED(409, "PAY_5003", "error.pay.expired", "Phiên thanh toán đã hết hạn"),
    PAYMENT_CHECKOUT_FAILED(502, "PAY_5004", "error.pay.checkout_failed", "Không thể tạo thanh toán"),
    VIDEO_PROVIDER_ERROR(502, "VIDEO_5001", "error.video.provider_error", "Dịch vụ video tạm thời không khả dụng, vui lòng thử lại sau"),

    // Khóa học.
    COURSE_ACCESS_DENIED(403, "COURSE_4601", "error.course.access_denied", "Bạn không có quyền truy cập khóa học"),
    COURSE_MATERIAL_LOCKED(403, "COURSE_4602", "error.course.material_locked", "Tài liệu này chưa được mở khóa"),
    COURSE_INVALID_STATUS(409, "COURSE_4603", "error.course.invalid_status", "Trạng thái khóa học không hợp lệ"),
    COURSE_ALREADY_ENROLLED(409, "COURSE_4604", "error.course.already_enrolled", "Bạn đã đăng ký khóa học này"),

    // Booking.
    BOOKING_SLOT_UNAVAILABLE(409, "BOOKING_4001", "error.booking.slot_unavailable", "Khung giờ này không còn khả dụng"),
    BOOKING_ALREADY_EXISTS(409, "BOOKING_4002", "error.booking.already_exists", "Bạn đã có booking cho khung giờ này"),
    BOOKING_INVALID_STATUS(409, "BOOKING_4003", "error.booking.invalid_status", "Trạng thái booking không hợp lệ"),
    BOOKING_EXPIRED(409, "BOOKING_4004", "error.booking.expired", "Booking này đã hết hạn"),

    // Blog.
    BLOG_POST_VERSION_CONFLICT(409, "BLOG_4001", "error.blog.post_version_conflict", "Bài viết đã được quản trị viên khác cập nhật"),
    BLOG_FOLLOW_LIMIT_REACHED(409, "BLOG_4002", "error.blog.follow_limit_reached", "Đã đạt giới hạn theo dõi Blog"),

    // Kiểm duyệt forum.
    FORUM_CONTENT_PROHIBITED(400, "FORUM_4201", "error.forum.content_prohibited", "Nội dung chứa cụm từ không được phép"),
    FORUM_PROHIBITED_PHRASE_DUPLICATE(409, "FORUM_4202", "error.forum.prohibited_phrase_duplicate", "Cụm từ cấm đã tồn tại"),

    // Mẫu lịch rảnh.
    AVAILABILITY_TEMPLATE_NOT_FOUND(404, "AVAIL_4301", "error.availability.template_not_found", "Không tìm thấy availability template"),
    AVAILABILITY_TEMPLATE_VERSION_CONFLICT(409, "AVAIL_4302", "error.availability.template_version_conflict", "Availability template đã được cập nhật"),
    AVAILABILITY_TEMPLATE_OVERLAP(409, "AVAIL_4303", "error.availability.template_overlap", "Availability template bị trùng thời gian"),
    AVAILABILITY_TEMPLATE_LIMIT_EXCEEDED(409, "AVAIL_4304", "error.availability.template_limit", "Đã đạt giới hạn availability template đang hoạt động"),
    AVAILABILITY_TEMPLATE_INVALID_SCHEDULE(400, "AVAIL_4305", "error.availability.template_invalid_schedule", "Lịch availability template không hợp lệ"),
    AVAILABILITY_TEMPLATE_INACTIVE_SERVICE(400, "AVAIL_4306", "error.availability.template_inactive_service", "Service gắn vào template không còn hợp lệ"),
    AVAILABILITY_TEMPLATE_EXPIRED(409, "AVAIL_4307", "error.availability.template_expired", "Availability template đã hết hiệu lực"),
    AVAILABILITY_TEMPLATE_HAS_PENDING_BOOKINGS(409, "AVAIL_4308", "error.availability.template_pending", "Template có booking đang chờ xử lý"),
    AVAILABILITY_TEMPLATE_HAS_LOCKING_BOOKINGS(409, "AVAIL_4309", "error.availability.template_locking", "Template có booking đang giữ lịch"),
    AVAILABILITY_TEMPLATE_INVALID_OCCURRENCE(400, "AVAIL_4310", "error.availability.template_invalid_occurrence", "Ngày occurrence không hợp lệ"),
    AVAILABILITY_TEMPLATE_EXCEPTION_EXISTS(409, "AVAIL_4311", "error.availability.template_exception_exists", "Ngày này đã được bỏ qua"),
    AVAILABILITY_TEMPLATE_EXCEPTION_NOT_FOUND(404, "AVAIL_4312", "error.availability.template_exception_not_found", "Không tìm thấy exception"),
    GENERATED_SLOT_MANAGED_BY_TEMPLATE(409, "AVAIL_4313", "error.availability.generated_slot_managed", "Generated slot phải được quản lý qua template"),
    GENERATED_OCCURRENCE_REPLACEMENT_REQUIRED(409, "AVAIL_4314", "error.availability.generated_replacement_required", "Cần xác nhận thay thế generated occurrence"),
    AVAILABILITY_TEMPLATE_OCCURRENCE_UNAVAILABLE(409, "AVAIL_4315", "error.availability.template_occurrence_unavailable", "Generated occurrence hiện không còn khả dụng"),

    // Chat.
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
    CHAT_CONVERSATION_LOCKED(403, "CHAT_4113", "error.chat.conversation_locked", "Cuộc hội thoại đang bị khóa"),
    CHAT_ACCESS_DENIED(403, "CHAT_4114", "error.chat.access_denied", "Bạn không có quyền truy cập đoạn chat"),
    CHAT_INVALID_MESSAGE(400, "CHAT_4115", "error.chat.invalid_message", "Nội dung tin nhắn không hợp lệ"),
    CHAT_INTERNAL_ERROR(500, "CHAT_5001", "error.chat.internal_error", "Không thể xử lý tin nhắn lúc này, vui lòng thử lại sau"),

    // Minh chứng booking dispute.
    BOOKING_ISSUE_EVIDENCE_INVALID(400, "BOOKING_4501", "error.booking.issue_evidence_invalid", "File minh chứng dispute không hợp lệ"),
    BOOKING_ISSUE_EVIDENCE_INTENT_INVALID(400, "BOOKING_4502", "error.booking.issue_evidence_intent_invalid", "Upload intent minh chứng dispute không hợp lệ"),
    BOOKING_ISSUE_EVIDENCE_HIDDEN(404, "BOOKING_4503", "error.booking.issue_evidence_hidden", "File minh chứng hiện không khả dụng"),

    // Kiểm tra dữ liệu.
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
