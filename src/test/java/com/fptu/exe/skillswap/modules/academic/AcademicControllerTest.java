package com.fptu.exe.skillswap.modules.academic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.academic.repository.AcademicProgramRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AcademicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AcademicProgramRepository academicProgramRepository;

    @Test
    void getCampuses_shouldReturnSeededList() throws Exception {
        mockMvc.perform(get("/api/campuses")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_0200"))
                .andExpect(jsonPath("$.data", hasSize(5)))
                .andExpect(jsonPath("$.data[0].code").exists())
                .andExpect(jsonPath("$.data[0].name").exists());
    }

    @Test
    void getAcademicPrograms_shouldReturnSeededList() throws Exception {
        mockMvc.perform(get("/api/academic-programs")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_0200"))
                .andExpect(jsonPath("$.data", hasSize(5)))
                .andExpect(jsonPath("$.data[0].code").exists())
                .andExpect(jsonPath("$.data[0].nameVi").exists());
    }

    @Test
    void getSpecializations_shouldReturnSeededList() throws Exception {
        mockMvc.perform(get("/api/specializations")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_0200"))
                .andExpect(jsonPath("$.data", hasSize(22))) // 7 + 2 + 3 + 2 + 8 = 22
                .andExpect(jsonPath("$.data[0].code").exists())
                .andExpect(jsonPath("$.data[0].nameVi").exists());
    }

    @Test
    void getSpecializationsByProgram_shouldReturnCorrectFilteredList() throws Exception {
        // Fetch CNTT program ID
        List<AcademicProgram> programs = academicProgramRepository.findAll();
        AcademicProgram cntt = programs.stream()
                .filter(p -> "CNTT".equals(p.getCode()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/api/academic-programs/" + cntt.getId() + "/specializations")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS_0200"))
                .andExpect(jsonPath("$.data", hasSize(7)))
                .andExpect(jsonPath("$.data[0].code").value("CNTT_KTPM"));
    }
}
