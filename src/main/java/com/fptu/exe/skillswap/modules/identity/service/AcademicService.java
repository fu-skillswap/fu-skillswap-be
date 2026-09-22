package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.identity.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.identity.domain.StudentProfileType;
import com.fptu.exe.skillswap.modules.identity.dto.request.StudentProfileRequest;
import com.fptu.exe.skillswap.modules.identity.dto.response.StudentProfileResponse;
import com.fptu.exe.skillswap.modules.identity.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.port.AcademicEligibilityQuery;
import com.fptu.exe.skillswap.shared.event.ProfileStatusQuery;
import com.fptu.exe.skillswap.shared.event.UserDeletedEvent;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fptu.exe.skillswap.shared.time.TimeProvider;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Clock;
import java.time.Year;
import java.util.UUID;
import com.fptu.exe.skillswap.modules.catalog.repository.AdministrativeProvinceRepository;
import com.fptu.exe.skillswap.modules.catalog.repository.EducationalInstitutionRepository;
import com.fptu.exe.skillswap.modules.catalog.repository.EducationFieldGroupRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class AcademicService implements AcademicEligibilityQuery {

    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;
    private final AdministrativeProvinceRepository provinceRepository;
    private final EducationalInstitutionRepository institutionRepository;
    private final EducationFieldGroupRepository fieldGroupRepository;
    private TimeProvider timeProvider = TimeProvider.from(Clock.systemUTC());

    @Autowired(required = false)
    void setTimeProvider(TimeProvider timeProvider) {
        if (timeProvider != null) {
            this.timeProvider = timeProvider;
        }
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse getStudentProfile(UUID userId) {
        requireId(userId, "Người dùng");
        StudentProfile profile = studentProfileRepository.findWithDetailsByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ học thuật chưa được tạo"));
        return mapToStudentProfileResponse(profile);
    }

    @Transactional(readOnly = true)
    @Override
    public boolean hasCompletedStudentProfile(UUID userId) {
        requireId(userId, "Người dùng");
        return studentProfileRepository.findWithDetailsByUserId(userId)
                .map(this::isProfileCompleted)
                .orElse(false);
    }

    @Transactional
    public StudentProfileResponse updateStudentProfile(UUID userId, StudentProfileRequest request) {
        requireId(userId, "Người dùng");
        requireStudentProfileRequest(request);

        StudentProfile profile = studentProfileRepository.findById(userId).orElse(null);
        if (request.getProfileType() == null) throw new BaseException(ErrorCode.BAD_REQUEST, "profileType is required");

        validateNewProfileRequest(request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        if (profile == null) {
            profile = new StudentProfile();
            profile.setId(userId);
            profile.setUser(user);
        }

        // The request is fully validated above before changing the discriminator or completion state.
        profile.setProfileType(request.getProfileType());
        profile.setInstitutionId(request.getInstitutionId());
        profile.setCustomInstitutionName(clean(request.getCustomInstitutionName()));
        profile.setCustomInstitutionProvinceId(request.getInstitutionId() == null ? request.getCustomInstitutionProvinceId() : null);
        profile.setFieldGroupId(request.getFieldGroupId());
        profile.setMajorName(clean(request.getMajorName()));
        profile.setEnrollmentYear(request.getEnrollmentYear());
        profile.setGraduationYear(request.getGraduationYear());
        profile.setBio(request.getBio());
        profile.setOnboardingCompleted(true);
        profile.setOnboardingCompletedAt(timeProvider.nowBusiness());

        StudentProfile savedProfile = studentProfileRepository.save(profile);
        return mapToStudentProfileResponse(savedProfile);
    }

    /**
     * Lắng nghe ProfileStatusQuery từ module identity.
     * Set kết quả hasStudentProfile vào event object (synchronous request-reply pattern).
     */
    @EventListener
    public void onProfileStatusQuery(ProfileStatusQuery query) {
        query.setHasStudentProfile(hasCompletedStudentProfile(query.getUserId()));
    }

    private StudentProfileResponse mapToStudentProfileResponse(StudentProfile profile) {
        return StudentProfileResponse.builder()
                .id(profile.getId())
                .userId(profile.getUserId())
                .profileType(profile.getProfileType())
                .institutionId(profile.getInstitutionId())
                .customInstitutionName(profile.getCustomInstitutionName())
                .customInstitutionProvinceId(profile.getCustomInstitutionProvinceId())
                .fieldGroupId(profile.getFieldGroupId())
                .majorName(profile.getMajorName())
                .enrollmentYear(profile.getEnrollmentYear())
                .onboardingCompleted(profile.isOnboardingComplete())
                .onboardingCompletedAt(profile.getOnboardingCompletedAt())
                .bio(profile.getBio())
                .graduationYear(profile.getGraduationYear())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private void requireId(UUID id, String label) {
        if (id == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, label + " không được để trống");
        }
    }

    private void requireStudentProfileRequest(StudentProfileRequest request) {
        if (request == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Dữ liệu hồ sơ học thuật không được để trống");
        }
    }

    private boolean isProfileCompleted(StudentProfile profile) {
        return profile != null && profile.isOnboardingComplete();
    }

    private void validateNewProfileRequest(StudentProfileRequest request) {
        if (request.getUnsupportedFields() != null && !request.getUnsupportedFields().isEmpty()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Field không được hỗ trợ trong hồ sơ mới: " + request.getUnsupportedFields().keySet());
        }
        validateLength(request.getCustomInstitutionName(), 200, "customInstitutionName");
        validateLength(request.getMajorName(), 200, "majorName");
        validateCurrentModelYear(request.getEnrollmentYear(), "enrollmentYear");
        validateCurrentModelYear(request.getGraduationYear(), "graduationYear");
        switch (request.getProfileType()) {
            case SCHOOL_STUDENT -> {
                requireText(request.getCustomInstitutionName(), "customInstitutionName");
                requireId(request.getCustomInstitutionProvinceId(), "customInstitutionProvinceId");
                reject(request.getInstitutionId(), "institutionId", request.getFieldGroupId(), "fieldGroupId", request.getMajorName(), "majorName", request.getEnrollmentYear(), "enrollmentYear", request.getGraduationYear(), "graduationYear");
                if (provinceRepository.findById(request.getCustomInstitutionProvinceId()).filter(com.fptu.exe.skillswap.modules.catalog.domain.AdministrativeProvince::isActive).isEmpty()) throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy tỉnh/thành đang hoạt động");
            }
            case UNIVERSITY_STUDENT, ALUMNI -> {
                boolean catalog = request.getInstitutionId() != null;
                boolean customName = request.getCustomInstitutionName() != null && !request.getCustomInstitutionName().isBlank();
                boolean customProvince = request.getCustomInstitutionProvinceId() != null;
                if (catalog == (customName || customProvince)) throw new BaseException(ErrorCode.BAD_REQUEST, "Chọn đúng một trường trong danh mục hoặc nhập tên trường cùng tỉnh/thành");
                if (customName != customProvince) throw new BaseException(ErrorCode.BAD_REQUEST, "Trường tự nhập cần cả tên trường và tỉnh/thành");
                if (catalog && (customName || customProvince)) throw new BaseException(ErrorCode.BAD_REQUEST, "Không gửi trường tự nhập khi đã chọn institutionId");
                if (catalog && institutionRepository.findById(request.getInstitutionId()).filter(com.fptu.exe.skillswap.modules.catalog.domain.EducationalInstitution::isActive).isEmpty()) throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy trường đang hoạt động");
                if (customProvince && provinceRepository.findById(request.getCustomInstitutionProvinceId()).filter(com.fptu.exe.skillswap.modules.catalog.domain.AdministrativeProvince::isActive).isEmpty()) throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy tỉnh/thành đang hoạt động");
                requireId(request.getFieldGroupId(), "fieldGroupId");
                requireText(request.getMajorName(), "majorName");
                if (fieldGroupRepository.findById(request.getFieldGroupId()).filter(com.fptu.exe.skillswap.modules.catalog.domain.EducationFieldGroup::isActive).isEmpty()) throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy nhóm ngành đang hoạt động");
                if (request.getProfileType() == StudentProfileType.UNIVERSITY_STUDENT && request.getGraduationYear() != null) throw new BaseException(ErrorCode.BAD_REQUEST, "graduationYear phải để trống với UNIVERSITY_STUDENT");
                if (request.getProfileType() == StudentProfileType.ALUMNI && request.getGraduationYear() == null) throw new BaseException(ErrorCode.BAD_REQUEST, "ALUMNI bắt buộc có graduationYear");
            }
        }
    }

    private void requireText(String value, String field) { if (value == null || value.isBlank()) throw new BaseException(ErrorCode.BAD_REQUEST, field + " không được để trống"); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private void requireValue(Integer value, String field) { if (value == null) throw new BaseException(ErrorCode.BAD_REQUEST, field + " không được để trống"); }
    private void reject(Object a, String aName, Object b, String bName, Object c, String cName, Object d, String dName, Object e, String eName) {
        if (a != null) throw new BaseException(ErrorCode.BAD_REQUEST, aName + " không tương thích với profileType");
        if (b != null) throw new BaseException(ErrorCode.BAD_REQUEST, bName + " không tương thích với profileType");
        if (c != null) throw new BaseException(ErrorCode.BAD_REQUEST, cName + " không tương thích với profileType");
        if (d != null) throw new BaseException(ErrorCode.BAD_REQUEST, dName + " không tương thích với profileType");
        if (e != null) throw new BaseException(ErrorCode.BAD_REQUEST, eName + " không tương thích với profileType");
    }

    private void validateCurrentModelYear(Integer year, String field) {
        if (year != null && (year < 1900 || year > 2200)) {
            throw new BaseException(ErrorCode.BAD_REQUEST, field + " phải nằm trong khoảng 1900 đến 2200");
        }
        if (year != null && year > Year.now().getValue()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, field + " không được lớn hơn năm hiện tại");
        }
    }

    private void validateLength(String value, int max, String field) {
        if (value != null && value.length() > max) {
            throw new BaseException(ErrorCode.BAD_REQUEST, field + " không được vượt quá " + max + " ký tự");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @EventListener
    @Transactional
    public void onUserDeleted(UserDeletedEvent event) {
        log.info("Academic module: Handling UserDeletedEvent for user: {}", event.getUserId());
        if (studentProfileRepository.existsById(event.getUserId())) {
            studentProfileRepository.deleteById(event.getUserId());
            log.info("Deleted StudentProfile for user: {}", event.getUserId());
        }
    }
}
