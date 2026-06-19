package com.fptu.exe.skillswap.modules.mentor.dto.request;

import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MentorProfileUpsertRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        if (validatorFactory != null) {
            validatorFactory.close();
        }
    }

    @Test
    void validPhoneNumber_shouldPassValidation() {
        Set<ConstraintViolation<MentorProfileUpsertRequest>> violations = validator.validate(validRequest("0912345678"));
        assertTrue(violations.isEmpty());
    }

    @Test
    void blankPhoneNumber_shouldFailValidation() {
        Set<ConstraintViolation<MentorProfileUpsertRequest>> violations = validator.validate(validRequest(""));
        assertTrue(hasPhoneViolation(violations));
    }

    @Test
    void phoneNumberWithLetters_shouldFailValidation() {
        Set<ConstraintViolation<MentorProfileUpsertRequest>> violations = validator.validate(validRequest("09123abcde"));
        assertTrue(hasPhoneViolation(violations));
    }

    @Test
    void phoneNumberWithInvalidPrefix_shouldFailValidation() {
        Set<ConstraintViolation<MentorProfileUpsertRequest>> violations = validator.validate(validRequest("0212345678"));
        assertTrue(hasPhoneViolation(violations));
    }

    private MentorProfileUpsertRequest validRequest(String phoneNumber) {
        return new MentorProfileUpsertRequest(
                "Backend Developer | Spring Boot Mentor",
                "Mình có kinh nghiệm xây dựng REST API với Spring Boot.",
                "Java, REST API",
                true,
                List.of(UUID.randomUUID()),
                TeachingMode.ONLINE,
                60,
                "https://linkedin.com/in/example",
                "https://github.com/example",
                "https://example.dev",
                phoneNumber
        );
    }

    private boolean hasPhoneViolation(Set<ConstraintViolation<MentorProfileUpsertRequest>> violations) {
        return violations.stream().anyMatch(violation -> "phoneNumber".equals(violation.getPropertyPath().toString()));
    }
}
