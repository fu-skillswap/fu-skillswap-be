package com.fptu.exe.skillswap.modules.identity.controller;

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
    private CatalogService catalogService;

    @Test
    void mentorProfileOptions_shouldReturnLongLivedCacheHeader() throws Exception {
        when(catalogService.getMentorProfileOptions()).thenReturn(MentorProfileOptionsResponse.builder()
                .foundationSupportLevels(List.of())
                .outputReviewSupportLevels(List.of())
                .directionSupportLevels(List.of())
                .build());

        mockMvc.perform(get("/api/catalog/mentor-profile-options"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=86400"));
    }
}
