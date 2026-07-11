package com.fptu.exe.skillswap.infrastructure.config;

import com.fptu.exe.skillswap.shared.util.TraceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase1RuntimeFoundationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("notificationExecutor")
    private TaskExecutor notificationExecutor;

    @Test
    void shouldExposeHealthProbes() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldPropagateTraceIdThroughAsyncExecutor() throws Exception {
        try {
            TraceContext.setCurrentTraceId("phase1-trace");
            CompletableFuture<String> future = new CompletableFuture<>();
            notificationExecutor.execute(() -> future.complete(TraceContext.getCurrentTraceId()));
            assertEquals("phase1-trace", future.get(5, TimeUnit.SECONDS));
        } finally {
            TraceContext.clear();
        }
    }
}
