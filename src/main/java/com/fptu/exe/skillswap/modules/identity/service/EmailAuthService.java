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
import com.fptu.exe.skillswap.modules.notification.template.HtmlEmailTemplate;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAuthService {

    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailDispatchPort emailDispatchPort;
    private final IdentityLoginTransactionService identityLoginTransactionService;

    private final SecureRandom secureRandom = new SecureRandom();
    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @Autowired(required = false)
    void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }

    @Transactional
    public EmailRegisterResponse register(EmailRegisterRequest request) {
        String email = normalizeEmail(request.email());
        Optional<User> existingUserOpt = userRepository.findByEmailIncludingDeleted(email);

        EmailVerificationType type;
        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            if (existingUser.getDeletedAt() != null || existingUser.getStatus() == UserStatus.DELETED) {
                throw new BaseException(ErrorCode.USER_NOT_FOUND, "Tài khoản đã bị xóa khỏi hệ thống");
            }
            if (existingUser.getPasswordHash() != null) {
                throw new BaseException(ErrorCode.EMAIL_EXISTED, "Email này đã được đăng ký và thiết lập mật khẩu. Vui lòng đăng nhập.");
            }
            // Tài khoản Google đã có nhưng chưa có mật khẩu -> Liên kết tài khoản
            type = EmailVerificationType.LINK_GOOGLE_ACCOUNT;
        } else {
            type = EmailVerificationType.REGISTER;
        }

        String otpCode = generateOtp();
        String pendingPasswordHash = passwordEncoder.encode(request.password());
        LocalDateTime expiresAt = timeProvider.nowBusiness().plusMinutes(10);

        EmailVerification verification = EmailVerification.builder()
                .email(email)
                .otpCode(otpCode)
                .type(type)
                .fullName(request.fullName().trim())
                .pendingPasswordHash(pendingPasswordHash)
                .attempts(0)
                .expiresAt(expiresAt)
                .build();
        emailVerificationRepository.save(verification);

        boolean isLinked = (type == EmailVerificationType.LINK_GOOGLE_ACCOUNT);
        sendVerificationEmail(email, request.fullName().trim(), otpCode, isLinked);

        String message = isLinked
                ? "Mã xác thực đã được gửi tới email của bạn để liên kết mật khẩu vào tài khoản Google."
                : "Mã xác thực đã được gửi tới email của bạn. Vui lòng kiểm tra hộp thư để kích hoạt tài khoản.";

        return new EmailRegisterResponse(email, isLinked, message);
    }

    @Transactional
    public void resendOtp(EmailResendOtpRequest request) {
        String email = normalizeEmail(request.email());
        EmailVerification verification = emailVerificationRepository
                .findTopByEmailAndVerifiedAtIsNullOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new BaseException(ErrorCode.BAD_REQUEST, "Không tìm thấy yêu cầu xác thực. Vui lòng đăng ký lại."));

        String newOtpCode = generateOtp();
        verification.setOtpCode(newOtpCode);
        verification.setAttempts(0);
        verification.setExpiresAt(timeProvider.nowBusiness().plusMinutes(10));
        emailVerificationRepository.save(verification);

        boolean isLinked = (verification.getType() == EmailVerificationType.LINK_GOOGLE_ACCOUNT);
        String name = verification.getFullName() != null ? verification.getFullName() : "Người dùng";
        sendVerificationEmail(email, name, newOtpCode, isLinked);
    }

    @Transactional
    public TokenResponse verifyEmail(EmailVerifyRequest request) {
        String email = normalizeEmail(request.email());
        EmailVerification verification = emailVerificationRepository
                .findTopByEmailAndVerifiedAtIsNullOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new BaseException(ErrorCode.BAD_REQUEST, "Không tìm thấy yêu cầu xác thực hoặc mã đã được sử dụng."));

        if (verification.getExpiresAt().isBefore(timeProvider.nowBusiness())) {
            throw new BaseException(ErrorCode.OTP_EXPIRED, "Mã xác thực đã hết hạn. Vui lòng yêu cầu mã mới.");
        }

        if (verification.getAttempts() >= 5) {
            throw new BaseException(ErrorCode.OTP_MAX_ATTEMPTS_EXCEEDED, "Bạn đã nhập sai mã xác thực quá 5 lần. Vui lòng yêu cầu mã mới.");
        }

        if (!verification.getOtpCode().equals(request.otpCode().trim())) {
            verification.setAttempts(verification.getAttempts() + 1);
            emailVerificationRepository.save(verification);
            throw new BaseException(ErrorCode.OTP_INVALID, "Mã xác thực không chính xác.");
        }

        verification.setVerifiedAt(timeProvider.nowBusiness());
        emailVerificationRepository.save(verification);

        User authenticatedUser;
        if (verification.getType() == EmailVerificationType.LINK_GOOGLE_ACCOUNT) {
            authenticatedUser = userRepository.findByEmail(email)
                    .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy tài khoản người dùng"));
            authenticatedUser.setPasswordHash(verification.getPendingPasswordHash());
            if (authenticatedUser.getStatus() == UserStatus.INACTIVE) {
                authenticatedUser.setStatus(UserStatus.ACTIVE);
            }
            authenticatedUser.setLastLoginAt(timeProvider.nowBusiness());
            authenticatedUser = userRepository.save(authenticatedUser);
        } else {
            // New user registration
            userRepository.findByEmailIncludingDeleted(email).ifPresent(u -> {
                throw new BaseException(ErrorCode.EMAIL_EXISTED, "Email này đã được kích hoạt.");
            });

            User newUser = User.builder()
                    .email(email)
                    .fullName(verification.getFullName() != null ? verification.getFullName() : extractDefaultName(email))
                    .passwordHash(verification.getPendingPasswordHash())
                    .status(UserStatus.ACTIVE)
                    .build();
            authenticatedUser = userRepository.save(newUser);
            authenticatedUser.getRoles().add(RoleCode.MENTEE);
            authenticatedUser.setLastLoginAt(timeProvider.nowBusiness());
            authenticatedUser = userRepository.save(authenticatedUser);
        }

        return identityLoginTransactionService.generateTokensAndCreateSession(authenticatedUser);
    }

    @Transactional
    public TokenResponse login(EmailLoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailIncludingDeleted(email)
                .orElseThrow(() -> new BaseException(ErrorCode.INVALID_CREDENTIALS, "Email hoặc mật khẩu không chính xác"));

        if (user.getDeletedAt() != null || user.getStatus() == UserStatus.DELETED) {
            throw new BaseException(ErrorCode.USER_NOT_FOUND, "Tài khoản đã bị xóa khỏi hệ thống");
        }

        if (user.getPasswordHash() == null) {
            throw new BaseException(ErrorCode.ACCOUNT_PASSWORD_NOT_SET, "Tài khoản này được đăng ký bằng Google. Vui lòng đăng nhập bằng Google hoặc đăng ký để thiết lập mật khẩu.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BaseException(ErrorCode.INVALID_CREDENTIALS, "Email hoặc mật khẩu không chính xác");
        }

        if (user.getStatus() == UserStatus.BANNED) {
            throw new BaseException(ErrorCode.USER_BANNED, "Tài khoản của bạn đã bị khóa");
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new BaseException(ErrorCode.USER_INACTIVE, "Tài khoản của bạn chưa kích hoạt");
        }

        user.setLastLoginAt(timeProvider.nowBusiness());
        userRepository.save(user);

        return identityLoginTransactionService.generateTokensAndCreateSession(user);
    }

    private String generateOtp() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String extractDefaultName(String email) {
        if (email == null || !email.contains("@")) {
            return "Người dùng";
        }
        return email.split("@")[0];
    }

    private void sendVerificationEmail(String email, String recipientName, String otpCode, boolean isAccountLinking) {
        String subject = isAccountLinking
                ? "[SkillSwap] Mã xác thực email tài khoản SkillSwap"
                : "[SkillSwap] Mã xác thực kích hoạt tài khoản";
        String title = isAccountLinking
                ? "Mã xác thực email tài khoản SkillSwap"
                : "Kích hoạt tài khoản SkillSwap";
        String intro = isAccountLinking
                ? "Chúng tôi nhận được yêu cầu thiết lập mật khẩu cho tài khoản Google của bạn trên hệ thống SkillSwap."
                : "Cảm ơn bạn đã đăng ký tham gia mạng lưới trao đổi kỹ năng học tập SkillSwap!";

        String detailRows = """
                <tr>
                  <td style="padding:16px 20px;font-size:14px;color:#5d7083;width:40%%;font-family:'Segoe UI',Arial,sans-serif;">Mã xác thực (OTP)</td>
                  <td style="padding:16px 20px;font-size:24px;font-weight:700;color:#0b3a67;letter-spacing:4px;font-family:'Segoe UI',Arial,sans-serif;">%s</td>
                </tr>
                <tr>
                  <td style="padding:16px 20px;font-size:14px;color:#5d7083;width:40%%;font-family:'Segoe UI',Arial,sans-serif;">Thời hạn hiệu lực</td>
                  <td style="padding:16px 20px;font-size:14px;font-weight:600;color:#102a43;font-family:'Segoe UI',Arial,sans-serif;">10 phút</td>
                </tr>
                """.formatted(otpCode);

        HtmlEmailTemplate.Model model = new HtmlEmailTemplate.Model(
                subject,
                "Mã OTP xác thực: " + otpCode,
                "Xác thực",
                title,
                isAccountLinking ? "Thiết lập mật khẩu cho tài khoản Google" : "Mã xác thực tạo tài khoản mới",
                "Mã xác thực",
                otpCode,
                recipientName,
                intro,
                detailRows,
                "Nhập mã OTP 6 chữ số này trên ứng dụng để hoàn tất xác thực.",
                "Mở SkillSwap",
                HtmlEmailTemplate.PLATFORM_URL
        );

        String html = HtmlEmailTemplate.render(model);
        String plainText = "Xin chào " + recipientName + ",\nMã xác thực OTP của bạn là: " + otpCode + ". Mã có hiệu lực trong 10 phút.";
        String dedupeKey = "auth:otp:" + email + ":" + UUID.randomUUID();

        log.info("[EmailAuth] Đã sinh mã OTP cho email {}: {} (hiệu lực 10 phút, isLinked={})", email, otpCode, isAccountLinking);

        try {
            boolean queued = emailDispatchPort.sendHtmlOnce(dedupeKey, email, subject, html, plainText, "EMAIL_VERIFICATION_OTP");
            if (!queued) {
                log.warn("[EmailAuth] Không thể đưa email OTP vào outbox cho {}", email);
            }
        } catch (Exception ex) {
            log.error("[EmailAuth] Lỗi khi gửi email OTP tới {}: {}", email, ex.getMessage(), ex);
        }
    }
}
