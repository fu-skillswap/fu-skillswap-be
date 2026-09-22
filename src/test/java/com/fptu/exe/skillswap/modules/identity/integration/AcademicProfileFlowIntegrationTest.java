package com.fptu.exe.skillswap.modules.identity.integration;

import com.fptu.exe.skillswap.modules.catalog.repository.*;
import com.fptu.exe.skillswap.modules.identity.domain.*;
import com.fptu.exe.skillswap.modules.identity.dto.request.StudentProfileRequest;
import com.fptu.exe.skillswap.modules.identity.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.identity.service.AcademicService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @Transactional
class AcademicProfileFlowIntegrationTest {
    @Autowired UserRepository userRepository;
    @Autowired AcademicService academicService;
    @Autowired StudentProfileRepository profileRepository;
    @Autowired AdministrativeProvinceRepository provinceRepository;
    @Autowired EducationalInstitutionRepository institutionRepository;
    @Autowired EducationFieldGroupRepository fieldGroupRepository;
    private User user;
    @BeforeEach void setUp(){ user=userRepository.save(User.builder().email("academic-flow@test.com").fullName("Academic Flow User").status(UserStatus.ACTIVE).build()); }

    @Test void schoolUniversityAndAlumniProfilesAreSupportedAndSwitchCleanly(){
        var hanoi=provinceRepository.findByCode("01").orElseThrow();
        var hust=institutionRepository.findBySlug("hust").orElseThrow();
        var field=fieldGroupRepository.findByOfficialCode("748").orElseThrow();
        var school=academicService.updateStudentProfile(user.getId(),StudentProfileRequest.builder().profileType(StudentProfileType.SCHOOL_STUDENT).customInstitutionName("THPT Example").customInstitutionProvinceId(hanoi.getId()).build());
        assertEquals(StudentProfileType.SCHOOL_STUDENT,school.getProfileType());
        var university=academicService.updateStudentProfile(user.getId(),StudentProfileRequest.builder().profileType(StudentProfileType.UNIVERSITY_STUDENT).institutionId(hust.getId()).fieldGroupId(field.getId()).majorName("Khoa học máy tính").enrollmentYear(2024).build());
        assertEquals(StudentProfileType.UNIVERSITY_STUDENT,university.getProfileType()); assertNull(university.getCustomInstitutionName()); assertNull(university.getCustomInstitutionProvinceId());
        var alumni=academicService.updateStudentProfile(user.getId(),StudentProfileRequest.builder().profileType(StudentProfileType.ALUMNI).institutionId(hust.getId()).fieldGroupId(field.getId()).majorName("Khoa học máy tính").graduationYear(2025).build());
        assertEquals(StudentProfileType.ALUMNI,alumni.getProfileType()); assertEquals(2025,alumni.getGraduationYear());
        var stored=profileRepository.findById(user.getId()).orElseThrow(); assertTrue(stored.isOnboardingCompleted()); assertNull(stored.getCustomInstitutionName());
        assertTrue(academicService.hasCompletedStudentProfile(user.getId()));
    }

    @Test void newOnboardingUsesCanonicalProfileData(){
        var province=provinceRepository.findByCode("01").orElseThrow();
        var response=academicService.updateStudentProfile(user.getId(),StudentProfileRequest.builder().profileType(StudentProfileType.SCHOOL_STUDENT).customInstitutionName("School").customInstitutionProvinceId(province.getId()).build());
        assertTrue(response.isOnboardingCompleted());
    }

    @Test void unknownAndIncompatibleFieldsAreRejected(){
        var province=provinceRepository.findByCode("01").orElseThrow();
        var request=StudentProfileRequest.builder().profileType(StudentProfileType.SCHOOL_STUDENT).customInstitutionName("School").customInstitutionProvinceId(province.getId()).build();
        request.getUnsupportedFields().put("majorName","should not be accepted through unknown legacy input");
        assertThrows(BaseException.class,()->academicService.updateStudentProfile(user.getId(),request));
        assertTrue(profileRepository.findById(user.getId()).isEmpty());
    }
}
