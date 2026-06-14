package com.fptu.exe.skillswap.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.MethodArgumentConversionNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;

import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Object>> handleBaseException(BaseException ex) {
        return buildResponse(ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDeniedException(org.springframework.security.access.AccessDeniedException ex) {
        return buildResponse(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("Dữ liệu gửi lên không hợp lệ");

        return buildResponse(ErrorCode.INVALID_INPUT, msg);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String msg = String.format("Giá trị không hợp lệ cho tham số '%s': %s",
                ex.getName(), ex.getValue());
        return buildResponse(ErrorCode.INVALID_INPUT, msg);
    }

    @ExceptionHandler(MethodArgumentConversionNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleConversionNotSupported(MethodArgumentConversionNotSupportedException ex) {
        String msg = String.format("Giá trị không hợp lệ cho tham số '%s'", ex.getName());
        return buildResponse(ErrorCode.INVALID_INPUT, msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations()
                .stream()
                .map(violation -> violation.getMessage())
                .findFirst()
                .orElse("Dữ liệu gửi lên không hợp lệ");
        return buildResponse(ErrorCode.INVALID_INPUT, msg);
    }

    @ExceptionHandler({HttpMediaTypeNotSupportedException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Object>> handleUnreadableRequest(Exception ex) {
        return buildResponse(ErrorCode.INVALID_INPUT, "Dữ liệu gửi lên không đúng định dạng mà API hỗ trợ");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingRequestPart(MissingServletRequestPartException ex) {
        return buildResponse(ErrorCode.INVALID_INPUT, "Thiếu dữ liệu bắt buộc: " + ex.getRequestPartName());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return buildResponse(ErrorCode.PAYLOAD_TOO_LARGE, "Tệp tải lên vượt quá giới hạn dung lượng cho phép");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.error("Data integrity violation", ex);
        return buildResponse(ErrorCode.RESOURCE_CONFLICT, "Dữ liệu không hợp lệ hoặc đang xung đột với trạng thái hiện tại");
    }

    @ExceptionHandler({DataAccessException.class, JpaSystemException.class})
    public ResponseEntity<ApiResponse<Object>> handleDataAccess(Exception ex) {
        log.error("Database access error", ex);
        return buildResponse(ErrorCode.DATABASE_ERROR, "Không thể xử lý dữ liệu do lỗi hệ thống lưu trữ");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildResponse(ErrorCode.INVALID_INPUT, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalState(IllegalStateException ex) {
        log.error("Illegal state", ex);
        return buildResponse(
                ErrorCode.CONFIGURATION_ERROR,
                ex.getMessage() != null ? ex.getMessage() : "Hệ thống đang ở trạng thái không hợp lệ để xử lý yêu cầu"
        );
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiResponse<Object>> handleIOException(IOException ex) {
        log.error("I/O error", ex);
        return buildResponse(ErrorCode.STORAGE_ERROR, ex.getMessage() != null ? ex.getMessage() : ErrorCode.STORAGE_ERROR.getMessage());
    }

    private ResponseEntity<ApiResponse<Object>> buildResponse(ErrorCode errorCode, String message) {
        ApiResponse<Object> response = ApiResponse.builder()
                .timestamp(DateTimeUtil.now())
                .status(errorCode.getStatus())
                .code(errorCode.getCode())
                .message(message != null ? message : errorCode.getMessage())
                .build();

        return new ResponseEntity<>(response, HttpStatus.valueOf(errorCode.getStatus()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleAllExceptions(Exception ex) {
        log.error("Unhandled Exception: ", ex);
        return buildResponse(ErrorCode.UNCATEGORIZED_EXCEPTION, ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage());
    }
}

