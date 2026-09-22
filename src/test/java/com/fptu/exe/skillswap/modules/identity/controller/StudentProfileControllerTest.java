package com.fptu.exe.skillswap.modules.identity.controller;

import com.fptu.exe.skillswap.infrastructure.security.UserPrincipal;
import com.fptu.exe.skillswap.modules.identity.domain.StudentProfileType;
import com.fptu.exe.skillswap.modules.identity.dto.response.StudentProfileResponse;
import com.fptu.exe.skillswap.modules.identity.service.AcademicService;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class StudentProfileControllerTest {
    @Autowired MockMvc mvc;
    @MockBean AcademicService academicService;

    @Test void profileReadAndWriteAreOwnedByAuthenticatedPrincipal() throws Exception {
        UUID userId=UUID.randomUUID();
        UserPrincipal principal=UserPrincipal.create(userId,"mentee@example.test",List.of(RoleCode.MENTEE));
        when(academicService.updateStudentProfile(eq(userId),any())).thenReturn(StudentProfileResponse.builder().id(userId).userId(userId).profileType(StudentProfileType.SCHOOL_STUDENT).onboardingCompleted(true).build());
        mvc.perform(put("/api/me/student-profile").with(authentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities())))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"profileType":"SCHOOL_STUDENT","customInstitutionName":"Example school","customInstitutionProvinceId":"%s"}
                    """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(userId.toString()));
        verify(academicService).updateStudentProfile(eq(userId),any());
    }

    @Test void profileEndpointRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/me/student-profile")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/me/student-profile").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(academicService);
    }

    @Test void incompleteProfileReadPreservesNullTypeAndPrivacy() throws Exception {
        UUID userId=UUID.randomUUID();
        UserPrincipal principal=UserPrincipal.create(userId,"mentee@example.test",List.of(RoleCode.MENTEE));
        when(academicService.getStudentProfile(userId)).thenReturn(StudentProfileResponse.builder()
                .userId(userId).profileType(null).onboardingCompleted(false)
                .bio("legacy bio").build());

        mvc.perform(get("/api/me/student-profile").with(authentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileType").doesNotExist())
                .andExpect(jsonPath("$.data.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.data.bio").value("legacy bio"))
                .andExpect(jsonPath("$.data.institutionId").doesNotExist())
                .andExpect(jsonPath("$.data.fieldGroupId").doesNotExist())
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.displayName").doesNotExist())
                .andExpect(jsonPath("$.data.avatarUrl").doesNotExist());
    }

    @Test void openApiExposesCanonicalProfileTypeWithoutLegacyFields() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.StudentProfileRequest.properties.profileType.enum[0]").value("SCHOOL_STUDENT"))
                .andExpect(jsonPath("$.components.schemas.StudentProfileResponse.properties.profileType.nullable").value(true))
                .andExpect(jsonPath("$.components.schemas.StudentProfileResponse.properties.profileType.enum[0]").value("SCHOOL_STUDENT"))
                .andExpect(jsonPath("$.components.schemas.StudentProfileResponse.properties.onboardingCompleted").exists());
    }
}
