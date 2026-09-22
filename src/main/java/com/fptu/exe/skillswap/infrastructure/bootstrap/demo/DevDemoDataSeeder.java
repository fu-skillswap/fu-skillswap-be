package com.fptu.exe.skillswap.infrastructure.bootstrap.demo;

import com.fptu.exe.skillswap.modules.catalog.domain.AdministrativeProvince;
import com.fptu.exe.skillswap.modules.catalog.domain.EducationFieldGroup;
import com.fptu.exe.skillswap.modules.catalog.domain.EducationalInstitution;
import com.fptu.exe.skillswap.modules.catalog.repository.AdministrativeProvinceRepository;
import com.fptu.exe.skillswap.modules.catalog.repository.EducationFieldGroupRepository;
import com.fptu.exe.skillswap.modules.catalog.repository.EducationalInstitutionRepository;
import com.fptu.exe.skillswap.modules.identity.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.identity.domain.StudentProfileType;
import com.fptu.exe.skillswap.modules.identity.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRepeatType;
import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityRuleType;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilityRule;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilityRuleRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.filestorage.domain.FilePurpose;
import com.fptu.exe.skillswap.modules.filestorage.domain.StoredFile;
import com.fptu.exe.skillswap.modules.filestorage.repository.StoredFileRepository;
import com.fptu.exe.skillswap.modules.identity.domain.OauthAccount;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.OauthAccountRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorService;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationDocument;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationEventType;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequest;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequestEvent;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentType;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStorageKind;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorServiceRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationDocumentRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestEventRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
@Profile("demo")
@RequiredArgsConstructor
@Slf4j
@Order(2)
public class DevDemoDataSeeder implements CommandLineRunner {

    private static final int MIN_SERVICE_PRICE_SCOIN_PER_MINUTE = 1_200;
    private static final String GOOGLE_PROVIDER = "GOOGLE";
    private static final int TOTAL_MENTOR_COUNT = 100;
    private static final String DEFAULT_TIMEZONE = "Asia/Ho_Chi_Minh";
    private static final Set<VerificationStatus> OPEN_REQUEST_STATUSES = EnumSet.of(
            VerificationStatus.DRAFT,
            VerificationStatus.PENDING_REVIEW,
            VerificationStatus.NEEDS_REVISION
    );
    private static final List<String> VIETNAMESE_LAST_NAMES = List.of(
            "Nguyen", "Tran", "Le", "Pham", "Hoang", "Huynh", "Phan", "Vu", "Vo", "Dang",
            "Bui", "Do", "Ho", "Ngo", "Duong", "Ly", "Mai", "Dinh", "Truong", "Cao"
    );
    private static final List<String> VIETNAMESE_MIDDLE_NAMES = List.of(
            "Minh", "Gia", "Thanh", "Ngoc", "Quoc", "Bao", "Anh", "Duc", "Thu", "Tien",
            "Khanh", "Hoai", "Nhat", "Phuong", "Tu", "Xuan", "Yen", "Hai", "Lan", "My"
    );
    private static final List<String> VIETNAMESE_GIVEN_NAMES = List.of(
            "Khang", "Linh", "Huy", "Vy", "Quan", "Trang", "Phong", "Nhi", "Thinh", "Ha",
            "Dat", "Chau", "An", "Ngan", "Tam", "Hanh", "Kiet", "Quyen", "Son", "Truc",
            "Phuc", "Thao", "Long", "Nhu", "Lam", "Uyen", "Tai", "Yen", "Duy", "Quynh"
    );

    private final UserRepository userRepository;
    private final OauthAccountRepository oauthAccountRepository;
    private final AdministrativeProvinceRepository provinceRepository;
    private final EducationalInstitutionRepository institutionRepository;
    private final EducationFieldGroupRepository fieldGroupRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final MentorVerificationRequestRepository mentorVerificationRequestRepository;
    private final MentorVerificationRequestEventRepository mentorVerificationRequestEventRepository;
    private final MentorVerificationDocumentRepository mentorVerificationDocumentRepository;
    private final StoredFileRepository storedFileRepository;
    private final MentorServiceRepository mentorServiceRepository;
    private final MentorAvailabilityRuleRepository mentorAvailabilityRuleRepository;
    private final MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;

    private boolean seederEnabled = false;

    public void setSeederEnabled(boolean seederEnabled) {
        this.seederEnabled = seederEnabled;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!seederEnabled) {
            log.info("SkillSwap demo data seeding is disabled to keep DB clean.");
            return;
        }

        log.info("Starting SkillSwap demo data seeding...");

        purgeMenteeSeeds();
        seedMentors();

