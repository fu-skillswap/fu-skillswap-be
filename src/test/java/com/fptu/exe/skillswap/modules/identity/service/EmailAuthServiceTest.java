package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.identity.domain.EmailVerification;
import com.fptu.exe.skillswap.modules.identity.domain.EmailVerificationType;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.dto.request.EmailLoginRequest;
import com.fptu.exe.skillswap.modules.identity.dto.request.EmailRegisterRequest;
import com.fptu.exe.skillswap.modules.identity.dto.request.EmailResendOtpRequest;
import com.fptu.exe.skillswap.modules.identity.dto.request.EmailVerifyRequest;
import com.fptu.exe.skillswap.modules.identity.dto.response.EmailRegisterResponse;
import com.fptu.exe.skillswap.modules.identity.dto.response.TokenResponse;
import com.fptu.exe.skillswap.modules.identity.repository.EmailVerificationRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.notification.port.EmailDispatchPort;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EmailAuthServiceTest {

    private UserRepository userRepository;
    private EmailVerificationRepository emailVerificationRepository;
    private PasswordEncoder passwordEncoder;
    private EmailDispatchPort emailDispatchPort;
    private IdentityLoginTransactionService identityLoginTransactionService;
    private TimeProvider timeProvider;

    private EmailAuthService emailAuthService;

    private final LocalDateTime fixedNow = LocalDateTime.of(2026, 10, 29, 23, 0, 0);

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        emailVerificationRepository = mock(EmailVerificationRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        emailDispatchPort = mock(EmailDispatchPort.class);
        identityLoginTransactionService = mock(IdentityLoginTransactionService.class);
        timeProvider = mock(TimeProvider.class);

        when(timeProvider.nowBusiness()).thenReturn(fixedNow);

        emailAuthService = new EmailAuthService(
                userRepository,
                emailVerificationRepository,
                passwordEncoder,
                emailDispatchPort,
                identityLoginTransactionService
        );
        emailAuthService.setTimeProvider(timeProvider);
    }

    // --- REGISTER TESTS ---

    @Test
    void register_newUser_shouldCreatePendingVerificationAndSendEmail() {
        String email = "student@fpt.edu.vn";
        EmailRegisterRequest request = new EmailRegisterRequest(email, "Password@123", "Nguyen Van A");

        when(userRepository.findByEmailIncludingDeleted(email)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Password@123")).thenReturn("hashedPassword123");

        EmailRegisterResponse response = emailAuthService.register(request);

        assertThat(response.email()).isEqualTo(email);
        assertThat(response.isLinked()).isFalse();
        assertThat(response.message()).contains("kích hoạt tài khoản");

        ArgumentCaptor<EmailVerification> captor = ArgumentCaptor.forClass(EmailVerification.class);
        verify(emailVerificationRepository).save(captor.capture());
        EmailVerification saved = captor.getValue();

        assertThat(saved.getEmail()).isEqualTo(email);
        assertThat(saved.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(saved.getPendingPasswordHash()).isEqualTo("hashedPassword123");
        assertThat(saved.getType()).isEqualTo(EmailVerificationType.REGISTER);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getExpiresAt()).isEqualTo(fixedNow.plusMinutes(10));
        assertThat(saved.getOtpCode()).matches("^\\d{6}$");

        verify(emailDispatchPort).sendHtmlOnce(any(), eq(email), any(), any(), any(), eq("EMAIL_VERIFICATION_OTP"));
    }

    @Test
    void register_existingGoogleUserWithoutPassword_shouldSetTypeLinkAccount() {
        String email = "googleuser@fpt.edu.vn";
        EmailRegisterRequest request = new EmailRegisterRequest(email, "Password@123", "Google User");

        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .fullName("Google User")
                .passwordHash(null)
                .status(UserStatus.ACTIVE)
                .roles(new HashSet<>())
                .build();

        when(userRepository.findByEmailIncludingDeleted(email)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.encode("Password@123")).thenReturn("hashedPassword123");

        EmailRegisterResponse response = emailAuthService.register(request);

        assertThat(response.email()).isEqualTo(email);
        assertThat(response.isLinked()).isTrue();
        assertThat(response.message()).contains("liên kết mật khẩu");

        ArgumentCaptor<EmailVerification> captor = ArgumentCaptor.forClass(EmailVerification.class);
        verify(emailVerificationRepository).save(captor.capture());
        EmailVerification saved = captor.getValue();

        assertThat(saved.getType()).isEqualTo(EmailVerificationType.LINK_GOOGLE_ACCOUNT);
        verify(emailDispatchPort).sendHtmlOnce(any(), eq(email), any(), any(), any(), eq("EMAIL_VERIFICATION_OTP"));
    }

    @Test
    void register_existingUserWithPassword_shouldThrowConflict() {
        String email = "existing@fpt.edu.vn";
        EmailRegisterRequest request = new EmailRegisterRequest(email, "Password@123", "Existing User");

        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash("already_has_hash")
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findByEmailIncludingDeleted(email)).thenReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> emailAuthService.register(request))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_EXISTED);

        verify(emailVerificationRepository, never()).save(any());
        verify(emailDispatchPort, never()).sendHtmlOnce(any(), any(), any(), any(), any(), any());
    }

    @Test
    void register_deletedUser_shouldThrowUserNotFound() {
        String email = "deleted@fpt.edu.vn";
        EmailRegisterRequest request = new EmailRegisterRequest(email, "Password@123", "Deleted User");

        User deletedUser = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .deletedAt(LocalDateTime.now())
                .build();

        when(userRepository.findByEmailIncludingDeleted(email)).thenReturn(Optional.of(deletedUser));

        assertThatThrownBy(() -> emailAuthService.register(request))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // --- RESEND OTP TESTS ---

    @Test
    void resendOtp_validPendingVerification_shouldGenerateNewOtpAndResend() {
        String email = "resend@fpt.edu.vn";
        EmailResendOtpRequest request = new EmailResendOtpRequest(email);

        EmailVerification pending = EmailVerification.builder()
                .email(email)
                .fullName("User Resend")
                .otpCode("111111")
                .attempts(2)
                .expiresAt(fixedNow.plusMinutes(5))
                .type(EmailVerificationType.REGISTER)
                .build();

        when(emailVerificationRepository.findTopByEmailAndVerifiedAtIsNullOrderByCreatedAtDesc(email))
                .thenReturn(Optional.of(pending));

        emailAuthService.resendOtp(request);

        assertThat(pending.getAttempts()).isZero();
        assertThat(pending.getExpiresAt()).isEqualTo(fixedNow.plusMinutes(10));
        assertThat(pending.getOtpCode()).matches("^\\d{6}$");

        verify(emailVerificationRepository).save(pending);
        verify(emailDispatchPort).sendHtmlOnce(any(), eq(email), any(), any(), any(), eq("EMAIL_VERIFICATION_OTP"));
    }

    @Test
    void resendOtp_noPendingVerification_shouldThrowBadRequest() {
        String email = "notfound@fpt.edu.vn";
        when(emailVerificationRepository.findTopByEmailAndVerifiedAtIsNullOrderByCreatedAtDesc(email))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> emailAuthService.resendOtp(new EmailResendOtpRequest(email)))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    // --- VERIFY EMAIL TESTS ---

    @Test
    void verifyEmail_validOtp_newUser_shouldCreateActiveMenteeUserAndIssueTokens() {
        String email = "newuser@fpt.edu.vn";
        EmailVerifyRequest request = new EmailVerifyRequest(email, "654321");

        EmailVerification pending = EmailVerification.builder()
                .email(email)
                .fullName("New Mentee")
                .otpCode("654321")
                .attempts(1)
                .expiresAt(fixedNow.plusMinutes(5))
                .pendingPasswordHash("encoded_secret")
                .type(EmailVerificationType.REGISTER)
                .build();

        when(emailVerificationRepository.findTopByEmailAndVerifiedAtIsNullOrderByCreatedAtDesc(email))
                .thenReturn(Optional.of(pending));
        when(userRepository.findByEmailIncludingDeleted(email)).thenReturn(Optional.empty());

        User savedUser = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .fullName("New Mentee")
                .passwordHash("encoded_secret")
                .status(UserStatus.ACTIVE)
                .roles(new HashSet<>())
                .build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken("access-token-123")
                .refreshToken("refresh-token-123")
                .build();
        when(identityLoginTransactionService.generateTokensAndCreateSession(any(User.class)))
                .thenReturn(tokenResponse);

        TokenResponse result = emailAuthService.verifyEmail(request);

        assertThat(result.getAccessToken()).isEqualTo("access-token-123");
        assertThat(pending.getVerifiedAt()).isEqualTo(fixedNow);
        verify(emailVerificationRepository).save(pending);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture());
        User createdUser = userCaptor.getAllValues().get(0);
        assertThat(createdUser.getEmail()).isEqualTo(email);
        assertThat(createdUser.getPasswordHash()).isEqualTo("encoded_secret");
        assertThat(createdUser.getStatus()).isEqualTo(UserStatus.ACTIVE);

        verify(identityLoginTransactionService).generateTokensAndCreateSession(any(User.class));
    }

    @Test
    void verifyEmail_validOtp_googleUser_shouldUpdatePasswordHash() {
        String email = "link@fpt.edu.vn";
        EmailVerifyRequest request = new EmailVerifyRequest(email, "123456");

        EmailVerification pending = EmailVerification.builder()
                .email(email)
                .otpCode("123456")
                .expiresAt(fixedNow.plusMinutes(5))
                .pendingPasswordHash("new_password_hash")
                .type(EmailVerificationType.LINK_GOOGLE_ACCOUNT)
                .build();

        User googleUser = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .status(UserStatus.ACTIVE)
                .roles(new HashSet<>())
                .build();

        when(emailVerificationRepository.findTopByEmailAndVerifiedAtIsNullOrderByCreatedAtDesc(email))
                .thenReturn(Optional.of(pending));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(googleUser));
        when(userRepository.save(any(User.class))).thenReturn(googleUser);

        TokenResponse tokenResponse = TokenResponse.builder().accessToken("token-linked").build();
        when(identityLoginTransactionService.generateTokensAndCreateSession(googleUser)).thenReturn(tokenResponse);

        TokenResponse result = emailAuthService.verifyEmail(request);

        assertThat(result.getAccessToken()).isEqualTo("token-linked");
        assertThat(googleUser.getPasswordHash()).isEqualTo("new_password_hash");
        verify(userRepository).save(googleUser);
    }

    @Test
    void verifyEmail_invalidOtp_shouldIncrementAttempts() {
        String email = "wrong@fpt.edu.vn";
        EmailVerifyRequest request = new EmailVerifyRequest(email, "999999");

        EmailVerification pending = EmailVerification.builder()
                .email(email)
                .otpCode("123456")
                .attempts(2)
                .expiresAt(fixedNow.plusMinutes(5))
                .type(EmailVerificationType.REGISTER)
                .build();

        when(emailVerificationRepository.findTopByEmailAndVerifiedAtIsNullOrderByCreatedAtDesc(email))
                .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> emailAuthService.verifyEmail(request))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.OTP_INVALID);

        assertThat(pending.getAttempts()).isEqualTo(3);
        verify(emailVerificationRepository).save(pending);
    }

    @Test
    void verifyEmail_maxAttemptsReached_shouldThrowException() {
        String email = "max@fpt.edu.vn";
        EmailVerifyRequest request = new EmailVerifyRequest(email, "123456");

        EmailVerification pending = EmailVerification.builder()
                .email(email)
                .otpCode("123456")
                .attempts(5)
                .expiresAt(fixedNow.plusMinutes(5))
                .type(EmailVerificationType.REGISTER)
                .build();

        when(emailVerificationRepository.findTopByEmailAndVerifiedAtIsNullOrderByCreatedAtDesc(email))
                .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> emailAuthService.verifyEmail(request))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED);
    }

    @Test
    void verifyEmail_expiredOtp_shouldThrowException() {
        String email = "expired@fpt.edu.vn";
        EmailVerifyRequest request = new EmailVerifyRequest(email, "123456");

        EmailVerification pending = EmailVerification.builder()
                .email(email)
                .otpCode("123456")
                .expiresAt(fixedNow.minusMinutes(1)) // Expired
                .type(EmailVerificationType.REGISTER)
                .build();

        when(emailVerificationRepository.findTopByEmailAndVerifiedAtIsNullOrderByCreatedAtDesc(email))
                .thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> emailAuthService.verifyEmail(request))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.OTP_EXPIRED);
    }

    // --- LOGIN TESTS ---

    @Test
    void login_validCredentials_shouldIssueTokens() {
        String email = "login@fpt.edu.vn";
        EmailLoginRequest request = new EmailLoginRequest(email, "Secret@123");

        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash("hashed_secret")
                .status(UserStatus.ACTIVE)
                .roles(new HashSet<>())
                .build();

        when(userRepository.findByEmailIncludingDeleted(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret@123", "hashed_secret")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenReturn(user);

        TokenResponse tokenResponse = TokenResponse.builder()
                .accessToken("login-access-token")
                .refreshToken("login-refresh-token")
                .build();
        when(identityLoginTransactionService.generateTokensAndCreateSession(user)).thenReturn(tokenResponse);

        TokenResponse result = emailAuthService.login(request);

        assertThat(result.getAccessToken()).isEqualTo("login-access-token");
        assertThat(user.getLastLoginAt()).isEqualTo(fixedNow);
        verify(userRepository).save(user);
    }

    @Test
    void login_wrongPassword_shouldThrowInvalidCredentials() {
        String email = "login@fpt.edu.vn";
        EmailLoginRequest request = new EmailLoginRequest(email, "WrongSecret");

        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash("hashed_secret")
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findByEmailIncludingDeleted(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongSecret", "hashed_secret")).thenReturn(false);

        assertThatThrownBy(() -> emailAuthService.login(request))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void login_googleAccountWithoutPassword_shouldThrowFriendlyError() {
        String email = "googleonly@fpt.edu.vn";
        EmailLoginRequest request = new EmailLoginRequest(email, "SomeSecret");

        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash(null)
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findByEmailIncludingDeleted(email)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> emailAuthService.login(request))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_PASSWORD_NOT_SET);
    }

    @Test
    void login_bannedUser_shouldThrowUserBanned() {
        String email = "banned@fpt.edu.vn";
        EmailLoginRequest request = new EmailLoginRequest(email, "Secret@123");

        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash("hashed_secret")
                .status(UserStatus.BANNED)
                .build();

        when(userRepository.findByEmailIncludingDeleted(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret@123", "hashed_secret")).thenReturn(true);

        assertThatThrownBy(() -> emailAuthService.login(request))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_BANNED);
    }

    @Test
    void login_inactiveUser_shouldThrowUserInactive() {
        String email = "inactive@fpt.edu.vn";
        EmailLoginRequest request = new EmailLoginRequest(email, "Secret@123");

        User user = User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash("hashed_secret")
                .status(UserStatus.INACTIVE)
                .build();

        when(userRepository.findByEmailIncludingDeleted(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret@123", "hashed_secret")).thenReturn(true);

        assertThatThrownBy(() -> emailAuthService.login(request))
                .isInstanceOf(BaseException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_INACTIVE);
    }
}
