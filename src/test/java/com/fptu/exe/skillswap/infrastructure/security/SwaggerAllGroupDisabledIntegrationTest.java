package com.fptu.exe.skillswap.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "application.swagger.enabled=true",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",
        "application.openapi.version=0.1.0-beta",
        "application.openapi.include-all-group=false",
        "application.openapi.warmup.async=false",
        "application.openapi.warmup.initial-delay-ms=0",
        "application.openapi.warmup.group-delay-ms=0"
})
@AutoConfigureMockMvc
class SwaggerAllGroupDisabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "MENTEE")
    void allGroup_shouldReturn404WhenDisabled() throws Exception {
        mockMvc.perform(get("/v3/api-docs/00-all"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "MENTEE")
    void otherGroups_shouldStillReturnHttp200WhenAllGroupDisabled() throws Exception {
        List<String> groups = List.of(
                "01-identity",
                "02-mentor",
                "03-booking",
                "04-community",
                "05-admin",
                "06-integration",
                "07-internal"
        );

        for (String group : groups) {
            mockMvc.perform(get("/v3/api-docs/" + group))
                    .andExpect(status().isOk());
        }
    }
}
