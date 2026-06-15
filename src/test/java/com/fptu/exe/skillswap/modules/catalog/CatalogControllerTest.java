package com.fptu.exe.skillswap.modules.catalog;

import com.fptu.exe.skillswap.modules.catalog.dto.HelpTopicResponse;
import com.fptu.exe.skillswap.modules.catalog.service.CatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CatalogService catalogService;

    @Test
    void getHelpTopics_shouldReturnPublicSeededList() throws Exception {
        when(catalogService.getHelpTopics()).thenReturn(List.of(
                HelpTopicResponse.builder()
                        .id(UUID.randomUUID())
                        .code("HELP_CV_REVIEW")
                        .nameVi("Đánh giá CV")
                        .nameEn("CV Review")
                        .weight(100)
                        .build()
        ));

        mockMvc.perform(get("/api/catalog/help-topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_0200"))
                .andExpect(jsonPath("$.data[0].code").value("HELP_CV_REVIEW"))
                .andExpect(jsonPath("$.data[0].nameVi").value("Đánh giá CV"));
    }
}
