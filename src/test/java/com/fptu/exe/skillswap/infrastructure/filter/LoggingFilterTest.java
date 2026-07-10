package com.fptu.exe.skillswap.infrastructure.filter;

import com.fptu.exe.skillswap.shared.dto.response.ApiResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.GlobalExceptionHandler;
import com.fptu.exe.skillswap.shared.util.TraceContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoggingFilterTest {

    @Test
    void shouldGenerateTraceIdAndAttachItToErrorResponse() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ErrorController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter(new LoggingFilter())
                .build();

        mockMvc.perform(get("/__phase1/error").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists(TraceContext.TRACE_ID_HEADER))
                .andExpect(jsonPath("$.code", is(ErrorCode.BAD_REQUEST.getCode())));

        assertNull(TraceContext.getCurrentTraceId());
    }

    @Test
    void shouldReuseIncomingTraceId() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new OkController())
                .addFilter(new LoggingFilter())
                .build();

        mockMvc.perform(get("/__phase1/ok").header(TraceContext.TRACE_ID_HEADER, "trace-abc-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceContext.TRACE_ID_HEADER, "trace-abc-123"));
    }

    @RestController
    static class ErrorController {
        @GetMapping("/__phase1/error")
        ApiResponse<String> error() {
            throw new BaseException(ErrorCode.BAD_REQUEST, "phase1 error");
        }
    }

    @RestController
    static class OkController {
        @GetMapping("/__phase1/ok")
        String ok() {
            return "ok";
        }
    }
}
