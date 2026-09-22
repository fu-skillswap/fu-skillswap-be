package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.catalog.domain.AdministrativeProvince;
import com.fptu.exe.skillswap.modules.catalog.domain.EducationalInstitution;
import com.fptu.exe.skillswap.modules.catalog.domain.EducationFieldGroup;
import com.fptu.exe.skillswap.modules.catalog.repository.*;
import com.fptu.exe.skillswap.modules.identity.domain.*;
import com.fptu.exe.skillswap.modules.identity.dto.request.StudentProfileRequest;
import com.fptu.exe.skillswap.modules.identity.repository.*;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcademicServiceTest {
    @Mock StudentProfileRepository studentProfileRepository;
    @Mock UserRepository userRepository;
    @Mock AdministrativeProvinceRepository provinceRepository;
    @Mock EducationalInstitutionRepository institutionRepository;
    @Mock EducationFieldGroupRepository fieldGroupRepository;
    @InjectMocks AcademicService academicService;
    private UUID userId, provinceId, institutionId, fieldGroupId;
    private User user;
    private StudentProfileRequest request;
    private AdministrativeProvince province;
    private EducationalInstitution institution;
    private EducationFieldGroup fieldGroup;

    @BeforeEach void setUp() {
        userId=UUID.randomUUID(); provinceId=UUID.randomUUID(); institutionId=UUID.randomUUID(); fieldGroupId=UUID.randomUUID();
        user=User.builder().id(userId).email("student@test.com").fullName("Student").build();
        province=AdministrativeProvince.builder().id(provinceId).code("01").name("Hà Nội").normalizedName("ha noi").active(true).build();
        institution=EducationalInstitution.builder().id(institutionId).slug("hust").name("Đại học Bách khoa Hà Nội").province(province).active(true).build();
        fieldGroup=EducationFieldGroup.builder().id(fieldGroupId).officialCode("748").name("Máy tính và công nghệ thông tin").active(true).build();
        request=StudentProfileRequest.builder().profileType(StudentProfileType.UNIVERSITY_STUDENT).institutionId(institutionId).fieldGroupId(fieldGroupId).majorName("Khoa học máy tính").build();
        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        lenient().when(institutionRepository.findById(institutionId)).thenReturn(Optional.of(institution));
        lenient().when(fieldGroupRepository.findById(fieldGroupId)).thenReturn(Optional.of(fieldGroup));
        lenient().when(provinceRepository.findById(provinceId)).thenReturn(Optional.of(province));
        lenient().when(studentProfileRepository.save(any(StudentProfile.class))).thenAnswer(i->i.getArgument(0));
    }

    @Test void universityStudentDoesNotRequireStudentIdentifierAndDoesNotCopyUserFields() {
        when(studentProfileRepository.findById(userId)).thenReturn(Optional.empty());
        var response=academicService.updateStudentProfile(userId,request);
        assertEquals(StudentProfileType.UNIVERSITY_STUDENT,response.getProfileType());
        assertTrue(response.isOnboardingCompleted());
        verify(userRepository,never()).save(any());
    }

    @Test void alumniRequiresGraduationYear() {
        request.setProfileType(StudentProfileType.ALUMNI);
        BaseException error=assertThrows(BaseException.class,()->academicService.updateStudentProfile(userId,request));
        assertTrue(error.getMessage().contains("graduationYear"));
        verify(studentProfileRepository,never()).save(any());
    }

    @Test void schoolStudentRejectsHigherEducationFields() {
        request.setProfileType(StudentProfileType.SCHOOL_STUDENT);
        request.setCustomInstitutionName("THPT Example"); request.setCustomInstitutionProvinceId(provinceId);
        BaseException error=assertThrows(BaseException.class,()->academicService.updateStudentProfile(userId,request));
        assertTrue(error.getMessage().contains("institutionId"));
    }

    @Test void customInstitutionRequiresProvince() {
        request.setInstitutionId(null); request.setCustomInstitutionName("Trường mới");
        BaseException error=assertThrows(BaseException.class,()->academicService.updateStudentProfile(userId,request));
        assertTrue(error.getMessage().contains("cả tên trường"));
    }

    @Test void catalogInstitutionRejectsCustomProvince() {
        request.setCustomInstitutionProvinceId(provinceId);
        BaseException error=assertThrows(BaseException.class,()->academicService.updateStudentProfile(userId,request));
        assertTrue(error.getMessage().contains("đúng một"));
    }

    @Test void profileTypeSwitchReplacesIncompatibleFields() {
        StudentProfile existing=new StudentProfile(); existing.setUserId(userId); existing.setId(userId); existing.setUser(user);
        existing.setProfileType(StudentProfileType.SCHOOL_STUDENT); existing.setCustomInstitutionName("Old school"); existing.setCustomInstitutionProvinceId(provinceId);
        when(studentProfileRepository.findById(userId)).thenReturn(Optional.of(existing));
        request.setEnrollmentYear(2022);
        var response=academicService.updateStudentProfile(userId,request);
        assertEquals(StudentProfileType.UNIVERSITY_STUDENT,response.getProfileType());
        assertNull(response.getCustomInstitutionName()); assertNull(response.getCustomInstitutionProvinceId());
        assertEquals(institutionId,response.getInstitutionId());
    }

    @Test void unsupportedAdditionalFieldsAreRejected() {
        request.getUnsupportedFields().put("unexpectedField",5);
        BaseException error=assertThrows(BaseException.class,()->academicService.updateStudentProfile(userId,request));
        assertTrue(error.getMessage().contains("unexpectedField"));
    }

    @Test void profileTypeNullIsIncompleteEvenWhenCompletionFlagIsSet() {
        StudentProfile legacy=StudentProfile.builder().id(userId).userId(userId).user(user).profileType(null).onboardingCompleted(true).build();
        when(studentProfileRepository.findWithDetailsByUserId(userId)).thenReturn(Optional.of(legacy));
        assertFalse(academicService.hasCompletedStudentProfile(userId));
    }

}