        log.info("SkillSwap demo data seeding completed successfully!");
    }

    private List<String> menteeSeedEmails() {
        return List.of("mentee01.demo@skillswap.local", "mentee02.demo@skillswap.local", "mentee03.demo@skillswap.local",
                "mentee04.demo@skillswap.local", "mentee05.demo@skillswap.local", "mentee06.demo@skillswap.local",
                "mentee07.demo@skillswap.local", "mentee08.demo@skillswap.local", "mentee09.demo@skillswap.local",
                "mentee10.demo@skillswap.local");
    }

    private void purgeMenteeSeeds() {
        for (String email : menteeSeedEmails()) {
            userRepository.findByEmailIncludingDeleted(email).ifPresent(user -> {
                studentProfileRepository.findById(user.getId()).ifPresent(studentProfileRepository::delete);
                userRepository.delete(user);
            });
            oauthAccountRepository.findByProviderAndProviderUserId(GOOGLE_PROVIDER, demoProviderUserId(email))
                    .ifPresent(oauthAccountRepository::delete);
        }
    }

    private void seedMentors() {
        for (MentorSeed seed : mentorSeeds()) {
            User user = upsertUser(seed.email(), seed.fullName(), seed.avatarUrl(), Set.of(RoleCode.MENTEE, RoleCode.MENTOR));
            upsertOauthAccount(user, demoProviderUserId(seed.email()));
            upsertStudentProfile(user, seed.expertiseDescription());

            MentorProfile mentorProfile = upsertMentorProfile(user, seed);
            upsertMentorService(mentorProfile, seed);
            upsertAvailabilityPlan(mentorProfile);
        }
    }

    private User upsertUser(String email, String fullName, String avatarUrl, Set<RoleCode> roles) {
        User user = userRepository.findByEmailIncludingDeleted(email)
                .orElseGet(User::new);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setAvatarUrl(avatarUrl);
        user.setStatus(UserStatus.ACTIVE);
        user.setLastLoginAt(DateTimeUtil.now().minusDays(1));
        user.setDeletedAt(null);
        user.setRoles(new HashSet<>(roles));
        return userRepository.save(user);
    }

    private void upsertOauthAccount(User user, String providerUserId) {
        OauthAccount oauthAccount = oauthAccountRepository.findByProviderAndProviderUserId(GOOGLE_PROVIDER, providerUserId)
                .orElseGet(OauthAccount::new);
        oauthAccount.setUser(user);
        oauthAccount.setProvider(GOOGLE_PROVIDER);
        oauthAccount.setProviderUserId(providerUserId);
        oauthAccount.setProviderEmail(user.getEmail());
        oauthAccountRepository.save(oauthAccount);
    }

    private StudentProfile upsertStudentProfile(User user, String bio) {
        StudentProfile profile = studentProfileRepository.findWithDetailsByUserId(user.getId())
                .orElseGet(StudentProfile::new);
        profile.setUser(user);
        int profileVariant = Math.floorMod(user.getId().hashCode(), 3);
        AdministrativeProvince province = provinceRepository.searchActive("").stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Canonical province seed is missing"));
        if (profileVariant == 0) {
            profile.setProfileType(StudentProfileType.SCHOOL_STUDENT);
            profile.setCustomInstitutionName("Demo Secondary School");
            profile.setCustomInstitutionProvinceId(province.getId());
            profile.setInstitutionId(null);
            profile.setFieldGroupId(null);
            profile.setMajorName(null);
            profile.setGraduationYear(null);
        } else {
            EducationalInstitution institution = institutionRepository.findAll().stream()
                    .filter(EducationalInstitution::isActive).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Canonical institution seed is missing"));
            EducationFieldGroup fieldGroup = fieldGroupRepository.findAll().stream()
                    .filter(EducationFieldGroup::isActive).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Canonical field group seed is missing"));
            profile.setProfileType(profileVariant == 1 ? StudentProfileType.UNIVERSITY_STUDENT : StudentProfileType.ALUMNI);
            profile.setInstitutionId(institution.getId());
            profile.setCustomInstitutionName(null);
            profile.setCustomInstitutionProvinceId(null);
            profile.setFieldGroupId(fieldGroup.getId());
            profile.setMajorName("Demo major");
            profile.setGraduationYear(profileVariant == 2 ? 2024 : null);
        }
        profile.setEnrollmentYear(2022);
        profile.setOnboardingCompleted(true);
        profile.setOnboardingCompletedAt(DateTimeUtil.now());
        profile.setBio(bio);
        return studentProfileRepository.save(profile);
    }

    private MentorProfile upsertMentorProfile(User user, MentorSeed seed) {
        MentorProfile mentorProfile = mentorProfileRepository.findWithUserByUserId(user.getId())
                .orElseGet(MentorProfile::new);
        mentorProfile.setUserId(user.getId());
        mentorProfile.setStatus(seed.activeMentor() ? MentorStatus.ACTIVE : MentorStatus.PENDING_VERIFICATION);
        mentorProfile.setHeadline(seed.headline());
        mentorProfile.setExpertiseDescription(seed.expertiseDescription());
        mentorProfile.setSupportingSubjects(seed.supportingSubjects());
        mentorProfile.setTeachingMode(seed.teachingMode());
        mentorProfile.setSessionDuration(seed.sessionDuration());
        mentorProfile.setPortfolioUrl(null);
        mentorProfile.setLinkedinUrl(null);
        mentorProfile.setGithubUrl(null);
        mentorProfile.setAverageRating(seed.averageRating());
        mentorProfile.setTotalReviews(seed.activeMentor() ? 18 : 0);
        mentorProfile.setTotalSessions(seed.activeMentor() ? 20 : 0);
        mentorProfile.setTotalCompletedSessions(seed.activeMentor() ? 18 : 0);
        mentorProfile.setTotalRejectedBookings(seed.activeMentor() ? 1 : 0);
        mentorProfile.setAvailable(seed.activeMentor());
        mentorProfile.setBookingSuspendedUntil(null);
        mentorProfile.setVerifiedAt(seed.activeMentor() ? DateTimeUtil.now().minusDays(seed.verifiedDaysAgo()) : null);
        mentorProfile.setVerifiedByUserId(null);
        return mentorProfileRepository.save(mentorProfile);
    }

    private void upsertMentorService(MentorProfile mentorProfile, MentorSeed seed) {
        List<MentorService> existing = mentorServiceRepository.findByMentorProfileUserIdOrderByCreatedAtAsc(mentorProfile.getUserId());
        if (!existing.isEmpty()) {
            MentorService service = existing.get(0);
            if (existing.size() > 1) {
                mentorServiceRepository.deleteAll(existing.subList(1, existing.size()));
            }
            service.setMentorProfile(mentorProfile);
            service.setTitle(seed.serviceTitle());
            service.setDescription(seed.serviceDescription());
            service.setExpectedOutcome("Sau buổi mentoring, mentee có checklist hành động rõ ràng để tự cải thiện.");
            service.setDurationMinutes(seed.serviceDuration());
            service.setFree(seed.serviceFree());
            service.setPriceScoin(normalizedServicePrice(seed.serviceFree(), seed.serviceDuration(), seed.priceScoin()));
            service.setActive(true);
            mentorServiceRepository.save(service);
            return;
        }

        MentorService service = MentorService.builder()
                .mentorProfile(mentorProfile)
                .title(seed.serviceTitle())
                .description(seed.serviceDescription())
                .expectedOutcome("Sau buổi mentoring, mentee có checklist hành động rõ ràng để tự cải thiện.")
                .durationMinutes(seed.serviceDuration())
                .isFree(seed.serviceFree())
                .priceScoin(normalizedServicePrice(seed.serviceFree(), seed.serviceDuration(), seed.priceScoin()))
                .isActive(true)
                .build();
        mentorServiceRepository.save(service);
    }

    private void upsertAvailabilityPlan(MentorProfile mentorProfile) {
        UUID mentorUserId = mentorProfile.getUserId();
        List<MentorAvailabilityRule> rules = mentorAvailabilityRuleRepository.findByMentorUserIdAndActiveTrueOrderByEffectiveFromAscStartTimeAsc(mentorUserId);
        MentorAvailabilityRule activeRule;
        if (rules.isEmpty()) {
            activeRule = MentorAvailabilityRule.builder()
                    .mentorUserId(mentorUserId)
                    .ruleType(AvailabilityRuleType.OPEN)
                    .repeatType(AvailabilityRepeatType.WEEKLY)
                    .daysOfWeek("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY")
                    .effectiveFrom(DateTimeUtil.now().toLocalDate())
                    .effectiveTo(DateTimeUtil.now().toLocalDate().plusMonths(3))
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(17, 0))
                    .timezone(DEFAULT_TIMEZONE)
                    .active(true)
                    .note("Demo availability plan")
                    .build();
            activeRule = mentorAvailabilityRuleRepository.save(activeRule);
        } else {
            activeRule = rules.get(0);
        }

        List<SlotSeed> slots = List.of(
                new SlotSeed(1, 9, 0, mentorProfile.getSessionDuration()),
                new SlotSeed(1, 14, 0, mentorProfile.getSessionDuration()),
                new SlotSeed(2, 9, 0, mentorProfile.getSessionDuration())
        );
        for (SlotSeed slotSeed : slots) {
            LocalDateTime start = DateTimeUtil.now().toLocalDate().plusDays(slotSeed.dayOffset()).atTime(slotSeed.hour(), slotSeed.minute());
            LocalDateTime end = start.plusMinutes(slotSeed.durationMinutes());
            boolean exists = mentorAvailabilitySlotRepository.existsByMentorUserIdAndStartTimeUtcAndEndTimeUtcAndIsActiveTrue(
                    mentorUserId,
                    start.atZone(ZoneId.of(DEFAULT_TIMEZONE)).toInstant(),
                    end.atZone(ZoneId.of(DEFAULT_TIMEZONE)).toInstant()
            );
            if (exists) {
                continue;
            }

            MentorAvailabilitySlot slot = MentorAvailabilitySlot.builder()
                    .mentorUserId(mentorUserId)
                    .rule(activeRule)
                    .startTime(start)
                    .endTime(end)
                    .timezone(DEFAULT_TIMEZONE)
                    .isBooked(false)
                    .isActive(true)
                    .build();
            mentorAvailabilitySlotRepository.save(slot);
        }
    }

    private void upsertPendingVerificationRequest(User user, MentorProfile mentorProfile, MentorSeed seed) {
        MentorVerificationRequest request = mentorVerificationRequestRepository
                .findFirstByMentorUserIdAndStatusInOrderByCreatedAtDesc(user.getId(), OPEN_REQUEST_STATUSES)
                .orElseGet(MentorVerificationRequest::new);

        request.setMentorUserId(user.getId());
        request.setMethod(com.fptu.exe.skillswap.modules.mentor.domain.VerificationMethod.MANUAL);
        request.setStatus(VerificationStatus.PENDING_REVIEW);
        request.setRevisionCount(0);
        request.setSubmittedNote("Hồ sơ demo chờ duyệt");
        request.setReviewNote(null);
        request.setTermsAcceptedAt(DateTimeUtil.now().minusDays(1));
        request.setTermsVersion("SKILLSWAP_MENTOR_TERMS_V1");
        request.setSubmittedAt(DateTimeUtil.now().minusHours(4));
        request.setReviewedByUserId(null);
        request.setReviewedAt(null);
        request.setWithdrawnAt(null);
        request.setApprovedAt(null);
        request.setRejectionReason(null);
        request.setLockedByUserId(null);
        request.setLockedAt(null);
        request.setLockExpiresAt(null);
        request.setPreviousRequest(null);
        MentorVerificationRequest savedRequest = mentorVerificationRequestRepository.save(request);

        mentorProfile.setStatus(MentorStatus.PENDING_VERIFICATION);
        mentorProfile.setAvailable(false);
        mentorProfile.setVerifiedAt(null);
        mentorProfile.setBookingSuspendedUntil(null);
        mentorProfileRepository.save(mentorProfile);

        if (mentorVerificationRequestDocumentCount(savedRequest.getId()) == 0) {
            createVerificationDocument(savedRequest, user, VerificationDocumentType.FPTU_AFFILIATION_PROOF, "jpg");
            createVerificationDocument(savedRequest, user, VerificationDocumentType.EXPERTISE_PROOF, "pdf");
        }

        if (mentorVerificationRequestEventRepository.findByRequestIdOrderByCreatedAtAsc(savedRequest.getId()).isEmpty()) {
            appendRequestEvent(savedRequest, MentorVerificationEventType.REQUEST_CREATED, null, VerificationStatus.DRAFT, VerificationStatus.DRAFT, "Tạo hồ sơ demo");
            appendRequestEvent(savedRequest, MentorVerificationEventType.SUBMITTED, user, VerificationStatus.DRAFT, VerificationStatus.PENDING_REVIEW, "Nộp hồ sơ demo");
        }
    }

    private long mentorVerificationRequestDocumentCount(UUID requestId) {
        return mentorVerificationDocumentRepository.findByRequestIdOrderByUploadedAtAsc(requestId).size();
    }

    private void createVerificationDocument(MentorVerificationRequest request, User user, VerificationDocumentType documentType, String extension) {
        StoredFile storedFile = StoredFile.builder()
                .ownerUserId(user.getId())
                .purpose(FilePurpose.VERIFICATION_DOCUMENT)
                .originalName(demoFileName(user.getEmail(), documentType, extension))
                .storageProvider("DEMO")
                .storageKey("demo/verification/" + user.getEmail() + "/" + documentType.name().toLowerCase() + "." + extension)
                .publicUrl("https://storage.skillswap.local/" + user.getEmail() + "/" + documentType.name().toLowerCase() + "." + extension)
                .mimeType("pdf".equalsIgnoreCase(extension) ? "application/pdf" : "image/jpeg")
                .sizeBytes("pdf".equalsIgnoreCase(extension) ? 256_000L : 128_000L)
                .checksum("demo-" + user.getEmail() + "-" + documentType.name())
                .build();
        StoredFile savedFile = storedFileRepository.save(storedFile);

        MentorVerificationDocument document = MentorVerificationDocument.builder()
                .request(request)
                .documentType(documentType)
                .status(VerificationDocumentStatus.UPLOADED)
                .storageKind("pdf".equalsIgnoreCase(extension) ? VerificationStorageKind.DOCUMENT : VerificationStorageKind.IMAGE)
                .storedFileId(savedFile.getId())
                .originalFilename(savedFile.getOriginalName())
                .contentType(savedFile.getMimeType())
                .sizeBytes(savedFile.getSizeBytes())
                .fileUrl(savedFile.getPublicUrl())
                .isActive(true)
                .version(1)
                .uploadedByUserId(user.getId())
                .build();
        mentorVerificationDocumentRepository.save(document);
    }

    private void appendRequestEvent(
            MentorVerificationRequest request,
            MentorVerificationEventType eventType,
            User actor,
            VerificationStatus fromStatus,
            VerificationStatus toStatus,
            String note
    ) {
        MentorVerificationRequestEvent event = MentorVerificationRequestEvent.builder()
                .request(request)
                .eventType(eventType)
                .actorUserId(actor != null ? actor.getId() : null)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .note(note)
                .build();
        mentorVerificationRequestEventRepository.save(event);
    }


    private String demoProviderUserId(String email) {
        return "demo-google-" + email;
    }

    private String demoAvatarUrl(String key) {
        return "https://storage.skillswap.local/avatars/" + key + ".png";
    }

    private String demoFileName(String email, VerificationDocumentType documentType, String extension) {
        return email + "-" + documentType.name().toLowerCase() + "." + extension;
    }

    private Integer normalizedServicePrice(boolean serviceFree, Integer durationMinutes, Integer configuredPriceScoin) {
        if (serviceFree) {
            return 0;
        }
        if (durationMinutes == null || durationMinutes <= 0) {
            throw new IllegalStateException("Demo mentor service duration must be positive");
        }
        int minimumPrice = durationMinutes * MIN_SERVICE_PRICE_SCOIN_PER_MINUTE;
        int configured = configuredPriceScoin == null ? 0 : configuredPriceScoin;
        return Math.max(configured, minimumPrice);
    }

    private List<MentorSeed> mentorSeeds() {
        List<MentorSeed> seeds = new ArrayList<>();

        seeds.addAll(buildMentorGroup(
                "backend",
                "Backend",

                "TECH",
                "BACKEND",
                20,
                1,
                "Spring Boot Mentor",
                "Backend, Spring Boot, REST API, PostgreSQL, Docker, clean architecture",
                "EXE101, EXE201, PRJ301",
                TeachingMode.ONLINE,
                60,
                false,
                120,
                1L
        ));

        seeds.addAll(buildMentorGroup(
                "communication",
                "Communication",

                "COMMUNICATION",
                "COMMUNICATION",
                10,
                21,
                "Communication Mentor",
                "Presentation, storytelling, teamwork, UX explanation, demo pitching",
                "COM101, COM102, PRJ301",
                TeachingMode.HYBRID,
                90,
                false,
                100,
                21L
        ));

        seeds.addAll(buildMentorGroup(
                "ai",
                "AI",

                "TECH",
                "AI",
                10,
                31,
                "AI Mentor",
                "Machine learning, Python, data processing, model review, project guidance",
                "AI100, ML101, DSA",
                TeachingMode.ONLINE,
                90,
                false,
                180,
                41L
        ));

        seeds.addAll(buildMentorGroup(
                "design",
                "Design",

                "TECH",
                "DESIGN",
                10,
                41,
                "Design Mentor",
                "UI design, visual storytelling, product demo, frontend presentation, design review",
                "WEB101, UIX201, PRJ301",
                TeachingMode.HYBRID,
                60,
                false,
                110,
                61L
        ));

        seeds.addAll(buildMentorGroup(
                "business",
                "Business",

                "BUSINESS",
                "BUSINESS",
                10,
                51,
                "International Business Mentor",
                "Business strategy, market analysis, internship guidance, communication",
                "BUS101, MKT201, COM102",
                TeachingMode.ONLINE,
                60,
                false,
                130,
                81L
        ));

        seeds.addAll(buildRandomMentors(40, 61));

        if (seeds.size() != TOTAL_MENTOR_COUNT) {
            throw new IllegalStateException("Demo mentor seed count must be exactly " + TOTAL_MENTOR_COUNT + ", but was " + seeds.size());
        }
        return seeds;
    }

    private List<MentorSeed> buildMentorGroup(
            String emailPrefix,
            String fullNamePrefix,
            String category,
            String focusKey,
            int count,
            int startIndex,
            String headlinePrefix,
            String expertisePrefix,
            String supportingSubjectsPrefix,
            TeachingMode teachingMode,
            Integer sessionDuration,
            boolean serviceFree,
            int basePriceScoin,
            long verifiedDaysStart
    ) {
        List<MentorSeed> seeds = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            int seedIndex = startIndex + i - 1;
            boolean experienced = i % 4 == 0;
            String suffix = String.format("%02d", seedIndex);
            String email = String.format("mentor%02d.demo@skillswap.local", seedIndex);
            String fullName = vietnameseFullName(seedIndex);
            String headline = specializedHeadline(category, focusKey, seedIndex, experienced);
            String expertiseDescription = specializedExpertiseDescription(category, focusKey, seedIndex, experienced);
            String supportingSubjects = specializedSupportingSubjects(category, focusKey, seedIndex);
            String serviceTitle = specializedServiceTitle(category, focusKey, seedIndex);
            String serviceDescription = specializedServiceDescription(category, focusKey, seedIndex);
            Integer priceScoin = normalizedServicePrice(serviceFree, sessionDuration, basePriceScoin + ((i - 1) * 5));
            int totalCompletedSessions = 10 + i;
            int totalReviews = 6 + (i % 12);
            BigDecimal averageRating = BigDecimal.valueOf(450L - ((i - 1L) % 10L) * 5L, 2);

            seeds.add(new MentorSeed(
                    email,
                    fullName,

                    headlinePrefix + " " + suffix,
                    expertisePrefix,
                    supportingSubjectsPrefix,
                    teachingMode,
                    sessionDuration,
                    demoAvatarUrl("mentor" + suffix),
                    serviceTitle,
                    serviceDescription,
                    sessionDuration,
                    serviceFree,
                    priceScoin,
                    true,
                    Math.toIntExact(verifiedDaysStart + i),
                    totalCompletedSessions,
                    totalReviews,
                    averageRating
            ).withProfileContent(fullName, headline, expertiseDescription, supportingSubjects));
        }
        return seeds;
    }

    private List<MentorSeed> buildRandomMentors(int count, int startIndex) {
        List<RandomTrack> tracks = new ArrayList<>(List.of(
                new RandomTrack("TECH", "SECURITY", "Security Mentor", "Information security, secure coding, system hardening", "SEC101, DSA, EXE101", TeachingMode.ONLINE, 60, false, 95),
                new RandomTrack("TECH", "SYSTEMS", "System Analysis Mentor", "Requirements, database design, UML, architecture review", "DB101, UML201, PRJ301", TeachingMode.HYBRID, 60, false, 105),
                new RandomTrack("TECH", "AI", "AI Mentor", "Machine learning, Python, data preparation, portfolio review", "AI100, ML101, DSA", TeachingMode.ONLINE, 90, false, 175),
                new RandomTrack("COMMUNICATION", "COMMUNICATION", "Communication Mentor", "Presentation, teamwork, pitching, public speaking", "COM101, COM102, PRJ301", TeachingMode.OFFLINE, 90, false, 90),
                new RandomTrack("LANGUAGE", "LANGUAGE", "English Mentor", "English communication, interview practice, speaking confidence", "ENG101, ENG201, COM102", TeachingMode.ONLINE, 60, false, 115),
                new RandomTrack("LAW", "LAW", "Law Mentor", "Legal studies, documentation, presentation structure, career advice", "LAW101, COM102, EXE101", TeachingMode.ONLINE, 60, false, 80),
                new RandomTrack("BUSINESS", "MARKETING", "Business Mentor", "Marketing, business analysis, internship prep, communication", "BUS101, MKT201, COM102", TeachingMode.HYBRID, 60, false, 100),
                new RandomTrack("BUSINESS", "ECOMMERCE", "E-commerce Mentor", "E-commerce, product review, project storytelling, digital business", "ECOM101, PRJ301, COM102", TeachingMode.ONLINE, 60, false, 100)
        ));

        List<MentorSeed> seeds = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            int seedIndex = startIndex + i - 1;
            RandomTrack track = tracks.get((i - 1) % tracks.size());
            String suffix = String.format("%02d", seedIndex);
            String fullName = vietnameseFullName(seedIndex);
            String headline = specializedHeadline(track.category(), track.focusKey(), seedIndex, i % 5 == 0);
            String expertiseDescription = specializedExpertiseDescription(track.category(), track.focusKey(), seedIndex, i % 5 == 0);
            String supportingSubjects = specializedSupportingSubjects(track.category(), track.focusKey(), seedIndex);
            String serviceTitle = specializedServiceTitle(track.category(), track.focusKey(), seedIndex);
            String serviceDescription = specializedServiceDescription(track.category(), track.focusKey(), seedIndex);

            seeds.add(new MentorSeed(
                    String.format("mentor%02d.demo@skillswap.local", seedIndex),
                    fullName,

                    headline,
                    expertiseDescription,
                    supportingSubjects,
                    track.teachingMode(),
                    track.sessionDuration(),
                    demoAvatarUrl("mentor" + suffix),
                    serviceTitle,
                    serviceDescription,
                    track.sessionDuration(),
                    track.serviceFree(),
                    normalizedServicePrice(track.serviceFree(), track.sessionDuration(), track.basePriceScoin() + ((i - 1) * 3)),
                    true,
                    120 + i,
                    5 + i,
                    3 + (i % 10),
                    BigDecimal.valueOf(430L - ((i - 1L) % 6L) * 6L, 2)
            ));
        }
        return seeds;
    }


    private String vietnameseFullName(int seedIndex) {
        String lastName = VIETNAMESE_LAST_NAMES.get(seedIndex % VIETNAMESE_LAST_NAMES.size());
        String middleName = VIETNAMESE_MIDDLE_NAMES.get((seedIndex * 3) % VIETNAMESE_MIDDLE_NAMES.size());
        String givenName = VIETNAMESE_GIVEN_NAMES.get((seedIndex * 7) % VIETNAMESE_GIVEN_NAMES.size());
        return lastName + " " + middleName + " " + givenName;
    }

    private String specializedHeadline(String category, String focusKey, int seedIndex, boolean experienced) {
        return switch (focusKey) {
            case "BACKEND" -> experienced
                    ? "Experienced backend mentor | Spring Boot, PostgreSQL, Docker"
                    : keywordVariant(seedIndex,
                            "Mentor backend Java | Spring Boot, REST API, SWP391",
                            "Mentor fullstack | React, Spring Boot, database design",
                            "Mentor project | OJT, PRJ301, clean architecture");
            case "AI" -> keywordVariant(seedIndex,
                    "Mentor AI | Python, machine learning, data preprocessing",
                    "Mentor tri tue nhan tao | model review, MLOps co ban, portfolio AI",
                    "Mentor data science | Pandas, notebook, project AI");
            case "SYSTEMS" -> keywordVariant(seedIndex,
                    "Mentor he thong thong tin | database, UML, BA co ban",
                    "Mentor phan tich he thong | database design, ERD, SQL",
                    "Mentor database | requirements, system analysis, documentation");
            case "SECURITY" -> keywordVariant(seedIndex,
                    "Mentor an toan thong tin | secure coding, OWASP, network basics",
                    "Mentor security | pentest co ban, authentication, logging",
                    "Mentor cyber security | secure API, risk review, SOC mindset");
            case "DESIGN" -> keywordVariant(seedIndex,
                    "Mentor UI/UX | Figma, design system, presentation deck",
                    "Mentor thiet ke do hoa so | UI review, portfolio, storytelling",
                    "Mentor san pham so | React UI, UX writing, visual critique");
            case "COMMUNICATION" -> keywordVariant(seedIndex,
                    "Mentor multimedia | React, storytelling, pitching, demo day",
                    "Mentor presentation | content plan, communication, UX explanation",
                    "Mentor truyền thông số | product demo, public speaking, teamwork");
            case "BUSINESS" -> keywordVariant(seedIndex,
                    "Mentor kinh doanh quốc tế | case analysis, internship prep",
                    "Mentor business | market research, communication, CV review",
                    "Mentor career | networking, OJT mindset, business presentation");
            case "MARKETING" -> keywordVariant(seedIndex,
                    "Mentor digital marketing | content, campaign review, analytics",
                    "Mentor marketing | brand pitch, customer insight, CV review",
                    "Mentor growth | research, internship prep, presentation");
            case "ECOMMERCE" -> keywordVariant(seedIndex,
                    "Mentor thương mại điện tử | e-commerce, project review, SQL cơ bản",
                    "Mentor e-commerce | product flow, analytics, pitching",
                    "Mentor digital business | PRJ301, idea validation, feedback");
            case "LANGUAGE" -> keywordVariant(seedIndex,
                    "Mentor tiếng Anh | interview, speaking, presentation",
                    "Mentor English communication | CV, mock interview, confidence",
                    "Mentor language | study plan, speaking, career support");
            case "LAW" -> keywordVariant(seedIndex,
                    "Mentor luật kinh tế | legal writing, report structure, presentation",
                    "Mentor law | business law basics, argumentation, thesis support",
                    "Mentor học thuật | documentation, critical thinking, defense");
            default -> switch (category) {
                case "TECH" -> "Mentor công nghệ | project review, backend, database";
                case "COMMUNICATION" -> "Mentor truyền thông | content, pitching, collaboration";
                case "BUSINESS" -> "Mentor kinh doanh | internship, CV, communication";
                default -> "Mentor SkillSwap | hỗ trợ môn học và định hướng";
            };
        };
    }

    private String specializedExpertiseDescription(String category, String focusKey, int seedIndex, boolean experienced) {
        String intro = experienced
                ? "Mình có kinh nghiệm thực tế và thường hỗ trợ người học chuẩn bị internship, OJT và project."
                : "Mình là mentor đang theo học hoặc vừa hoàn thành các project-based mentoring, quen với cách chấm project và review báo cáo.";

        return switch (focusKey) {
            case "BACKEND" -> intro + " Mình mạnh về Spring Boot, REST API, PostgreSQL, Docker và clean architecture. Có thể hỗ trợ các môn như EXE101, EXE201, SWP391, PRJ301, code review và tối ưu database.";
            case "AI" -> intro + " Mình tập trung vào Python, machine learning, data preprocessing và cách trình bày project AI rõ ràng. Có thể hỗ trợ portfolio AI, review notebook, model baseline và báo cáo thực nghiệm.";
            case "SYSTEMS" -> intro + " Mình hỗ trợ database design, SQL, UML, requirements và system analysis. Phù hợp cho bạn đang làm đồ án cần ERD, use case, sequence diagram hoặc chuẩn bị bảo vệ proposal.";
            case "SECURITY" -> intro + " Mình hỗ trợ secure coding, authentication, logging, OWASP và tư duy threat modeling cơ bản. Hợp với bạn muốn học backend an toàn hoặc làm project có yếu tố bảo mật.";
            case "DESIGN" -> intro + " Mình hỗ trợ UI/UX, Figma, design critique, storytelling và cách kết nối giữa design với frontend React. Có thể review portfolio, case study và cấu trúc trình bày sản phẩm.";
            case "COMMUNICATION" -> intro + " Mình hỗ trợ thuyết trình, storytelling, demo pitching và phối hợp nội dung cho project liên ngành. Hợp với các bạn cần luyện trình bày đồ án, bảo vệ project hoặc demo day.";
            case "BUSINESS" -> intro + " Mình hỗ trợ market analysis, business presentation, networking, CV và định hướng internship. Có thể review slide, assignment và tình huống thực tế trong môi trường doanh nghiệp.";
            case "MARKETING" -> intro + " Mình hỗ trợ content planning, campaign thinking, customer insight và CV cho ngành marketing. Hợp với bạn cần góp ý proposal, deck, case study hoặc định hướng thực tập.";
            case "ECOMMERCE" -> intro + " Mình hỗ trợ e-commerce flow, phân tích sản phẩm, idea validation và cách trình bày project kinh doanh số. Có thể review assignment, phản biện logic và luyện pitching.";
            case "LANGUAGE" -> intro + " Mình hỗ trợ speaking, mock interview, CV tiếng Anh và kỹ năng trình bày học thuật. Hợp với bạn muốn tăng tự tin khi phỏng vấn hoặc thuyết trình trước hội đồng.";
            case "LAW" -> intro + " Mình hỗ trợ legal writing, lập luận, cấu trúc báo cáo và cách trình bày case. Hợp với bạn cần định hướng môn học, phản biện nội dung hoặc chuẩn bị bảo vệ bài làm.";
            default -> intro + " Mình có thể hỗ trợ review bài tập, giải đáp thắc mắc, định hướng môn học và góp ý project theo bối cảnh FPT.";
        };
    }

    private String specializedSupportingSubjects(String category, String focusKey, int seedIndex) {
        return switch (focusKey) {
            case "BACKEND" -> keywordVariant(seedIndex,
                    "EXE101, EXE201, SWP391, PRJ301, Spring Boot, PostgreSQL, Docker",
                    "OJT, PRJ301, React, Spring Boot, REST API, database design",
                    "Java backend, Clean Architecture, CI/CD cơ bản, code review");
            case "AI" -> keywordVariant(seedIndex,
                    "Python, Machine Learning, AI100, ML101, data preprocessing",
                    "Model evaluation, notebook review, portfolio AI, Pandas",
                    "Deep learning cơ bản, project AI, data storytelling");
            case "SYSTEMS" -> keywordVariant(seedIndex,
                    "Database Design, SQL, UML201, system analysis, BA cơ bản",
                    "ERD, sequence diagram, use case, report structure",
                    "Requirements, documentation, PRJ301, architecture review");
            case "SECURITY" -> keywordVariant(seedIndex,
                    "Secure coding, OWASP, authentication, JWT, logging",
                    "Network basics, API security, risk review, backend security",
                    "System hardening, threat modeling, security checklist");
            case "DESIGN" -> keywordVariant(seedIndex,
                    "Figma, UI/UX critique, design system, portfolio",
                    "React UI, presentation deck, case study, visual storytelling",
                    "Prototype review, typography, color system, product demo");
            case "COMMUNICATION" -> keywordVariant(seedIndex,
                    "COM101, COM102, PRJ301, storytelling, pitching",
                    "Presentation, teamwork, demo script, public speaking",
                    "Content planning, UX explanation, stage confidence");
            case "BUSINESS" -> keywordVariant(seedIndex,
                    "BUS101, MKT201, communication, internship prep",
                    "Case analysis, business presentation, CV review, OJT mindset",
                    "Market research, networking, slide review, report critique");
            case "MARKETING" -> keywordVariant(seedIndex,
                    "Marketing plan, customer insight, content review, campaign critique",
                    "Brand storytelling, proposal review, CV, internship support",
                    "Analytics cơ bản, pitch deck, communication");
            case "ECOMMERCE" -> keywordVariant(seedIndex,
                    "E-commerce, product flow, PRJ301, business analytics",
                    "Proposal review, idea validation, pitching, report structure",
                    "Digital business, customer journey, feedback presentation");
            case "LANGUAGE" -> keywordVariant(seedIndex,
                    "English speaking, mock interview, CV tiếng Anh",
                    "Presentation, confidence, study guidance, communication",
                    "Listening-speaking, internship interview, pronunciation");
            case "LAW" -> keywordVariant(seedIndex,
                    "Legal writing, report structure, argumentation, presentation",
                    "Business law basics, case reading, thesis support",
                    "Study guidance, documentation, defense preparation");
            default -> switch (category) {
                case "TECH" -> "Project review, backend, database, giải đáp thắc mắc";
                case "BUSINESS" -> "CV review, internship support, business presentation";
                default -> "Hướng dẫn môn học, giải đáp thắc mắc, review project";
            };
        };
    }

    private String specializedServiceTitle(String category, String focusKey, int seedIndex) {
        return switch (focusKey) {
            case "BACKEND" -> keywordVariant(seedIndex,
                    "Review đồ án backend Spring Boot và database",
                    "Mentoring OJT, SWP391 và project fullstack React + Spring",
                    "Code review Java backend và clean architecture");
            case "AI" -> keywordVariant(seedIndex,
                    "Review project AI và portfolio machine learning",
                    "Mentoring Python, data preprocessing và model baseline",
                    "Hỗ trợ report, notebook và thuyết trình project AI");
            case "SYSTEMS" -> keywordVariant(seedIndex,
                    "Review database, UML và system analysis",
                    "Mentoring requirements, ERD và báo cáo đồ án",
                    "Hỗ trợ PRJ301, SQL và kiến trúc hệ thống");
            case "DESIGN" -> keywordVariant(seedIndex,
                    "Review portfolio UI/UX và case study",
                    "Mentoring Figma, React UI và product storytelling",
                    "Góp ý design system và bài thuyết trình sản phẩm");
            case "COMMUNICATION" -> keywordVariant(seedIndex,
                    "Luyện pitching và demo presentation",
                    "Góp ý storytelling, teamwork và nội dung demo",
                    "Review slide, script và kỹ năng đứng trình bày");
            default -> keywordVariant(seedIndex,
                    "Mentoring định hướng môn học và review project",
                    "Hỗ trợ internship, CV và giải đáp thắc mắc",
                    "Góp ý assignment, báo cáo và kỹ năng trình bày");
        };
    }

    private String specializedServiceDescription(String category, String focusKey, int seedIndex) {
        return switch (focusKey) {
            case "BACKEND" -> "Buổi mentoring tập trung vào Spring Boot, database, SWP391, OJT hoặc review code Java backend. Mentee có thể mang source code, ERD hoặc backlog để được góp ý thực tế.";
            case "AI" -> "Buổi mentoring tập trung vào project AI, Python, data cleaning và cách trình bày kết quả mô hình. Phù hợp cho bạn cần review notebook, baseline hoặc portfolio học máy.";
            case "SYSTEMS" -> "Buổi mentoring tập trung vào SQL, database design, UML và logic nghiệp vụ của đồ án. Phù hợp khi bạn cần rà ERD, use case hoặc report system analysis.";
            case "DESIGN" -> "Buổi mentoring tập trung vào Figma, UI critique, case study và cách kể chuyện sản phẩm. Có thể review portfolio, prototype hoặc màn hình React UI.";
            case "COMMUNICATION" -> "Buổi mentoring tập trung vào storytelling, pitching, script và cách phối hợp nhóm để demo thuyết phục hơn. Hợp với bạn chuẩn bị bảo vệ project hoặc làm presentation quan trọng.";
            default -> "Buổi mentoring tập trung vào giải đáp thắc mắc, review project, định hướng môn học và góp ý tài liệu thực tế theo bối cảnh FPT.";
        };
    }

    private String keywordVariant(int seedIndex, String first, String second, String third) {
        return switch (Math.floorMod(seedIndex, 3)) {
            case 0 -> first;
            case 1 -> second;
            default -> third;
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private record RandomTrack(
            String category,
            String focusKey,
            String headlinePrefix,
            String expertisePrefix,
            String supportingSubjectsPrefix,
            TeachingMode teachingMode,
            Integer sessionDuration,
            boolean serviceFree,
            int basePriceScoin
    ) {
    }
    private record MentorSeed(
            String email,
            String fullName,
            String headline,
            String expertiseDescription,
            String supportingSubjects,
            TeachingMode teachingMode,
            Integer sessionDuration,
            String avatarUrl,
            String serviceTitle,
            String serviceDescription,
            Integer serviceDuration,
            boolean serviceFree,
            Integer priceScoin,
            boolean activeMentor,
            Integer verifiedDaysAgo,
            Integer totalCompletedSessions,
            Integer totalReviews,
            BigDecimal averageRating
    ) {
        private MentorSeed withProfileContent(
                String fullName,
                String headline,
                String expertiseDescription,
                String supportingSubjects
        ) {
            return new MentorSeed(
                    email,
                    fullName,

                    headline,
                    expertiseDescription,
                    supportingSubjects,
                    teachingMode,
                    sessionDuration,
                    avatarUrl,
                    serviceTitle,
                    serviceDescription,
                    serviceDuration,
                    serviceFree,
                    priceScoin,
                    activeMentor,
                    verifiedDaysAgo,
                    totalCompletedSessions,
                    totalReviews,
                    averageRating
            );
        }
    }

    private record SlotSeed(int dayOffset, int hour, int minute, int durationMinutes) {
    }
}
