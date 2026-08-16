package com.fptu.exe.skillswap.shared.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Trả lại HTTP response thành công đã commit cho các command endpoint nghiêm ngặt.
 * Transaction bao cả command và bản ghi replay, nên command lỗi không để lại key vô dụng.
 */
@Aspect
@Component
// Phải kiểm tra quyền trước để admin thiếu quyền nhận 403, không phải lỗi header.
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class IdempotencyAspect {

    private static final int RETENTION_HOURS = 24;
    private static final String SAFE_KEY_PATTERN = "[A-Za-z0-9._~-]{1,100}";

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Around("@annotation(com.fptu.exe.skillswap.shared.idempotency.Idempotent)")
    public Object checkIdempotency(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }
        HttpServletRequest request = attributes.getRequest();
        String key = normalizeKey(request.getHeader("Idempotency-Key"));
        String fingerprint = fingerprint(joinPoint.getArgs(), request.getMethod(), request.getRequestURI());

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        try {
            return transaction.execute(status -> executeWithinTransaction(joinPoint, request, key, fingerprint));
        } catch (InvocationFailure failure) {
            throw failure.getCause();
        }
    }

    private Object executeWithinTransaction(ProceedingJoinPoint joinPoint,
                                            HttpServletRequest request,
                                            String key,
                                            String fingerprint) {
        LocalDateTime now = DateTimeUtil.now();
        idempotencyKeyRepository.deleteExpired(now);

        List<String> claimed = jdbcTemplate.queryForList("""
                        insert into idempotency_keys
                            (idempotency_key, method, path, request_fingerprint, created_at, expires_at)
                        values (?, ?, ?, ?, ?, ?)
                        on conflict (idempotency_key) do nothing
                        returning idempotency_key
                        """,
                String.class,
                key,
                request.getMethod(),
                request.getRequestURI(),
                fingerprint,
                now,
                now.plusHours(RETENTION_HOURS));
        if (claimed.isEmpty()) {
            return replayOrReject(key, request, fingerprint);
        }

        try {
            Object result = joinPoint.proceed();
            if (result instanceof ResponseEntity<?> response && response.getStatusCode().is2xxSuccessful()) {
                persistReplay(key, response, now);
            }
            return result;
        } catch (Throwable ex) {
            // Transaction bên ngoài rollback claim. Lỗi validation/nghiệp vụ vẫn cho retry cùng key sau khi state đổi.
            throw new InvocationFailure(ex);
        }
    }

    private Object replayOrReject(String key, HttpServletRequest request, String fingerprint) {
        IdempotencyKey existing = idempotencyKeyRepository.findById(key)
                .orElseThrow(() -> new BaseException(ErrorCode.RESOURCE_CONFLICT, "IDEMPOTENCY_REQUEST_IN_PROGRESS"));
        if (!request.getMethod().equals(existing.getMethod())
                || !request.getRequestURI().equals(existing.getPath())
                || !fingerprint.equals(existing.getRequestFingerprint())) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "IDEMPOTENCY_KEY_REUSED");
        }
        if (!existing.isCompleted()) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "IDEMPOTENCY_REQUEST_IN_PROGRESS");
        }
        return ResponseEntity.status(existing.getResponseStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(existing.getResponseBody());
    }

    private void persistReplay(String key, ResponseEntity<?> response, LocalDateTime completedAt) {
        IdempotencyKey record = idempotencyKeyRepository.findById(key)
                .orElseThrow(() -> new BaseException(ErrorCode.DATABASE_ERROR, "Không tìm thấy idempotency claim"));
        try {
            record.setResponseStatus(response.getStatusCode().value());
            record.setResponseBody(objectMapper.writeValueAsString(response.getBody()));
            record.setCompletedAt(completedAt);
            idempotencyKeyRepository.save(record);
        } catch (JsonProcessingException ex) {
            throw new BaseException(ErrorCode.DATABASE_ERROR, "Không thể lưu idempotency response", ex);
        }
    }

    private String normalizeKey(String rawKey) {
        String key = rawKey == null ? null : rawKey.trim();
        if (key == null || !key.matches(SAFE_KEY_PATTERN)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED");
        }
        return key;
    }

    private String fingerprint(Object[] args, String method, String path) {
        Object requestBody = java.util.Arrays.stream(args)
                .filter(arg -> arg != null && arg.getClass().getPackageName().contains(".dto.request"))
                .findFirst()
                .orElse(List.of());
        try {
            String canonical = method + "\n" + path + "\n" + objectMapper.writeValueAsString(requestBody);
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BaseException(ErrorCode.DATABASE_ERROR, "Không thể tạo idempotency fingerprint", ex);
        }
    }

    @RequiredArgsConstructor
    private static final class InvocationFailure extends RuntimeException {
        @Override
        public synchronized Throwable getCause() {
            return super.getCause();
        }

        private InvocationFailure(Throwable cause) {
            super(cause);
        }
    }
}
