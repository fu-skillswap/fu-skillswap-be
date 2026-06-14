package com.fptu.exe.skillswap.modules.system.seeder;

import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.academic.domain.Campus;
import com.fptu.exe.skillswap.modules.academic.domain.CampusCode;
import com.fptu.exe.skillswap.modules.academic.domain.Specialization;
import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.academic.repository.AcademicProgramRepository;
import com.fptu.exe.skillswap.modules.academic.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.academic.repository.SpecializationRepository;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTag;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagId;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.catalog.repository.MentorTagRepository;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import com.fptu.exe.skillswap.modules.filestorage.domain.FilePurpose;
import com.fptu.exe.skillswap.modules.filestorage.domain.StoredFile;
import com.fptu.exe.skillswap.modules.filestorage.repository.StoredFileRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserRole;
import com.fptu.exe.skillswap.modules.identity.domain.UserRoleId;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRoleRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationDocument;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationEventType;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequest;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorVerificationRequestEvent;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationDocumentType;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationMethod;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.VerificationStorageKind;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationDocumentRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestEventRepository;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorVerificationRequestRepository;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Component
@ConditionalOnProperty(prefix = "application.demo-seed", name = "enabled", havingValue = "true")
@Order(200)
@RequiredArgsConstructor
@Slf4j
public class DevDemoDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final CampusRepository campusRepository;
    private final AcademicProgramRepository academicProgramRepository;
    private final SpecializationRepository specializationRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final TagRepository tagRepository;
    private final MentorTagRepository mentorTagRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final MentorVerificationRequestRepository mentorVerificationRequestRepository;
    private final MentorVerificationRequestEventRepository mentorVerificationRequestEventRepository;
    private final MentorVerificationDocumentRepository mentorVerificationDocumentRepository;
    private final StoredFileRepository storedFileRepository;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting dev demo data seeding for mentor verification and discovery...");

        seedTags();
        seedAdminAccounts();
        seedMentorVerificationScenarios();
        seedActiveDiscoveryMentors();

        log.info("Dev demo data seeding completed successfully.");
    }

    private void seedAdminAccounts() {
        User quangTam = ensureUser(
                "quangtamshare@gmail.com",
                "Quang Tam Admin",
                "https://api.dicebear.com/9.x/initials/svg?seed=QuangTam"
        );
        ensureRole(quangTam, RoleCode.ADMIN, null);

        User thnn = ensureUser(
                "thnn16102005@gmail.com",
                "Thao Nhi Admin",
                "https://api.dicebear.com/9.x/initials/svg?seed=ThaoNhi"
        );
        ensureRole(thnn, RoleCode.ADMIN, null);

        User h4Vinh = ensureUser(
                "h4vinh@gmail.com",
                "Ha Vinh Admin",
                "https://api.dicebear.com/9.x/initials/svg?seed=HaVinh"
        );
        ensureRole(h4Vinh, RoleCode.ADMIN, null);
    }

    private void seedMentorVerificationScenarios() {
        Campus hcm = requiredCampus(CampusCode.HCM);
        AcademicProgram cntt = requiredProgram("CNTT");
        Specialization ktpm = requiredSpecialization("CNTT_KTPM");
        Specialization ttnt = requiredSpecialization("CNTT_TTNT");
        Campus haNoi = requiredCampus(CampusCode.HA_NOI);
        AcademicProgram qtkd = requiredProgram("QTKD");
        Specialization marketing = requiredSpecialization("QTKD_MKT");

        User draftUser = ensureUser("seed.draft.mentor@skillswap.local", "Mentor Draft", avatar("MentorDraft"));
        ensureRole(draftUser, RoleCode.MENTEE, null);
        ensureStudentProfile(draftUser, hcm, cntt, ktpm, 5, false, "SE170001");
        ensureMentorProfile(draftUser, profileSpec(
                MentorStatus.DRAFT, "Backend mentor cho sinh viên mới vào nghề", "Mình hỗ trợ định hướng Java backend và cách học môn chuyên ngành.",
                "Backend Developer", "FPT Software", "Information Technology", TeachingMode.ONLINE,
                60, decimal("120000"), decimal("1.5"), true, null, 0, 0, 0
        ));
        MentorVerificationRequest draftRequest = ensureVerificationRequest(
                draftUser,
                VerificationStatus.DRAFT,
                VerificationMethod.MANUAL,
                "Hồ sơ đang chuẩn bị minh chứng",
                null,
                0,
                null,
                null,
                null,
                null
        );
        ensureVerificationDocumentBundle(draftRequest, draftUser, VerificationDocumentStatus.UPLOADED, false);
        ensureEvent(draftRequest, MentorVerificationEventType.REQUEST_CREATED, draftUser, null, VerificationStatus.DRAFT, "Khởi tạo hồ sơ xác thực");

        User pendingUser = ensureUser("seed.pending.mentor@skillswap.local", "Mentor Pending", avatar("MentorPending"));
        ensureRole(pendingUser, RoleCode.MENTEE, null);
        ensureStudentProfile(pendingUser, haNoi, cntt, ttnt, 8, false, "SE170002");
        ensureMentorProfile(pendingUser, profileSpec(
                MentorStatus.PENDING_VERIFICATION, "AI mentor cho định hướng nghề nghiệp", "Hỗ trợ lộ trình AI/ML cho sinh viên CNTT.",
                "AI Engineer", "FPT Smart Cloud", "Artificial Intelligence", TeachingMode.HYBRID,
                90, decimal("180000"), decimal("2.5"), true, null, 0, 0, 0
        ));
        MentorVerificationRequest pendingRequest = ensureVerificationRequest(
                pendingUser,
                VerificationStatus.PENDING_REVIEW,
                VerificationMethod.MANUAL,
                "Em đã bổ sung đầy đủ minh chứng học tập và năng lực.",
                null,
                0,
                LocalDateTime.now().minusDays(2),
                null,
                null,
                null
        );
        ensureVerificationDocumentBundle(pendingRequest, pendingUser, VerificationDocumentStatus.UPLOADED, false);
        ensureEvent(pendingRequest, MentorVerificationEventType.REQUEST_CREATED, pendingUser, null, VerificationStatus.DRAFT, "Khởi tạo hồ sơ");
        ensureEvent(pendingRequest, MentorVerificationEventType.SUBMITTED, pendingUser, VerificationStatus.DRAFT, VerificationStatus.PENDING_REVIEW, "Nộp hồ sơ chờ admin duyệt");

        User revisionUser = ensureUser("seed.revision.mentor@skillswap.local", "Mentor Revision", avatar("MentorRevision"));
        ensureRole(revisionUser, RoleCode.MENTEE, null);
        ensureStudentProfile(revisionUser, hcm, qtkd, marketing, 6, false, "SE170003");
        ensureMentorProfile(revisionUser, profileSpec(
                MentorStatus.DRAFT, "Digital marketing mentor", "Hỗ trợ xây nền tảng marketing, branding và portfolio cá nhân.",
                "Marketing Executive", "VNG", "Marketing", TeachingMode.ONLINE,
                60, decimal("150000"), decimal("3.0"), true, null, 0, 0, 0
        ));
        MentorVerificationRequest revisionRequest = ensureVerificationRequest(
                revisionUser,
                VerificationStatus.NEEDS_REVISION,
                VerificationMethod.MANUAL,
                "Em muốn được review lại hồ sơ sau khi bổ sung portfolio.",
                "Thiếu minh chứng affiliation rõ nét và cần bổ sung portfolio support.",
                1,
                LocalDateTime.now().minusDays(4),
                LocalDateTime.now().minusDays(3),
                null,
                null
        );
        ensureVerificationDocumentBundle(revisionRequest, revisionUser, VerificationDocumentStatus.REJECTED, true);
        ensureEvent(revisionRequest, MentorVerificationEventType.REQUEST_CREATED, revisionUser, null, VerificationStatus.DRAFT, "Khởi tạo hồ sơ");
        ensureEvent(revisionRequest, MentorVerificationEventType.SUBMITTED, revisionUser, VerificationStatus.DRAFT, VerificationStatus.PENDING_REVIEW, "Nộp hồ sơ lần đầu");
        ensureEvent(revisionRequest, MentorVerificationEventType.REVISION_REQUESTED, null, VerificationStatus.PENDING_REVIEW, VerificationStatus.NEEDS_REVISION, "Admin yêu cầu cập nhật lại minh chứng");

        User approvedUser = ensureUser("seed.approved.mentor@skillswap.local", "Mentor Approved", avatar("MentorApproved"));
        ensureRole(approvedUser, RoleCode.MENTEE, null);
        ensureRole(approvedUser, RoleCode.MENTOR, null);
        ensureStudentProfile(approvedUser, hcm, cntt, ktpm, 0, true, "ALUMNI170001");
        ensureMentorProfile(approvedUser, profileSpec(
                MentorStatus.ACTIVE, "Senior Java mentor cho backend và system design", "Mình từng mentor nhiều bạn intern/junior về Java, Spring Boot và kiến trúc hệ thống.",
                "Senior Backend Engineer", "NashTech", "Software Engineering", TeachingMode.HYBRID,
                90, decimal("250000"), decimal("5.5"), true, LocalDateTime.now().minusDays(20), 4.8, 16, 48
        ));
        MentorVerificationRequest approvedRequest = ensureVerificationRequest(
                approvedUser,
                VerificationStatus.APPROVED,
                VerificationMethod.MANUAL,
                "Em gửi hồ sơ xác thực đầy đủ.",
                "Hồ sơ hợp lệ, đủ căn cứ xác thực mentor.",
                0,
                LocalDateTime.now().minusDays(22),
                LocalDateTime.now().minusDays(20),
                LocalDateTime.now().minusDays(20),
                null
        );
        ensureVerificationDocumentBundle(approvedRequest, approvedUser, VerificationDocumentStatus.ACCEPTED, false);
        ensureEvent(approvedRequest, MentorVerificationEventType.REQUEST_CREATED, approvedUser, null, VerificationStatus.DRAFT, "Khởi tạo hồ sơ");
        ensureEvent(approvedRequest, MentorVerificationEventType.SUBMITTED, approvedUser, VerificationStatus.DRAFT, VerificationStatus.PENDING_REVIEW, "Nộp hồ sơ");
        ensureEvent(approvedRequest, MentorVerificationEventType.APPROVED, null, VerificationStatus.PENDING_REVIEW, VerificationStatus.APPROVED, "Admin phê duyệt hồ sơ");
    }

    private void seedActiveDiscoveryMentors() {
        Campus hcm = requiredCampus(CampusCode.HCM);
        Campus haNoi = requiredCampus(CampusCode.HA_NOI);
        Campus daNang = requiredCampus(CampusCode.DA_NANG);
        AcademicProgram cntt = requiredProgram("CNTT");
        AcademicProgram qtkd = requiredProgram("QTKD");
        Specialization ktpm = requiredSpecialization("CNTT_KTPM");
        Specialization ttnt = requiredSpecialization("CNTT_TTNT");
        Specialization httt = requiredSpecialization("CNTT_HTTT");
        Specialization fintech = requiredSpecialization("QTKD_CNTC");
        Specialization marketing = requiredSpecialization("QTKD_MKT");

        seedDiscoveryMentor(
                "seed.discovery.java@skillswap.local", "Le Minh Java", hcm, cntt, ktpm, 9, false,
                mentorSpec("Java backend mentor, luyện CV và phỏng vấn", "Senior Java Developer", "Techcombank", "Software Engineering",
                        TeachingMode.ONLINE, 60, "220000", "4.5", true, 4.9, 21, 68, 32),
                List.of("JAVA", "SPRING_BOOT", "SYSTEM_DESIGN"),
                List.of("CV_REVIEW", "INTERVIEW_PREP")
        );
        seedDiscoveryMentor(
                "seed.discovery.ai@skillswap.local", "Tran Bao AI", hcm, cntt, ttnt, 8, false,
                mentorSpec("Mentor AI/ML cho dự án học kỳ và định hướng nghề nghiệp", "AI Engineer", "FPT Smart Cloud", "Artificial Intelligence",
                        TeachingMode.HYBRID, 90, "260000", "3.8", true, 4.7, 14, 42, 14),
                List.of("PYTHON", "MACHINE_LEARNING", "DATA_ANALYTICS"),
                List.of("PROJECT_GUIDE", "CAREER_ORIENTATION")
        );
        seedDiscoveryMentor(
                "seed.discovery.alumni@skillswap.local", "Nguyen Hoang Alumni", haNoi, cntt, httt, 0, true,
                mentorSpec("Alumni mentor về data và growth mindset", "Data Engineer", "Shopee", "Data Engineering",
                        TeachingMode.ONLINE, 60, "280000", "6.0", true, 5.0, 25, 82, 4),
                List.of("SQL", "DATA_ANALYTICS", "SYSTEM_DESIGN"),
                List.of("CAREER_ORIENTATION", "CV_REVIEW")
        );
        seedDiscoveryMentor(
                "seed.discovery.product@skillswap.local", "Pham Linh Product", daNang, qtkd, marketing, 7, false,
                mentorSpec("Mentor về digital marketing và xây portfolio", "Growth Marketer", "Tiki", "Marketing",
                        TeachingMode.OFFLINE, 60, "190000", "4.1", false, 4.5, 9, 20, 11),
                List.of("DIGITAL_MARKETING", "PORTFOLIO_BUILDING", "COMMUNICATION"),
                List.of("PORTFOLIO_REVIEW", "CAREER_ORIENTATION")
        );
        seedDiscoveryMentor(
                "seed.discovery.frontend@skillswap.local", "Vu Anh Frontend", hcm, cntt, ktpm, 6, false,
                mentorSpec("Frontend mentor cho React và teamwork dự án", "Frontend Engineer", "ZaloPay", "Frontend Engineering",
                        TeachingMode.ONLINE, 60, "200000", "3.2", true, 4.4, 7, 18, 40),
                List.of("REACT", "JAVASCRIPT", "UI_UX"),
                List.of("PROJECT_GUIDE", "INTERVIEW_PREP")
        );
        seedDiscoveryMentor(
                "seed.discovery.devops@skillswap.local", "Do Khang DevOps", haNoi, cntt, httt, 9, false,
                mentorSpec("DevOps mentor cho CI/CD, Docker và cloud cơ bản", "Platform Engineer", "VNPT", "Cloud Infrastructure",
                        TeachingMode.HYBRID, 90, "230000", "4.7", true, 4.6, 11, 26, 9),
                List.of("DOCKER", "AWS", "SYSTEM_DESIGN"),
                List.of("PROJECT_GUIDE", "CAREER_ORIENTATION")
        );
        seedDiscoveryMentor(
                "seed.discovery.data@skillswap.local", "Bui Trang Data", hcm, qtkd, fintech, 5, false,
                mentorSpec("Mentor data analytics cho business student", "Data Analyst", "KMS", "Data Analytics",
                        TeachingMode.ONLINE, 60, "175000", "2.8", true, 4.3, 6, 15, 2),
                List.of("SQL", "DATA_ANALYTICS", "COMMUNICATION"),
                List.of("CV_REVIEW", "PROJECT_GUIDE")
        );
        seedDiscoveryMentor(
                "seed.discovery.mobile@skillswap.local", "Hoang Son Mobile", daNang, cntt, ktpm, 8, false,
                mentorSpec("Mobile mentor cho Android và clean architecture", "Mobile Engineer", "MoMo", "Mobile Engineering",
                        TeachingMode.ONLINE, 60, "210000", "3.6", true, 4.8, 12, 33, 6),
                List.of("ANDROID", "JAVA", "SYSTEM_DESIGN"),
                List.of("INTERVIEW_PREP", "PROJECT_GUIDE")
        );
    }

    private void seedDiscoveryMentor(
            String email,
            String fullName,
            Campus campus,
            AcademicProgram program,
            Specialization specialization,
            int semester,
            boolean alumni,
            MentorProfileSeedSpec mentorSpec,
            List<String> expertiseCodes,
            List<String> helpTopicCodes
    ) {
        User user = ensureUser(email, fullName, avatar(fullName));
        ensureRole(user, RoleCode.MENTEE, null);
        ensureRole(user, RoleCode.MENTOR, null);
        ensureStudentProfile(user, campus, program, specialization, semester, alumni, generatedStudentCode(email, alumni));
        MentorProfile mentorProfile = ensureMentorProfile(user, profileSpec(
                MentorStatus.ACTIVE,
                mentorSpec.headline(),
                "Mentor chia sẻ theo hướng thực chiến, tập trung vào nhu cầu học tập và nghề nghiệp của từng mentee.",
                mentorSpec.currentPosition(),
                mentorSpec.currentCompany(),
                mentorSpec.industry(),
                mentorSpec.teachingMode(),
                mentorSpec.sessionDuration(),
                mentorSpec.hourlyRate(),
                mentorSpec.yearsOfExperience(),
                mentorSpec.available(),
                mentorSpec.verifiedAt(),
                mentorSpec.ratingAverage(),
                mentorSpec.totalReviews(),
                mentorSpec.completedSessions()
        ));
        ensureMentorTags(mentorProfile, expertiseCodes, MentorTagType.EXPERTISE);
        ensureMentorTags(mentorProfile, helpTopicCodes, MentorTagType.HELP_TOPIC);
    }

    private void seedTags() {
        ensureTag("JAVA", "Java", "Java", TagType.TECH_SKILL);
        ensureTag("SPRING_BOOT", "Spring Boot", "Spring Boot", TagType.TECH_SKILL);
        ensureTag("SYSTEM_DESIGN", "Thiết kế hệ thống", "System Design", TagType.TECH_SKILL);
        ensureTag("PYTHON", "Python", "Python", TagType.TECH_SKILL);
        ensureTag("MACHINE_LEARNING", "Machine Learning", "Machine Learning", TagType.TECH_SKILL);
        ensureTag("DATA_ANALYTICS", "Phân tích dữ liệu", "Data Analytics", TagType.TECH_SKILL);
        ensureTag("SQL", "SQL", "SQL", TagType.TECH_SKILL);
        ensureTag("REACT", "React", "React", TagType.TECH_SKILL);
        ensureTag("JAVASCRIPT", "JavaScript", "JavaScript", TagType.TECH_SKILL);
        ensureTag("UI_UX", "UI/UX", "UI/UX", TagType.TOOL);
        ensureTag("DOCKER", "Docker", "Docker", TagType.TOOL);
        ensureTag("AWS", "AWS", "AWS", TagType.TOOL);
        ensureTag("ANDROID", "Android", "Android", TagType.TECH_SKILL);
        ensureTag("DIGITAL_MARKETING", "Digital Marketing", "Digital Marketing", TagType.BUSINESS_SKILL);
        ensureTag("PORTFOLIO_BUILDING", "Xây dựng portfolio", "Portfolio Building", TagType.CAREER);
        ensureTag("COMMUNICATION", "Giao tiếp", "Communication", TagType.SOFT_SKILL);
        ensureTag("CV_REVIEW", "Review CV", "CV Review", TagType.HELP_TOPIC);
        ensureTag("INTERVIEW_PREP", "Luyện phỏng vấn", "Interview Preparation", TagType.HELP_TOPIC);
        ensureTag("PROJECT_GUIDE", "Định hướng đồ án", "Project Guidance", TagType.HELP_TOPIC);
        ensureTag("CAREER_ORIENTATION", "Định hướng nghề nghiệp", "Career Orientation", TagType.HELP_TOPIC);
        ensureTag("PORTFOLIO_REVIEW", "Review Portfolio", "Portfolio Review", TagType.HELP_TOPIC);
    }

    private User ensureUser(String email, String fullName, String avatarUrl) {
        User user = userRepository.findByEmail(email).orElseGet(() -> User.builder()
                .email(email)
                .status(UserStatus.ACTIVE)
                .build());
        user.setFullName(fullName);
        user.setAvatarUrl(avatarUrl);
        user.setStatus(UserStatus.ACTIVE);
        user.setLastLoginAt(LocalDateTime.now().minusHours(2));
        return userRepository.save(user);
    }

    private void ensureRole(User user, RoleCode roleCode, User assignedBy) {
        UserRoleId id = new UserRoleId(user.getId(), roleCode);
        if (userRoleRepository.existsById(id)) {
            return;
        }
        userRoleRepository.save(UserRole.builder()
                .id(id)
                .user(user)
                .assignedBy(assignedBy)
                .build());
    }

    private StudentProfile ensureStudentProfile(
            User user,
            Campus campus,
            AcademicProgram program,
            Specialization specialization,
            Integer semester,
            boolean alumni,
            String studentCode
    ) {
        StudentProfile profile = studentProfileRepository.findById(user.getId()).orElseGet(() -> StudentProfile.builder()
                .user(user)
                .userId(user.getId())
                .build());
        profile.setStudentCode(studentCode);
        profile.setCampus(campus);
        profile.setProgram(program);
        profile.setSpecialization(specialization);
        profile.setSemester(semester);
        profile.setIntakeYear(alumni ? 2019 : 2022);
        profile.setAlumni(alumni);
        profile.setGraduationYear(alumni ? 2024 : null);
        profile.setBio("Tài khoản seed phục vụ kiểm thử API trên môi trường dev/VPS.");
        return studentProfileRepository.save(profile);
    }

    private MentorProfile ensureMentorProfile(User user, MentorProfileSeedSpec spec) {
        MentorProfile profile = mentorProfileRepository.findById(user.getId()).orElseGet(() -> MentorProfile.builder()
                .user(user)
                .userId(user.getId())
                .build());
        profile.setStatus(spec.status());
        profile.setHeadline(spec.headline());
        profile.setBio(spec.bio());
        profile.setCurrentPosition(spec.currentPosition());
        profile.setCurrentCompany(spec.currentCompany());
        profile.setIndustry(spec.industry());
        profile.setTeachingMode(spec.teachingMode());
        profile.setSessionDuration(spec.sessionDuration());
        profile.setHourlyRate(spec.hourlyRate());
        profile.setYearsOfExperience(spec.yearsOfExperience());
        profile.setAvailable(spec.available());
        profile.setVerifiedAt(spec.verifiedAt());
        profile.setAverageRating(BigDecimal.valueOf(spec.ratingAverage()).setScale(2, RoundingMode.HALF_UP));
        profile.setTotalReviews(spec.totalReviews());
        profile.setTotalCompletedSessions(spec.completedSessions());
        profile.setTotalSessions(spec.completedSessions() + Math.max(2, spec.totalReviews()));
        profile.setExpertiseSummary("Seeder demo: hồ sơ mentor đầy đủ để test mentor discovery và matching.");
        profile.setMentoringStyle("Phản hồi thẳng, có bài tập nhỏ và follow-up sau phiên.");
        profile.setTargetMentees("Sinh viên FPTU cần định hướng học tập hoặc nghề nghiệp.");
        profile.setLinkedinUrl("https://www.linkedin.com/in/" + user.getEmail().replace("@", "-").replace(".", "-"));
        profile.setGithubUrl("https://github.com/" + user.getEmail().split("@")[0].replace(".", "-"));
        profile.setPortfolioUrl("https://portfolio.skillswap.local/" + user.getEmail().split("@")[0].replace(".", "-"));
        return mentorProfileRepository.save(profile);
    }

    private MentorVerificationRequest ensureVerificationRequest(
            User mentor,
            VerificationStatus status,
            VerificationMethod method,
            String submittedNote,
            String reviewNote,
            int revisionCount,
            LocalDateTime submittedAt,
            LocalDateTime reviewedAt,
            LocalDateTime approvedAt,
            LocalDateTime withdrawnAt
    ) {
        List<VerificationStatus> allStatuses = List.of(VerificationStatus.values());
        MentorVerificationRequest request = mentorVerificationRequestRepository
                .findFirstByMentorIdAndStatusInOrderByCreatedAtDesc(mentor.getId(), allStatuses)
                .orElseGet(() -> MentorVerificationRequest.builder()
                        .mentor(mentor)
                        .method(method)
                        .build());
        request.setMentor(mentor);
        request.setMethod(method);
        request.setStatus(status);
        request.setSubmittedNote(submittedNote);
        request.setReviewNote(reviewNote);
        request.setRevisionCount(revisionCount);
        request.setSubmittedAt(submittedAt);
        request.setReviewedAt(reviewedAt);
        request.setApprovedAt(approvedAt);
        request.setWithdrawnAt(withdrawnAt);
        return mentorVerificationRequestRepository.save(request);
    }

    private void ensureVerificationDocumentBundle(
            MentorVerificationRequest request,
            User uploadedBy,
            VerificationDocumentStatus documentStatus,
            boolean markExpertiseRejected
    ) {
        if (!mentorVerificationDocumentRepository.findByRequestIdOrderByUploadedAtAsc(request.getId()).isEmpty()) {
            return;
        }

        StoredFile affiliationFile = ensureStoredFile(uploadedBy, request.getId(), "fptu-affiliation.png", "image/png", 245_000L);
        StoredFile expertiseFile = ensureStoredFile(uploadedBy, request.getId(), "expertise-proof.pdf", "application/pdf", 412_000L);

        mentorVerificationDocumentRepository.save(MentorVerificationDocument.builder()
                .request(request)
                .documentType(VerificationDocumentType.FPTU_AFFILIATION_PROOF)
                .status(documentStatus)
                .storageKind(VerificationStorageKind.IMAGE)
                .storedFile(affiliationFile)
                .isPrimary(true)
                .uploadedBy(uploadedBy)
                .reviewNote(markExpertiseRejected ? "Ảnh thẻ sinh viên cần rõ hơn." : null)
                .build());

        mentorVerificationDocumentRepository.save(MentorVerificationDocument.builder()
                .request(request)
                .documentType(VerificationDocumentType.EXPERTISE_PROOF)
                .status(markExpertiseRejected ? VerificationDocumentStatus.REJECTED : documentStatus)
                .storageKind(VerificationStorageKind.DOCUMENT)
                .storedFile(expertiseFile)
                .isPrimary(true)
                .uploadedBy(uploadedBy)
                .rejectedReason(markExpertiseRejected ? "Portfolio hiện chưa thể hiện rõ năng lực mentoring." : null)
                .build());
    }

    private StoredFile ensureStoredFile(User owner, UUID requestId, String originalName, String mimeType, long sizeBytes) {
        String storageKey = "seed/" + requestId + "/" + originalName;
        Optional<StoredFile> existing = storedFileRepository.findAll().stream()
                .filter(file -> storageKey.equals(file.getStorageKey()))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        return storedFileRepository.save(StoredFile.builder()
                .owner(owner)
                .purpose(FilePurpose.VERIFICATION_DOCUMENT)
                .originalName(originalName)
                .storageProvider("SEED")
                .storageKey(storageKey)
                .publicUrl("https://seed.skillswap.local/" + storageKey)
                .mimeType(mimeType)
                .sizeBytes(sizeBytes)
                .checksum("seed-" + UUID.nameUUIDFromBytes(storageKey.getBytes()))
                .build());
    }

    private void ensureEvent(
            MentorVerificationRequest request,
            MentorVerificationEventType eventType,
            User actor,
            VerificationStatus fromStatus,
            VerificationStatus toStatus,
            String note
    ) {
        boolean exists = mentorVerificationRequestEventRepository.findByRequestIdOrderByCreatedAtAsc(request.getId()).stream()
                .anyMatch(event -> event.getEventType() == eventType);
        if (exists) {
            return;
        }

        mentorVerificationRequestEventRepository.save(MentorVerificationRequestEvent.builder()
                .request(request)
                .eventType(eventType)
                .actorUser(actor)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .note(note)
                .build());
    }

    private void ensureMentorTags(MentorProfile mentorProfile, List<String> tagCodes, MentorTagType tagType) {
        if (tagCodes == null || tagCodes.isEmpty()) {
            return;
        }

        Stream.iterate(0, index -> index + 1)
                .limit(tagCodes.size())
                .forEach(index -> {
                    Tag tag = requiredTag(tagCodes.get(index));
                    MentorTagId id = new MentorTagId(mentorProfile.getUserId(), tag.getId(), tagType);
                    if (mentorTagRepository.existsById(id)) {
                        return;
                    }
                    mentorTagRepository.save(MentorTag.builder()
                            .id(id)
                            .mentorProfile(mentorProfile)
                            .tag(tag)
                            .proficiencyLevel(tagType == MentorTagType.EXPERTISE ? 4 : null)
                            .yearsOfExperience(tagType == MentorTagType.EXPERTISE ? decimal("2.0") : null)
                            .isPrimary(index == 0)
                            .build());
                });
    }

    private Tag ensureTag(String code, String nameVi, String nameEn, TagType type) {
        Tag tag = tagRepository.findByCode(code).orElseGet(() -> Tag.builder()
                .code(code)
                .build());
        tag.setNameVi(nameVi);
        tag.setNameEn(nameEn);
        tag.setType(type);
        tag.setStatus(TagStatus.ACTIVE);
        tag.setFixed(true);
        tag.setWeight(10);
        return tagRepository.save(tag);
    }

    private Tag requiredTag(String code) {
        return tagRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy tag seed với code: " + code));
    }

    private Campus requiredCampus(CampusCode code) {
        return campusRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy campus seed: " + code));
    }

    private AcademicProgram requiredProgram(String code) {
        return academicProgramRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy academic program seed: " + code));
    }

    private Specialization requiredSpecialization(String code) {
        return specializationRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy specialization seed: " + code));
    }

    private MentorProfileSeedSpec profileSpec(
            MentorStatus status,
            String headline,
            String bio,
            String currentPosition,
            String currentCompany,
            String industry,
            TeachingMode teachingMode,
            int sessionDuration,
            BigDecimal hourlyRate,
            BigDecimal yearsOfExperience,
            boolean available,
            LocalDateTime verifiedAt,
            double ratingAverage,
            int totalReviews,
            int completedSessions
    ) {
        return new MentorProfileSeedSpec(
                status,
                headline,
                bio,
                currentPosition,
                currentCompany,
                industry,
                teachingMode,
                sessionDuration,
                hourlyRate,
                yearsOfExperience,
                available,
                verifiedAt,
                ratingAverage,
                totalReviews,
                completedSessions
        );
    }

    private MentorProfileSeedSpec mentorSpec(
            String headline,
            String currentPosition,
            String currentCompany,
            String industry,
            TeachingMode teachingMode,
            int sessionDuration,
            String hourlyRate,
            String yearsOfExperience,
            boolean available,
            double ratingAverage,
            int totalReviews,
            int completedSessions,
            long activeDays
    ) {
        return new MentorProfileSeedSpec(
                MentorStatus.ACTIVE,
                headline,
                null,
                currentPosition,
                currentCompany,
                industry,
                teachingMode,
                sessionDuration,
                decimal(hourlyRate),
                decimal(yearsOfExperience),
                available,
                LocalDateTime.now().minusDays(activeDays),
                ratingAverage,
                totalReviews,
                completedSessions
        );
    }

    private static String avatar(String seed) {
        return "https://api.dicebear.com/9.x/initials/svg?seed=" + seed.replace(" ", "");
    }

    private static String generatedStudentCode(String email, boolean alumni) {
        String prefix = alumni ? "ALUMNI" : "SE";
        return prefix + Math.abs(email.hashCode() % 1000000);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private record MentorProfileSeedSpec(
            MentorStatus status,
            String headline,
            String bio,
            String currentPosition,
            String currentCompany,
            String industry,
            TeachingMode teachingMode,
            int sessionDuration,
            BigDecimal hourlyRate,
            BigDecimal yearsOfExperience,
            boolean available,
            LocalDateTime verifiedAt,
            double ratingAverage,
            int totalReviews,
            int completedSessions
    ) {
    }
}
