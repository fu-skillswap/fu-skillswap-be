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
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.dto.response.ValidationErrorResponse;
import com.fptu.exe.skillswap.shared.dto.response.VersionConflictData;
import com.fptu.exe.skillswap.shared.ratelimit.RateLimitExceededException;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import com.fptu.exe.skillswap.shared.util.TraceContext;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.http.HttpHeaders;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(VersionConflictException.class)
    public ResponseEntity<ApiResponse<Object>> handleVersionConflict(VersionConflictException ex) {
        return buildResponse(
                ex.getErrorCode(),
                ex.getMessage(),
                new VersionConflictData(ex.getResourceId(), ex.getExpectedVersion(), ex.getCurrentVersion())
        );
    }

    @ExceptionHandler(GeneratedOccurrenceReplacementException.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneratedOccurrenceReplacement(GeneratedOccurrenceReplacementException ex) {
        return buildResponse(ex.getErrorCode(), ex.getMessage(), ex.getData());
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Object>> handleBaseException(BaseException ex) {
        Throwable diagnosticCause = ex.getErrorCode().getStatus() >= 500 ? ex : null;
        return buildResponse(ex.getErrorCode(), ex.getMessage(), null, null, ex.getLogContext(), diagnosticCause);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleRateLimitExceeded(RateLimitExceededException ex) {
        return buildResponse(ex.getErrorCode(), ex.getMessage(), null, ex.getRetryAfterSeconds());
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
                null
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
                        null
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

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String message = ErrorCode.METHOD_NOT_ALLOWED.getMessage();
        if (ex.getSupportedHttpMethods() != null && !ex.getSupportedHttpMethods().isEmpty()) {
            String supportedMethods = ex.getSupportedHttpMethods().stream()
                    .map(method -> method.name())
                    .collect(Collectors.joining(", "));
            message = message + ". Hỗ trợ: " + supportedMethods;
        }
        return buildResponse(ErrorCode.METHOD_NOT_ALLOWED, message);
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
        return buildResponse(ErrorCode.PAYLOAD_TOO_LARGE, "Tệp tải lên vượt quá giới hạn dung lượng cho phép (max_15MB)");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return buildResponse(
                ErrorCode.RESOURCE_CONFLICT,
                "Dữ liệu không hợp lệ hoặc đang xung đột với trạng thái hiện tại",
                null,
                null,
                Map.of(),
                ex
        );
    }

    @ExceptionHandler({DataAccessException.class, JpaSystemException.class})
    public ResponseEntity<ApiResponse<Object>> handleDataAccess(Exception ex) {
        return buildResponse(ErrorCode.DATABASE_ERROR, ErrorCode.DATABASE_ERROR.getMessage(), null, null, Map.of(), ex);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgument(IllegalArgumentException ex) {
        // Raw IllegalArgumentException is an internal invariant failure.
        // Request validation uses Spring validation handlers or BaseException
        // business codes above, so unexpected arguments remain HTTP 500.
        return buildResponse(
                ErrorCode.UNCATEGORIZED_EXCEPTION,
                ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage(),
                null,
                null,
                Map.of(),
                ex
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalState(IllegalStateException ex) {
        return buildResponse(
                ErrorCode.CONFIGURATION_ERROR,
                "Hệ thống đang ở trạng thái không hợp lệ để xử lý yêu cầu",
                null,
                null,
                Map.of(),
                ex
        );
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiResponse<Object>> handleIOException(IOException ex) {
        return buildResponse(ErrorCode.STORAGE_ERROR, ErrorCode.STORAGE_ERROR.getMessage(), null, null, Map.of(), ex);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Object>> handleResponseStatus(ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        ErrorCode errorCode = switch (status) {
            case 404 -> ErrorCode.NOT_FOUND;
            case 403 -> ErrorCode.ACCESS_DENIED;
            case 401 -> ErrorCode.UNAUTHENTICATED;
            case 409 -> ErrorCode.RESOURCE_CONFLICT;
            case 429 -> ErrorCode.TOO_MANY_REQUESTS;
            case 413 -> ErrorCode.PAYLOAD_TOO_LARGE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 422 -> ErrorCode.UNPROCESSABLE_ENTITY;
            default -> status >= 500 ? ErrorCode.UNCATEGORIZED_EXCEPTION : ErrorCode.BAD_REQUEST;
        };
        return buildResponse(errorCode, errorCode.getMessage());
    }

    private ResponseEntity<ApiResponse<Object>> buildResponse(ErrorCode errorCode, String message) {
        return buildResponse(errorCode, message, null);
    }

    private ResponseEntity<ApiResponse<Object>> buildResponse(ErrorCode errorCode, String message, Object data) {
        return buildResponse(errorCode, message, data, null);
    }

    private ResponseEntity<ApiResponse<Object>> buildResponse(
            ErrorCode errorCode,
            String message,
            Object data,
            Long retryAfterSeconds
    ) {
        return buildResponse(errorCode, message, data, retryAfterSeconds, Map.of(), null);
    }

    private ResponseEntity<ApiResponse<Object>> buildResponse(
            ErrorCode errorCode,
            String message,
            Object data,
            Long retryAfterSeconds,
            Map<String, String> logContext,
            Throwable diagnosticCause
    ) {
        String correlationId = applyCorrelationHeaders();
        logHandledError(errorCode, correlationId, logContext, diagnosticCause);
        ApiResponse<Object> response = ApiResponse.builder()
                .timestamp(DateTimeUtil.instantNow())
                .status(errorCode.getStatus())
                .code(errorCode.getCode())
                .message(message != null ? message : errorCode.getMessage())
                .data(data)
                .retryAfterSeconds(retryAfterSeconds)
                .build();
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.valueOf(errorCode.getStatus()));
        builder.header(TraceContext.CORRELATION_ID_HEADER, correlationId);
        builder.header(TraceContext.TRACE_ID_HEADER, correlationId);
        if (retryAfterSeconds != null) {
            builder.header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        }
        return builder.body(response);
    }

    private ResponseEntity<ApiResponse<Object>> buildValidationResponse(org.springframework.validation.BindingResult bindingResult) {
        List<ValidationErrorResponse> errors = bindingResult.getFieldErrors()
                .stream()
                .map(error -> new ValidationErrorResponse(
                        error.getField(),
                        error.getDefaultMessage(),
                        null
                ))
                .collect(Collectors.toList());
        String message = errors.isEmpty() ? "Dữ liệu đầu vào không hợp lệ" : errors.getFirst().message();
        return buildResponse(ErrorCode.INVALID_INPUT, message, errors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleAllExceptions(Exception ex) {
        return buildResponse(
                ErrorCode.UNCATEGORIZED_EXCEPTION,
                ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage(),
                null,
                null,
                Map.of(),
                ex
        );
    }

    private String applyCorrelationHeaders() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        String correlationId = TraceContext.getCurrentTraceId();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return TraceContext.ensureCurrentTraceId();
        }

        HttpServletRequest request = servletAttributes.getRequest();
        HttpServletResponse response = servletAttributes.getResponse();
        if (correlationId == null) {
            correlationId = TraceContext.resolveAndSet(
                    request.getHeader(TraceContext.CORRELATION_ID_HEADER),
                    request.getHeader(TraceContext.TRACE_ID_HEADER));
        }
        if (response != null) {
            response.setHeader(TraceContext.CORRELATION_ID_HEADER, correlationId);
            response.setHeader(TraceContext.TRACE_ID_HEADER, correlationId);
        }
        return correlationId;
    }

    private void logHandledError(
            ErrorCode errorCode,
            String correlationId,
            Map<String, String> logContext,
            Throwable diagnosticCause
    ) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        String method = null;
        String path = null;
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            method = servletAttributes.getRequest().getMethod();
            path = servletAttributes.getRequest().getRequestURI();
        }
        String template = "Handled API error code={} status={} correlationId={} method={} path={} context={}";
        Object[] arguments = {
                errorCode.getCode(), errorCode.getStatus(), correlationId, method, path,
                logContext == null ? Map.of() : logContext
        };
        if (diagnosticCause == null) {
            log.warn(template, arguments);
        } else {
            Object[] errorArguments = new Object[arguments.length + 1];
            System.arraycopy(arguments, 0, errorArguments, 0, arguments.length);
            errorArguments[arguments.length] = diagnosticCause;
            log.error(template, errorArguments);
        }
    }
}

