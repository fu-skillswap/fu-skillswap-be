package com.fptu.exe.skillswap.modules.catalog.seeder;

import com.fptu.exe.skillswap.modules.catalog.domain.*;
import com.fptu.exe.skillswap.modules.catalog.repository.*;
import com.fptu.exe.skillswap.modules.catalog.service.EducationCatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Component @Order(2) @RequiredArgsConstructor @Slf4j
public class EducationCatalogSeeder implements CommandLineRunner {
    private final AdministrativeProvinceRepository provinceRepo;
    private final EducationalInstitutionRepository institutionRepo;
    private final EducationFieldGroupRepository fieldRepo;

    private static final String[][] PROVINCES={
        {"91","An Giang","TINH"},{"27","Bắc Ninh","TINH"},{"96","Cà Mau","TINH"},{"04","Cao Bằng","TINH"},{"92","Cần Thơ","THANH_PHO"},{"48","Đà Nẵng","THANH_PHO"},{"66","Đắk Lắk","TINH"},{"11","Điện Biên","TINH"},{"75","Đồng Nai","TINH"},{"82","Đồng Tháp","TINH"},{"64","Gia Lai","TINH"},{"01","Hà Nội","THANH_PHO"},{"42","Hà Tĩnh","TINH"},{"31","Hải Phòng","THANH_PHO"},{"33","Hưng Yên","TINH"},{"46","Huế","THANH_PHO"},{"56","Khánh Hòa","TINH"},{"12","Lai Châu","TINH"},{"68","Lâm Đồng","TINH"},{"20","Lạng Sơn","TINH"},{"10","Lào Cai","TINH"},{"40","Nghệ An","TINH"},{"37","Ninh Bình","TINH"},{"25","Phú Thọ","TINH"},{"51","Quảng Ngãi","TINH"},{"22","Quảng Ninh","TINH"},{"44","Quảng Trị","TINH"},{"14","Sơn La","TINH"},{"72","Tây Ninh","TINH"},{"19","Thái Nguyên","TINH"},{"38","Thanh Hóa","TINH"},{"79","Thành phố Hồ Chí Minh","THANH_PHO"},{"08","Tuyên Quang","TINH"},{"86","Vĩnh Long","TINH"}
    };
    // Official names of representative institutions requested for the initial catalog.
    private static final String[][] SCHOOLS={
        {"hcm-vnu-science","Trường Đại học Khoa học tự nhiên, Đại học Quốc gia Thành phố Hồ Chí Minh","HCMUS","HCM","PUBLIC"},
        {"hcm-vnu-social-sciences-humanities","Trường Đại học Khoa học xã hội và Nhân văn, Đại học Quốc gia Thành phố Hồ Chí Minh","USSH","HCM","PUBLIC"},
        {"hcm-vnu-technology","Trường Đại học Bách khoa, Đại học Quốc gia Thành phố Hồ Chí Minh","HCMUT","HCM","PUBLIC"},
        {"hcm-vnu-international","Trường Đại học Quốc tế, Đại học Quốc gia Thành phố Hồ Chí Minh","HCMIU","HCM","PUBLIC"},
        {"hcm-vnu-information-technology","Trường Đại học Công nghệ Thông tin, Đại học Quốc gia Thành phố Hồ Chí Minh","UIT","HCM","PUBLIC"},
        {"hcm-vnu-economics-law","Trường Đại học Kinh tế - Luật, Đại học Quốc gia Thành phố Hồ Chí Minh","UEL","HCM","PUBLIC"},
        {"ueh","Đại học Kinh tế Thành phố Hồ Chí Minh","UEH","HCM","PUBLIC"},
        {"hcmute","Trường Đại học Sư phạm Kỹ thuật Thành phố Hồ Chí Minh","HCMUTE","HCM","PUBLIC"},
        {"ump-hcm","Đại học Y Dược Thành phố Hồ Chí Minh","UMP","HCM","PUBLIC"},
        {"pntu","Trường Đại học Y khoa Phạm Ngọc Thạch","PNTU","HCM","PUBLIC"},
        {"hcmulaw","Trường Đại học Luật Thành phố Hồ Chí Minh","HCMULAW","HCM","PUBLIC"},
        {"ou-hcm","Trường Đại học Mở Thành phố Hồ Chí Minh","HCMCOU","HCM","PUBLIC"},
        {"hubs","Trường Đại học Ngân hàng Thành phố Hồ Chí Minh","HUB","HCM","PUBLIC"},
        {"uofm-hcm","Trường Đại học Tài chính - Marketing","UFM","HCM","PUBLIC"},
        {"uah","Trường Đại học Kiến trúc Thành phố Hồ Chí Minh","UAH","HCM","PUBLIC"},
        {"nong-lam-hcm","Trường Đại học Nông Lâm Thành phố Hồ Chí Minh","NLU","HCM","PUBLIC"},
        {"sgu","Trường Đại học Sài Gòn","SGU","HCM","PUBLIC"},
        {"tdtu","Trường Đại học Tôn Đức Thắng","TDTU","HCM","PUBLIC"},
        {"fpt-hcm","Trường Đại học FPT","FPT","HCM","PRIVATE"},
        {"rmit-hcm","Đại học RMIT Việt Nam","RMIT","HCM","PRIVATE"},
        {"hutech","Trường Đại học Công nghệ Thành phố Hồ Chí Minh","HUTECH","HCM","PRIVATE"},
        {"van-lang","Trường Đại học Văn Lang","VLU","HCM","PRIVATE"},
        {"hoa-sen","Trường Đại học Hoa Sen","HSU","HCM","PRIVATE"},
        {"uef","Trường Đại học Kinh tế - Tài chính Thành phố Hồ Chí Minh","UEF","HCM","PRIVATE"},
        {"nguyen-tat-thanh","Trường Đại học Nguyễn Tất Thành","NTTU","HCM","PRIVATE"},
        {"vnu-hanoi-science","Trường Đại học Khoa học Tự nhiên, Đại học Quốc gia Hà Nội","HUS","HN","PUBLIC"},
        {"vnu-hanoi-humanities","Trường Đại học Khoa học Xã hội và Nhân văn, Đại học Quốc gia Hà Nội","USSH","HN","PUBLIC"},
        {"vnu-hanoi-languages","Trường Đại học Ngoại ngữ, Đại học Quốc gia Hà Nội","ULIS","HN","PUBLIC"},
        {"vnu-hanoi-engineering","Trường Đại học Công nghệ, Đại học Quốc gia Hà Nội","UET","HN","PUBLIC"},
        {"vnu-hanoi-economics","Trường Đại học Kinh tế, Đại học Quốc gia Hà Nội","VNU-UEB","HN","PUBLIC"},
        {"vnu-hanoi-education","Trường Đại học Giáo dục, Đại học Quốc gia Hà Nội","VNU-UED","HN","PUBLIC"},
        {"vnu-hanoi-law","Trường Đại học Luật, Đại học Quốc gia Hà Nội","VNU-UL","HN","PUBLIC"},
        {"vnu-hanoi-vietnam-japan","Trường Đại học Việt Nhật, Đại học Quốc gia Hà Nội","VJU","HN","PUBLIC"},
        {"vnu-hanoi-international-school","Trường Quốc tế, Đại học Quốc gia Hà Nội","VNU-IS","HN","PUBLIC"},
        {"vnu-hanoi-medicine-pharmacy","Trường Đại học Y Dược, Đại học Quốc gia Hà Nội","VNU-UMP","HN","PUBLIC"},
        {"hust","Đại học Bách khoa Hà Nội","HUST","HN","PUBLIC"},
        {"neu","Đại học Kinh tế Quốc dân","NEU","HN","PUBLIC"},
        {"ftu","Trường Đại học Ngoại thương","FTU","HN","PUBLIC"},
        {"hmu","Trường Đại học Y Hà Nội","HMU","HN","PUBLIC"},
        {"hlu","Trường Đại học Luật Hà Nội","HLU","HN","PUBLIC"},
        {"bav","Học viện Ngân hàng","BA","HN","PUBLIC"},
        {"aof","Học viện Tài chính","AOF","HN","PUBLIC"},
        {"huce","Trường Đại học Xây dựng Hà Nội","HUCE","HN","PUBLIC"},
        {"utc","Trường Đại học Giao thông vận tải","UTC","HN","PUBLIC"},
        {"fpt-hanoi","Trường Đại học FPT","FPT","HN","PRIVATE"},
        {"ud-science-education","Trường Đại học Sư phạm, Đại học Đà Nẵng","UED","DN","PUBLIC"},
        {"ud-economics","Trường Đại học Kinh tế, Đại học Đà Nẵng","DUE","DN","PUBLIC"},
        {"ud-technology","Trường Đại học Bách khoa, Đại học Đà Nẵng","DUT","DN","PUBLIC"},
        {"ud-foreign-languages","Trường Đại học Ngoại ngữ, Đại học Đà Nẵng","UFLS","DN","PUBLIC"},
        {"vku","Trường Đại học Công nghệ Thông tin và Truyền thông Việt - Hàn, Đại học Đà Nẵng","VKU","DN","PUBLIC"},
        {"duy-tan","Trường Đại học Duy Tân","DTU","DN","PRIVATE"},
        {"dong-a","Trường Đại học Đông Á","UDA","DN","PRIVATE"}
    };
    private static final String[][] FIELDS={
        {"714","Khoa học giáo dục và đào tạo giáo viên","sư phạm|giáo dục"},{"721","Nghệ thuật","mỹ thuật|nghệ thuật"},{"722","Nhân văn","ngôn ngữ|văn hóa"},{"731","Khoa học xã hội và hành vi","xã hội"},{"732","Báo chí và thông tin","báo chí|truyền thông"},{"734","Kinh doanh và quản lý","marketing|tiếp thị|kinh doanh|quản trị"},{"738","Pháp luật","luật"},{"742","Khoa học sự sống","sinh học"},{"744","Khoa học tự nhiên","khoa học vật chất"},{"746","Toán và thống kê","toán|thống kê"},{"748","Máy tính và công nghệ thông tin","công nghệ thông tin|cntt|it"},{"751","Công nghệ kỹ thuật","công nghệ kỹ thuật cơ khí|cơ khí"},{"752","Kỹ thuật","kỹ thuật cơ khí|điện|điện tử|viễn thông"},{"754","Sản xuất và chế biến","sản xuất|chế biến"},{"758","Kiến trúc và xây dựng","kiến trúc|xây dựng"},{"762","Nông nghiệp","nông nghiệp|lâm nghiệp|thủy sản"},{"764","Thú y","thú y"},{"772","Sức khỏe","y học|y khoa|dược|dược học"},{"781","Du lịch, khách sạn, thể thao và dịch vụ cá nhân","du lịch|khách sạn"},{"785","Môi trường và bảo vệ môi trường","môi trường"}
    };

