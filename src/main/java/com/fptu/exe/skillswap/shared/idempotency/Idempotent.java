package com.fptu.exe.skillswap.shared.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu method có hỗ trợ idempotency.
 * Khi {@code required = true} (mặc định), request gọi method này phải có header "Idempotency-Key".
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    boolean required() default true;
}
