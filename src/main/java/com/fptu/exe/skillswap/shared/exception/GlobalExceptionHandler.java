package com.fptu.exe.skillswap.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.MethodArgumentConversionNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.ValidationErrorResponse;
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
        return buildResponse(ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        return buildValidationResponse(ex.getBindingResult());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Object>> handleBindException(BindException ex) {
        return buildValidationResponse(ex.getBindingResult());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ValidationErrorResponse error = new ValidationErrorResponse(
                ex.getName(),
                String.format("Giá trị không hợp lệ cho tham số '%s'", ex.getName()),
                ex.getValue()
        );
        return buildResponse(ErrorCode.INVALID_INPUT, error.message(), List.of(error));
    }

    @ExceptionHandler(MethodArgumentConversionNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleConversionNotSupported(MethodArgumentConversionNotSupportedException ex) {
        ValidationErrorResponse error = new ValidationErrorResponse(
                ex.getName(),
                String.format("Giá trị không hợp lệ cho tham số '%s'", ex.getName()),
                null
        );
        return buildResponse(ErrorCode.INVALID_INPUT, error.message(), List.of(error));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(ConstraintViolationException ex) {
        List<ValidationErrorResponse> errors = ex.getConstraintViolations()
                .stream()
                .map(violation -> new ValidationErrorResponse(
                        violation.getPropertyPath() == null ? null : violation.getPropertyPath().toString(),
                        violation.getMessage(),
                        violation.getInvalidValue()
                ))
                .collect(Collectors.toList());
        String message = errors.isEmpty() ? "Dữ liệu đầu vào không hợp lệ" : errors.getFirst().message();
        return buildResponse(ErrorCode.INVALID_INPUT, message, errors);
    }

    @ExceptionHandler({HttpMediaTypeNotSupportedException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Object>> handleUnreadableRequest(Exception ex) {
        if (ex instanceof HttpMediaTypeNotSupportedException mediaTypeEx) {
            ValidationErrorResponse error = new ValidationErrorResponse(
                    "Content-Type",
                    "Content-Type không được hỗ trợ",
                    mediaTypeEx.getContentType()
            );
            return buildResponse(ErrorCode.UNSUPPORTED_MEDIA_TYPE, error.message(), List.of(error));
        }
        return buildResponse(ErrorCode.INVALID_INPUT, "Body request không hợp lệ hoặc không đúng định dạng mà API hỗ trợ");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingRequestPart(MissingServletRequestPartException ex) {
        ValidationErrorResponse error = new ValidationErrorResponse(
                ex.getRequestPartName(),
                "Thiếu dữ liệu bắt buộc",
                null
        );
        return buildResponse(ErrorCode.INVALID_INPUT, error.message(), List.of(error));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingRequestParameter(MissingServletRequestParameterException ex) {
        ValidationErrorResponse error = new ValidationErrorResponse(
                ex.getParameterName(),
                "Thiếu tham số bắt buộc",
                null
        );
        return buildResponse(ErrorCode.INVALID_INPUT, error.message(), List.of(error));
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingPathVariable(MissingPathVariableException ex) {
        ValidationErrorResponse error = new ValidationErrorResponse(
                ex.getVariableName(),
                "Thiếu path variable bắt buộc",
                null
        );
        return buildResponse(ErrorCode.INVALID_INPUT, error.message(), List.of(error));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResourceFound(NoResourceFoundException ex) {
        return buildResponse(ErrorCode.NOT_FOUND, "Không tìm thấy tài nguyên");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return buildResponse(ErrorCode.PAYLOAD_TOO_LARGE, "Tệp tải lên vượt quá giới hạn dung lượng cho phép (max_4MB)");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.error("Data integrity violation", ex);
        return buildResponse(ErrorCode.RESOURCE_CONFLICT, "Dữ liệu không hợp lệ hoặc đang xung đột với trạng thái hiện tại");
    }

    @ExceptionHandler({DataAccessException.class, JpaSystemException.class})
    public ResponseEntity<ApiResponse<Object>> handleDataAccess(Exception ex) {
        log.error("Database access error", ex);
        String detailedMessage = "Không thể xử lý dữ liệu do lỗi hệ thống lưu trữ: " + ex.getMessage();
        if (ex instanceof org.springframework.dao.DataAccessException) {
            try {
                detailedMessage += " | Chi tiết: " + ((org.springframework.dao.DataAccessException) ex).getMostSpecificCause().getMessage();
            } catch (Exception ignored) {}
        }
        return buildResponse(ErrorCode.DATABASE_ERROR, detailedMessage);
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
        return buildResponse(errorCode, message, null);
    }

    private ResponseEntity<ApiResponse<Object>> buildResponse(ErrorCode errorCode, String message, Object data) {
        ApiResponse<Object> response = ApiResponse.builder()
                .timestamp(DateTimeUtil.now())
                .status(errorCode.getStatus())
                .code(errorCode.getCode())
                .message(message != null ? message : errorCode.getMessage())
                .data(data)
                .build();

        return new ResponseEntity<>(response, HttpStatus.valueOf(errorCode.getStatus()));
    }

    private ResponseEntity<ApiResponse<Object>> buildValidationResponse(org.springframework.validation.BindingResult bindingResult) {
        List<ValidationErrorResponse> errors = bindingResult.getFieldErrors()
                .stream()
                .map(error -> new ValidationErrorResponse(
                        error.getField(),
                        error.getDefaultMessage(),
                        error.getRejectedValue()
                ))
                .collect(Collectors.toList());
        String message = errors.isEmpty() ? "Dữ liệu đầu vào không hợp lệ" : errors.getFirst().message();
        return buildResponse(ErrorCode.INVALID_INPUT, message, errors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleAllExceptions(Exception ex) {
        log.error("Unhandled Exception: ", ex);
        return buildResponse(ErrorCode.UNCATEGORIZED_EXCEPTION, ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage());
    }
}

