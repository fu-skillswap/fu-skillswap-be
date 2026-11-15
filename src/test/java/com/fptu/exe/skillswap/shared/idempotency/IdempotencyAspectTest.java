package com.fptu.exe.skillswap.shared.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IdempotencyAspectTest {

    private final IdempotencyKeyRepository repository = mock(IdempotencyKeyRepository.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final IdempotencyAspect aspect = new IdempotencyAspect(
            repository, jdbcTemplate, new ObjectMapper(), transactionManager);

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void optionalIdempotency_withoutHeader_proceedsNormally() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/me/availability-slots/1/deactivate");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        ProceedingJoinPoint joinPoint = mockJoinPoint("optionalMethod");
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.checkIdempotency(joinPoint);

        assertEquals("ok", result);
        verify(joinPoint).proceed();
        verifyNoInteractions(repository, jdbcTemplate, transactionManager);
    }

    @Test
    void requiredIdempotency_withoutHeader_throwsBadRequest() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/me/bookings/1/cancel");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        ProceedingJoinPoint joinPoint = mockJoinPoint("requiredMethod");

        BaseException ex = assertThrows(BaseException.class, () -> aspect.checkIdempotency(joinPoint));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertEquals("IDEMPOTENCY_KEY_REQUIRED", ex.getMessage());
        verifyNoInteractions(repository, jdbcTemplate, transactionManager);
    }

    @Test
    void optionalIdempotency_withInvalidHeader_stillValidatesKeyFormat() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/me/availability-slots/1/deactivate");
        request.addHeader("Idempotency-Key", "invalid/key");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        ProceedingJoinPoint joinPoint = mockJoinPoint("optionalMethod");

        BaseException ex = assertThrows(BaseException.class, () -> aspect.checkIdempotency(joinPoint));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    private ProceedingJoinPoint mockJoinPoint(String methodName) throws NoSuchMethodException {
        Method method = SampleController.class.getDeclaredMethod(methodName);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);

        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        return joinPoint;
    }

    static class SampleController {
        @Idempotent(required = false)
        void optionalMethod() {
        }

        @Idempotent
        void requiredMethod() {
        }
    }
}
