package com.fptu.exe.skillswap.shared.idempotency;

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
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@Order(1) // Run before transactions
@RequiredArgsConstructor
@Slf4j
public class IdempotencyAspect {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    @Around("@annotation(com.fptu.exe.skillswap.shared.idempotency.Idempotent)")
    public Object checkIdempotency(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        String idempotencyKey = request.getHeader("Idempotency-Key");

        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            // Log warning or throw exception if idempotency key is strictly required. 
            // We'll allow processing if no key is provided for backward compatibility, 
            // but log a warning.
            log.warn("Missing Idempotency-Key header for path: {}", request.getRequestURI());
            return joinPoint.proceed();
        }

        idempotencyKey = idempotencyKey.trim();

        if (idempotencyKeyRepository.existsById(idempotencyKey)) {
            log.warn("Idempotency key conflict: {}", idempotencyKey);
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Request đã được xử lý (Idempotency conflict)");
        }

        IdempotencyKey keyEntity = IdempotencyKey.builder()
                .key(idempotencyKey)
                .method(request.getMethod())
                .path(request.getRequestURI())
                .createdAt(DateTimeUtil.now())
                .build();

        try {
            idempotencyKeyRepository.saveAndFlush(keyEntity);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            log.warn("Idempotency key conflict (concurrent): {}", idempotencyKey);
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Request đang được xử lý (Concurrent request)");
        }

        return joinPoint.proceed();
    }
}
