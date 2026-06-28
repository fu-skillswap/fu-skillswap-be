package com.fptu.exe.skillswap.modules.academic.seeder;

import com.fptu.exe.skillswap.modules.academic.domain.Campus;
import com.fptu.exe.skillswap.modules.academic.domain.CampusCode;
import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.academic.domain.Specialization;
import com.fptu.exe.skillswap.modules.academic.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.academic.repository.AcademicProgramRepository;
import com.fptu.exe.skillswap.modules.academic.repository.SpecializationRepository;
import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class AcademicDataSeeder implements CommandLineRunner {

    private final CampusRepository campusRepository;
    private final AcademicProgramRepository academicProgramRepository;
    private final SpecializationRepository specializationRepository;
    private final TagRepository tagRepository;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting FPTU Academic Data Seeding...");

        seedCampuses();
        seedAcademicProgramsAndSpecializations();
        seedMentorSelectionTags();

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
        if (existing.isEmpty()) {
            Campus campus = Campus.builder()
                    .code(code)
                    .name(name)
                    .city(city)
                    .isActive(true)
                    .build();
            campusRepository.save(campus);
            log.info("Seeded Campus: {}", code);
        }
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
        seedSpecialization(cntt, "CNTT_OTHER", "Chuyên ngành khác", "Other");

        // 2. CTTT
        AcademicProgram cttt = seedProgram("CTTT", "Công nghệ truyền thông", "Communication Technology");
        seedSpecialization(cttt, "CTTT_TTDPM", "Truyền thông đa phương tiện", "Multimedia Communications");
        seedSpecialization(cttt, "CTTT_QHCC", "Quan hệ công chúng", "Public Relations");
        seedSpecialization(cttt, "CTTT_OTHER", "Chuyên ngành khác", "Other");

        // 3. NN
        AcademicProgram nn = seedProgram("NN", "Ngôn ngữ", "Languages");
        seedSpecialization(nn, "NN_NNA", "Ngôn ngữ Anh", "English Linguistics");
        seedSpecialization(nn, "NN_NNHQ", "Ngôn ngữ Hàn Quốc", "Korean Linguistics");
        seedSpecialization(nn, "NN_NNTQ", "Ngôn ngữ Trung Quốc", "Chinese Linguistics");
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
        seedSpecialization(qtkd, "QTKD_OTHER", "Chuyên ngành khác", "Other");
    }

    private AcademicProgram seedProgram(String code, String nameVi, String nameEn) {
        Optional<AcademicProgram> existing = academicProgramRepository.findByCode(code);
        if (existing.isPresent()) {
            AcademicProgram program = existing.get();
            if (!nameVi.equals(program.getNameVi()) || !nameEn.equals(program.getNameEn())) {
                program.setNameVi(nameVi);
                program.setNameEn(nameEn);
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
            if (!nameVi.equals(spec.getNameVi()) || !nameEn.equals(spec.getNameEn())) {
                spec.setNameVi(nameVi);
                spec.setNameEn(nameEn);
                specializationRepository.save(spec);
                log.info("Updated Specialization: {} (Vi: {}, En: {})", code, nameVi, nameEn);
            }
        }
    }

    private void seedMentorSelectionTags() {
        seedTag("TECH_JAVA", "Java", "Java", TagType.TECH_SKILL, 100);
        seedTag("TECH_SPRING_BOOT", "Spring Boot", "Spring Boot", TagType.TECH_SKILL, 98);
        seedTag("TECH_BACKEND", "Backend Development", "Backend Development", TagType.TECH_SKILL, 97);
        seedTag("TECH_DATABASE", "Database Design", "Database Design", TagType.TECH_SKILL, 92);
        seedTag("TECH_API_DESIGN", "API Design", "API Design", TagType.TECH_SKILL, 90);
        seedTag("TECH_SYSTEM_DESIGN", "System Design", "System Design", TagType.TECH_SKILL, 89);
        seedTag("TECH_DSA", "Data Structures and Algorithms", "Data Structures and Algorithms", TagType.TECH_SKILL, 91);
        seedTag("TECH_WEB_DEV", "Web Development", "Web Development", TagType.TECH_SKILL, 88);
        seedTag("TECH_MOBILE_DEV", "Mobile Development", "Mobile Development", TagType.TECH_SKILL, 84);
        seedTag("TECH_CLOUD", "Cloud Computing", "Cloud Computing", TagType.TECH_SKILL, 86);
        seedTag("TECH_DEVOPS", "DevOps", "DevOps", TagType.TECH_SKILL, 85);
        seedTag("TECH_UI_UX", "UI/UX", "UI/UX", TagType.TECH_SKILL, 80);
        seedTag("TECH_PRODUCT_MANAGEMENT", "Product Management", "Product Management", TagType.TECH_SKILL, 79);
        seedTag("TECH_AI", "Artificial Intelligence", "Artificial Intelligence", TagType.TECH_SKILL, 87);
        seedTag("TECH_DATA_ANALYTICS", "Data Analytics", "Data Analytics", TagType.TECH_SKILL, 83);

        seedTag("HELP_CV_REVIEW", "Đánh giá CV", "CV Review", TagType.HELP_TOPIC, 100);
        seedTag("HELP_INTERVIEW", "Luyện phỏng vấn", "Mock Interview", TagType.HELP_TOPIC, 98);
        seedTag("HELP_CAREER_PATH", "Định hướng nghề nghiệp", "Career Guidance", TagType.HELP_TOPIC, 96);
        seedTag("HELP_INTERNSHIP", "Hỗ trợ thực tập", "Internship Guidance", TagType.HELP_TOPIC, 93);
        seedTag("HELP_PROJECT_REVIEW", "Đánh giá dự án", "Project Review", TagType.HELP_TOPIC, 92);
        seedTag("HELP_GRADUATION_THESIS", "Hướng dẫn đồ án tốt nghiệp", "Graduation Thesis Guidance", TagType.HELP_TOPIC, 90);
        seedTag("HELP_PRODUCT_FEEDBACK", "Góp ý sản phẩm", "Product Feedback", TagType.HELP_TOPIC, 88);
        seedTag("HELP_QA", "Giải đáp thắc mắc", "Q&A Support", TagType.HELP_TOPIC, 89);
        seedTag("HELP_STUDY_PLAN", "Hướng dẫn môn học", "Study Guidance", TagType.HELP_TOPIC, 87);
    }

    private void seedTag(String code, String nameVi, String nameEn, TagType type, int weight) {
        Optional<Tag> existing = tagRepository.findByCode(code);
        if (existing.isPresent()) {
            Tag tag = existing.get();
            boolean changed = false;
            if (!nameVi.equals(tag.getNameVi())) {
                tag.setNameVi(nameVi);
                changed = true;
            }
            if (nameEn != null && !nameEn.equals(tag.getNameEn())) {
                tag.setNameEn(nameEn);
                changed = true;
            }
            if (tag.getType() != type) {
                tag.setType(type);
                changed = true;
            }
            if (tag.getWeight() == null || tag.getWeight() != weight) {
                tag.setWeight(weight);
                changed = true;
            }
            if (!tag.isFixed()) {
                tag.setFixed(true);
                changed = true;
            }
            if (tag.getStatus() != TagStatus.ACTIVE) {
                tag.setStatus(TagStatus.ACTIVE);
                changed = true;
            }
            if (changed) {
                tagRepository.save(tag);
                log.info("Updated Tag: {} ({})", code, type);
            }
            return;
        }

        Tag tag = Tag.builder()
                .code(code)
                .nameVi(nameVi)
                .nameEn(nameEn)
                .type(type)
                .status(TagStatus.ACTIVE)
                .weight(weight)
                .isFixed(true)
                .build();
        tagRepository.save(tag);
        log.info("Seeded Tag: {} ({})", code, type);
    }
}
