package com.fptu.exe.skillswap.infrastructure.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "application.swagger.enabled=true",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true",
        "application.openapi.version=0.1.0-beta",
        "application.openapi.include-all-group=true",
        "application.openapi.warmup.async=false",
        "application.openapi.warmup.initial-delay-ms=0",
        "application.openapi.warmup.group-delay-ms=0"
})
@AutoConfigureMockMvc
class SwaggerExposureEnabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "MENTEE")
    void apiDocs_shouldExposeLeanAndCompleteContractWhenSwaggerEnabled() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode document = objectMapper.readTree(json);
        java.nio.file.Path targetDir = java.nio.file.Path.of("target");
        java.nio.file.Files.createDirectories(targetDir);
        java.nio.file.Files.writeString(targetDir.resolve("openapi.json"), json);

        assertThat(document.path("openapi").asText()).startsWith("3.");
        assertThat(document.path("info").path("title").asText()).isEqualTo("SkillSwap API");
        assertThat(document.path("info").path("version").asText())
                .matches("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?$");
        assertThat(document.path("components").path("securitySchemes").has("bearerAuth")).isTrue();
        assertThat(document.path("components").path("schemas").has("ApiResponseObject")).isTrue();
        assertThat(document.path("components").path("schemas").has("ValidationErrorResponse")).isTrue();
        assertThat(java.util.Arrays.stream(ErrorCode.values()).anyMatch(error -> error.getStatus() == 422)).isTrue();
        assertThat(document.path("components").path("responses").has("UnprocessableEntity")).isTrue();
        java.util.Set<String> schemaNames = new java.util.HashSet<>();
        document.path("components").path("schemas").fieldNames().forEachRemaining(schemaNames::add);
        assertThat(schemaNames.stream().anyMatch(name -> name.startsWith("PageResponse"))).isTrue();
        assertThat(schemaNames.stream().anyMatch(name -> name.startsWith("CursorPageResponse"))).isTrue();

        JsonNode mentorDetailSchema = document.path("components").path("schemas")
                .path("MentorDiscoveryDetailResponse");
        java.util.Set<String> mentorDetailSections = new java.util.LinkedHashSet<>();
        mentorDetailSchema.path("properties").fieldNames().forEachRemaining(mentorDetailSections::add);
        assertThat(mentorDetailSections).containsExactly(
                "identity", "mentoring", "services", "evidence", "reputation", "availability");

        JsonNode reputationSchema = document.path("components").path("schemas")
                .path("MentorReputationResponse");
        assertThat(reputationSchema.path("properties").path("ratingState").path("enum"))
                .extracting(JsonNode::asText)
                .containsExactly("NO_REVIEWS", "RATED");
        assertThat(reputationSchema.path("properties").path("ratingAverage").path("nullable").asBoolean())
                .isTrue();

        JsonNode tags = document.path("tags");
        assertThat(tags.isArray()).isTrue();
        java.util.Set<String> tagNames = new java.util.HashSet<>();
        for (JsonNode tag : tags) {
            String tagName = tag.path("name").asText();
            assertThat(tagName).isNotBlank().isNotEqualTo("default");
            assertThat(tagNames.add(tagName)).as("duplicate OpenAPI tag: %s", tagName).isTrue();
        }

        document.path("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(operation -> {
            if (!java.util.Set.of("get", "post", "put", "patch", "delete", "head", "options").contains(operation.getKey())) {
                return;
            }
            JsonNode operationNode = operation.getValue();
            assertThat(operationNode.path("summary").asText())
                    .as("missing summary for %s %s", operation.getKey().toUpperCase(), path.getKey())
                    .isNotBlank();
            JsonNode operationTags = operationNode.path("tags");
            assertThat(operationTags.isArray() && !operationTags.isEmpty())
                    .as("missing tag for %s %s", operation.getKey().toUpperCase(), path.getKey())
                    .isTrue();
            for (JsonNode operationTag : operationTags) {
                assertThat(tagNames).contains(operationTag.asText());
            }
        }));

        JsonNode refresh = document.path("paths").path("/api/auth/refresh").path("post");
        assertThat(refresh.has("requestBody")).isFalse();
        assertThat(refresh.path("responses").path("200").path("headers").has("Set-Cookie")).isTrue();
        JsonNode login = document.path("paths").path("/api/auth/google").path("post");
        assertThat(login.path("responses").path("200").path("headers").has("Set-Cookie")).isTrue();

    }

    @Test
    @WithMockUser(roles = "MENTEE")
    void mentorDocs_shouldOnlyShowTheMentorFlow() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs/02-mentor"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        JsonNode document = objectMapper.readTree(json);

        assertThat(document.path("paths").has("/api/me/mentor-profile")).isTrue();
        assertThat(document.path("paths").has("/api/me/mentor-verification/request")).isTrue();
        assertThat(document.path("paths").has("/api/admin/mentor-verification/requests")).isFalse();

        java.util.Set<String> tagNames = new java.util.LinkedHashSet<>();
        document.path("tags").forEach(tag -> tagNames.add(tag.path("name").asText()));
        assertThat(tagNames).contains("Hồ sơ mentor", "Đăng ký mentor");
    }

    @Test
    @WithMockUser(roles = "MENTEE")
    void allConfiguredOpenApiGroups_shouldReturnHttp200AndValidContract() throws Exception {
        java.util.List<String> groups = java.util.List.of(
                "00-all",
                "01-identity",
                "02-mentor",
                "03-booking",
                "04-community",
                "05-admin",
                "06-integration",
                "07-internal"
        );

        for (String group : groups) {
            long startTime = System.currentTimeMillis();
            String json = mockMvc.perform(get("/v3/api-docs/" + group))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            long durationMs = System.currentTimeMillis() - startTime;

            assertThat(durationMs)
                    .as("OpenAPI generation for group %s exceeded 10 seconds: %d ms", group, durationMs)
                    .isLessThan(10_000L);

            JsonNode document = objectMapper.readTree(json);
            assertThat(document.path("openapi").asText())
                    .as("group %s should produce valid OpenAPI 3.x document", group)
                    .startsWith("3.");
            assertThat(document.path("paths").isObject())
                    .as("group %s should have paths object", group)
                    .isTrue();
            assertThat(document.path("paths").size())
                    .as("group %s should contain at least one mapped path", group)
                    .isGreaterThan(0);
        }
    }
}
