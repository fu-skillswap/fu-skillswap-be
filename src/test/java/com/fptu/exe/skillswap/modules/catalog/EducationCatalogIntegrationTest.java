package com.fptu.exe.skillswap.modules.catalog;

import com.fptu.exe.skillswap.modules.catalog.repository.*;
import com.fptu.exe.skillswap.modules.catalog.seeder.EducationCatalogSeeder;
import com.fptu.exe.skillswap.modules.catalog.service.EducationCatalogService;
import com.fptu.exe.skillswap.infrastructure.testcontainer.AbstractPostgreSQLIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import java.util.ArrayList;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate", "spring.test.database.replace=none"})
@ActiveProfiles("test")
@Transactional
class EducationCatalogIntegrationTest extends AbstractPostgreSQLIntegrationTest {
    @Autowired AdministrativeProvinceRepository provinces;
    @Autowired EducationalInstitutionRepository institutions;
    @Autowired EducationFieldGroupRepository fields;
    @Autowired EducationCatalogSeeder seeder;
    @Autowired EducationCatalogService catalog;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test void provinceSeederCoversExactlyThirtyFourAndIsIdempotent(){
        long before=provinces.count(); seeder.run();
        assertEquals(34,before); assertEquals(before,provinces.count());
        assertEquals(34,provinces.findAll().stream().map(p->p.getCode()).distinct().count());
    }
    @Test void institutionsReferenceActiveProvinceAndRemainUniqueAfterReseed(){
        long before=institutions.count(); assertTrue(before>=45); seeder.run();
        assertEquals(before,institutions.count());
        assertTrue(institutions.findAll().stream().allMatch(i->i.getProvince()!=null && i.getProvince().isActive()));
    }
    @Test void searchesIgnoreVietnameseDiacriticsAndSupportFiltersAndPagination(){
        assertTrue(catalog.provinces("ha noi").stream().anyMatch(p->p.name().equals("Hà Nội")));
        assertTrue(catalog.provinces("Hà Nội").stream().anyMatch(p->p.name().equals("Hà Nội")));
        var institutionsPage=catalog.institutions("bach khoa",provinces.findByCode("01").orElseThrow().getId(),0,1);
        assertEquals(1,institutionsPage.getContent().size()); assertEquals(1,institutionsPage.getSize()); assertEquals(1,institutionsPage.getTotalElements());
        assertEquals(1,catalog.institutions("bách khoa",provinces.findByCode("01").orElseThrow().getId(),0,1).getTotalElements());
        var marketing=catalog.fieldGroups("marketing",0,10);
        assertEquals(1,marketing.getTotalElements()); assertEquals("734",marketing.getContent().getFirst().officialCode());
        assertTrue(fields.count()>=20);
    }

    @Test void databasePaginationHasStablePagesExactTotalsAndConstantQueryCount(){
        var province=provinces.findByCode("01").orElseThrow();
        institutions.saveAndFlush(com.fptu.exe.skillswap.modules.catalog.domain.EducationalInstitution.builder()
                .slug("prompt8-shortname-"+UUID.randomUUID()).name("Short Name Probe").shortName("Đức Bách")
                .province(province).institutionType("UNIVERSITY").normalizedName("short name probe").active(true).sortOrder(9999).build());
        assertEquals(1,catalog.institutions("duc bach",province.getId(),0,10).getTotalElements(),
                "Vietnamese short names keep the same accent insensitive matching behavior");
        var added=new ArrayList<com.fptu.exe.skillswap.modules.catalog.domain.EducationalInstitution>();
        for(int n=0;n<140;n++) added.add(com.fptu.exe.skillswap.modules.catalog.domain.EducationalInstitution.builder()
                .slug("prompt8-page-"+UUID.randomUUID()).name("Prompt Eight University "+n).shortName("P8U"+n)
                .province(province).institutionType("UNIVERSITY").normalizedName("prompt eight university "+n).active(true).sortOrder(10000).build());
        institutions.saveAll(added); institutions.flush();

        SessionFactory sessionFactory=entityManagerFactory.unwrap(SessionFactory.class);
        var statistics=sessionFactory.getStatistics(); statistics.setStatisticsEnabled(true); statistics.clear();
        var first=catalog.institutions("prompt eight university",province.getId(),0,40);
        long queriesAfterFirst=statistics.getPrepareStatementCount();
        var second=catalog.institutions("prompt eight university",province.getId(),1,40);
        var third=catalog.institutions("prompt eight university",province.getId(),2,40);
        var fourth=catalog.institutions("prompt eight university",province.getId(),3,40);
        assertEquals(140,first.getTotalElements()); assertEquals(4,first.getTotalPages());
        assertEquals(40,first.getContent().size()); assertEquals(40,second.getContent().size());
        assertEquals(20,fourth.getContent().size()); assertTrue(fourth.isLast());
        var firstIds=first.getContent().stream().map(com.fptu.exe.skillswap.modules.catalog.dto.InstitutionResponse::id).toList();
        var secondIds=second.getContent().stream().map(com.fptu.exe.skillswap.modules.catalog.dto.InstitutionResponse::id).toList();
        assertTrue(java.util.Collections.disjoint(firstIds,secondIds));
        var allPageIds=java.util.stream.Stream.of(first,second,third,fourth).flatMap(p -> p.getContent().stream())
                .map(com.fptu.exe.skillswap.modules.catalog.dto.InstitutionResponse::id).toList();
        assertEquals(140,allPageIds.stream().distinct().count());
        assertTrue(firstIds.getFirst().compareTo(firstIds.getLast())!=0);
        assertTrue(queriesAfterFirst<=2,"The page and its total count are database queries, independent of 40 result rows");
        assertTrue(statistics.getPrepareStatementCount()-queriesAfterFirst<=6,
                "Fetching another 120 rows must use a fixed page and count query budget");
        assertEquals(0,catalog.institutions("no such catalog row",province.getId(),0,10).getTotalElements());
        assertTrue(catalog.institutions("prompt eight university",province.getId(),20,10).getContent().isEmpty());
        var ordered=catalog.institutions("prompt eight university",province.getId(),0,100);
        for(int index=1;index<ordered.getContent().size();index++)
            assertTrue(ordered.getContent().get(index-1).id().compareTo(ordered.getContent().get(index).id())<0);
    }

    @Test void fieldGroupAliasSearchIsPagedAndDeterministic(){
        var first=catalog.fieldGroups("marketing",0,1);
        var beyond=catalog.fieldGroups("marketing",5,1);
        assertEquals(1,first.getTotalElements()); assertEquals(1,first.getTotalPages());
        assertEquals("734",first.getContent().getFirst().officialCode());
        assertTrue(beyond.getContent().isEmpty());
        assertEquals(1,catalog.fieldGroups("tiep thi",0,10).getTotalElements());
        assertEquals(1,catalog.fieldGroups("tiếp thị",0,10).getTotalElements());
    }
}
