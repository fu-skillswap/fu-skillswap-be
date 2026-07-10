package com.fptu.exe.skillswap.modules.academic;

import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.modules.catalog.dto.response.HelpTopicResponse;
import com.fptu.exe.skillswap.modules.catalog.service.CatalogService;
import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorProfileOptionsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MasterDataControllerCacheHeaderTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AcademicService academicService;

    @MockBean
    private CatalogService catalogService;

    @Test
    void academicCatalogEndpoints_shouldReturnLongLivedCacheHeader() throws Exception {
        when(academicService.getAllCampuses()).thenReturn(List.of());
        when(academicService.getAllAcademicPrograms()).thenReturn(List.of());
        when(academicService.getAllSpecializations()).thenReturn(List.of());
        when(academicService.getSpecializationsByProgram(UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6")))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/campuses"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=86400"));

        mockMvc.perform(get("/api/academic-programs"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=86400"));

        mockMvc.perform(get("/api/specializations"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=86400"));

        mockMvc.perform(get("/api/academic-programs/3fa85f64-5717-4562-b3fc-2c963f66afa6/specializations"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=86400"));
    }

    @Test
    void catalogEndpoints_shouldReturnLongLivedCacheHeader() throws Exception {
        when(catalogService.getHelpTopics()).thenReturn(List.<HelpTopicResponse>of());
        when(catalogService.getMentorProfileOptions()).thenReturn(MentorProfileOptionsResponse.builder()
                .foundationSupportLevels(List.of())
                .outputReviewSupportLevels(List.of())
                .directionSupportLevels(List.of())
                .build());

        mockMvc.perform(get("/api/catalog/help-topics"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=86400"));

        mockMvc.perform(get("/api/catalog/mentor-profile-options"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=86400"));
    }
}