    @Override @Transactional public void run(String... args) {
        Map<String,AdministrativeProvince> byCode=new HashMap<>(); int order=1;
        for(String[] row:PROVINCES){
            AdministrativeProvince p=provinceRepo.findByCode(row[0]).orElseGet(AdministrativeProvince::new);
            p.setCode(row[0]); p.setName(row[1]); p.setNormalizedName(EducationCatalogService.normalize(row[1])); p.setUnitType(row[2]); p.setActive(true); p.setSortOrder(order++); p.setEffectiveFrom(LocalDate.of(2025,7,1));
            p=provinceRepo.save(p); byCode.put(p.getCode(),p);
        }
        String[][] provinceCodes={{"HCM","79"},{"HN","01"},{"DN","48"}};
        Map<String,AdministrativeProvince> codes=new HashMap<>(); for(String[] p:provinceCodes) codes.put(p[0],byCode.get(p[1]));
        order=1;
        for(String[] row:SCHOOLS){
            EducationalInstitution e=institutionRepo.findBySlug(row[0]).orElseGet(EducationalInstitution::new);
            e.setSlug(row[0]); e.setName(row[1]); e.setShortName(row[2]); e.setProvince(codes.get(row[3])); e.setInstitutionType(row[4]); e.setNormalizedName(EducationCatalogService.normalize(row[1])); e.setActive(true); e.setSortOrder(order++); institutionRepo.save(e);
        }
        order=1;
        for(String[] row:FIELDS){
            EducationFieldGroup g=fieldRepo.findByOfficialCode(row[0]).orElseGet(EducationFieldGroup::new);
            g.setOfficialCode(row[0]); g.setName(row[1]); g.setNormalizedName(EducationCatalogService.normalize(row[1])); g.setAliases(row[2].isBlank()?new ArrayList<>():new ArrayList<>(Arrays.asList(row[2].split("\\|")))); g.setActive(true); g.setSortOrder(order++); fieldRepo.save(g);
        }
        log.info("Education catalogs seeded idempotently: {} provinces, {} institutions, {} field groups",PROVINCES.length,SCHOOLS.length,FIELDS.length);
    }
}
