package com.fptu.exe.skillswap.modules.identity.seeder;

import com.fptu.exe.skillswap.modules.identity.domain.Campus;
import com.fptu.exe.skillswap.modules.identity.domain.CampusCode;
import com.fptu.exe.skillswap.modules.identity.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.identity.domain.Specialization;
import com.fptu.exe.skillswap.modules.identity.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.identity.repository.AcademicProgramRepository;
import com.fptu.exe.skillswap.modules.identity.repository.SpecializationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class AcademicDataSeeder implements CommandLineRunner {

    private final CampusRepository campusRepository;
    private final AcademicProgramRepository academicProgramRepository;
    private final SpecializationRepository specializationRepository;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting FPTU Academic Data Seeding...");

        seedCampuses();
        seedAcademicProgramsAndSpecializations();

        log.info("FPTU Academic Data Seeding completed successfully!");
    }

    private void seedCampuses() {
        seedCampus(CampusCode.HA_NOI, "Đại học FPT Hà Nội", "Hà Nội");
        seedCampus(CampusCode.HCM, "Đại học FPT TP. HCM", "TP. Hồ Chí Minh");
        seedCampus(CampusCode.DA_NANG, "Đại học FPT Đà Nẵng", "Đà Nẵng");
        seedCampus(CampusCode.CAN_THO, "Đại học FPT Cần Thơ", "Cần Thơ");
        seedCampus(CampusCode.QUY_NHON, "Đại học FPT Quy Nhơn", "Bình Định");
    }

    private void seedCampus(CampusCode code, String name, String city) {
        Optional<Campus> existing = campusRepository.findByCode(code);
        if (existing.isPresent()) {
            Campus campus = existing.get();
            boolean changed = false;
            if (!name.equals(campus.getName())) {
                campus.setName(name);
                changed = true;
            }
            if (city != null && !city.equals(campus.getCity())) {
                campus.setCity(city);
                changed = true;
            }
            if (!campus.isActive()) {
                campus.setActive(true);
                changed = true;
            }
            if (changed) {
                campusRepository.save(campus);
                log.info("Updated Campus: {} (Name: {}, City: {})", code, name, city);
            }
            return;
        }
        Campus campus = Campus.builder()
                .code(code)
                .name(name)
                .city(city)
                .isActive(true)
                .build();
        campusRepository.save(campus);
        log.info("Seeded Campus: {}", code);
    }

    private void seedAcademicProgramsAndSpecializations() {
        // 1. CNTT
        AcademicProgram cntt = seedProgram("CNTT", "Công nghệ thông tin", "Information Technology");
        seedSpecialization(cntt, "CNTT_KTPM", "Kỹ thuật phần mềm", "Software Engineering");
        seedSpecialization(cntt, "CNTT_TTNT", "Trí tuệ nhân tạo", "Artificial Intelligence");
        seedSpecialization(cntt, "CNTT_ATTT", "An toàn thông tin", "Information Assurance");
        seedSpecialization(cntt, "CNTT_TKVMCBD", "Thiết kế vi mạch bán dẫn", "Semiconductor IC Design");
        seedSpecialization(cntt, "CNTT_CNOTS", "Công nghệ ô tô số", "Digital Automotive Technology");
        seedSpecialization(cntt, "CNTT_HTTT", "Hệ thống thông tin", "Information Systems");
        seedSpecialization(cntt, "CNTT_TKDHMT", "Thiết kế đồ hoạ và mỹ thuật số", "Graphic and Digital Art Design");
        // Chuyên ngành mới thêm:
        seedSpecialization(cntt, "CNTT_RBT_TTNT", "Robot và trí tuệ nhân tạo", "Robotics and Artificial Intelligence");
        seedSpecialization(cntt, "CNTT_KHDL_UD", "Khoa học dữ liệu và ứng dụng", "Applied Data Science");
        seedSpecialization(cntt, "CNTT_TTNT_KHDL", "Trí tuệ nhân tạo và khoa học dữ liệu", "Artificial Intelligence and Data Science");
        seedSpecialization(cntt, "CNTT_ANM_ATS", "An ninh mạng và an toàn số", "Cybersecurity and Digital Safety");
        seedSpecialization(cntt, "CNTT_OTHER", "Chuyên ngành khác", "Other");

        // 2. CTTT
        AcademicProgram cttt = seedProgram("CTTT", "Công nghệ truyền thông", "Communication Technology");
        seedSpecialization(cttt, "CTTT_TTDPM", "Truyền thông đa phương tiện", "Multimedia Communications");
        seedSpecialization(cttt, "CTTT_QHCC", "Quan hệ công chúng", "Public Relations");
        // Chuyên ngành mới thêm:
        seedSpecialization(cttt, "CTTT_TT_MKT_TH", "Truyền thông Marketing tích hợp", "Integrated Marketing Communications");
        seedSpecialization(cttt, "CTTT_TTTH", "Truyền thông thương hiệu", "Brand Communications");
        seedSpecialization(cttt, "CTTT_OTHER", "Chuyên ngành khác", "Other");

        // 3. NN
        AcademicProgram nn = seedProgram("NN", "Ngôn ngữ", "Languages");
        seedSpecialization(nn, "NN_NNA", "Ngôn ngữ Anh", "English Linguistics");
        seedSpecialization(nn, "NN_NNHQ", "Ngôn ngữ Hàn Quốc", "Korean Linguistics");
        seedSpecialization(nn, "NN_NNTQ", "Ngôn ngữ Trung Quốc", "Chinese Linguistics");
        // Chuyên ngành mới thêm:
        seedSpecialization(nn, "NN_TATM", "Tiếng Anh thương mại", "Business English");
        seedSpecialization(nn, "NN_THTM", "Tiếng Hàn thương mại", "Business Korean");
        seedSpecialization(nn, "NN_TTTM", "Tiếng Trung thương mại", "Business Chinese");
        seedSpecialization(nn, "NN_OTHER", "Chuyên ngành khác", "Other");

        // 4. LUAT
        AcademicProgram luat = seedProgram("LUAT", "Luật", "Law");
        seedSpecialization(luat, "LUAT_L", "Luật", "Law");
        seedSpecialization(luat, "LUAT_LKT", "Luật Kinh tế", "Economic Law");
        seedSpecialization(luat, "LUAT_OTHER", "Chuyên ngành khác", "Other");

        // 5. QTKD
        AcademicProgram qtkd = seedProgram("QTKD", "Quản trị kinh doanh", "Business Administration");
        seedSpecialization(qtkd, "QTKD_MKT", "Digital Marketing", "Digital Marketing");
        seedSpecialization(qtkd, "QTKD_KDQT", "Kinh doanh quốc tế", "International Business");
        seedSpecialization(qtkd, "QTKD_TMDT", "Thương mại điện tử", "E-Commerce");
        seedSpecialization(qtkd, "QTKD_QTKD", "Quản trị kinh doanh", "Business Administration");
        seedSpecialization(qtkd, "QTKD_QTDVDLLH", "Quản trị dịch vụ du lịch và lữ hành", "Hospitality and Tourism Management");
        seedSpecialization(qtkd, "QTKD_LQLCCUGC", "Logistics và Quản lý chuỗi cung ứng toàn cầu", "Logistics and Global Supply Chain Management");
        seedSpecialization(qtkd, "QTKD_CNTC", "Công nghệ tài chính", "Financial Technology");
        seedSpecialization(qtkd, "QTKD_TCNH", "Tài chính ngân hàng", "Finance and Banking");
        // Chuyên ngành mới thêm:
        seedSpecialization(qtkd, "QTKD_TCDN", "Tài chính doanh nghiệp", "Corporate Finance");
        seedSpecialization(qtkd, "QTKD_OTHER", "Chuyên ngành khác", "Other");
    }

    private AcademicProgram seedProgram(String code, String nameVi, String nameEn) {
        Optional<AcademicProgram> existing = academicProgramRepository.findByCode(code);
        if (existing.isPresent()) {
            AcademicProgram program = existing.get();
            boolean changed = false;
            if (!nameVi.equals(program.getNameVi()) || !nameEn.equals(program.getNameEn())) {
                program.setNameVi(nameVi);
                program.setNameEn(nameEn);
                changed = true;
            }
            if (!program.isActive()) {
                program.setActive(true);
                changed = true;
            }
            if (changed) {
                academicProgramRepository.save(program);
                log.info("Updated Academic Program: {} (Vi: {}, En: {})", code, nameVi, nameEn);
            }
            return program;
        }
        AcademicProgram program = AcademicProgram.builder()
                .code(code)
                .nameVi(nameVi)
                .nameEn(nameEn)
                .isActive(true)
                .build();
        AcademicProgram saved = academicProgramRepository.save(program);
        log.info("Seeded Academic Program: {}", code);
        return saved;
    }

    private void seedSpecialization(AcademicProgram program, String code, String nameVi, String nameEn) {
        Optional<Specialization> existing = specializationRepository.findByCode(code);
        if (existing.isEmpty()) {
            Specialization spec = Specialization.builder()
                    .program(program)
                    .code(code)
                    .nameVi(nameVi)
                    .nameEn(nameEn)
                    .isExpected(false)
                    .isActive(true)
                    .build();
            specializationRepository.save(spec);
            log.info("Seeded Specialization: {}", code);
        } else {
            Specialization spec = existing.get();
            boolean changed = false;
            if (spec.getProgram() == null || !program.getId().equals(spec.getProgram().getId())) {
                spec.setProgram(program);
                changed = true;
            }
            if (!nameVi.equals(spec.getNameVi()) || !nameEn.equals(spec.getNameEn())) {
                spec.setNameVi(nameVi);
                spec.setNameEn(nameEn);
                changed = true;
            }
            if (spec.isExpected()) {
                spec.setExpected(false);
                changed = true;
            }
            if (!spec.isActive()) {
                spec.setActive(true);
                changed = true;
            }
            if (changed) {
                specializationRepository.save(spec);
                log.info("Updated Specialization: {} (Vi: {}, En: {})", code, nameVi, nameEn);
            }
        }
    }

}
