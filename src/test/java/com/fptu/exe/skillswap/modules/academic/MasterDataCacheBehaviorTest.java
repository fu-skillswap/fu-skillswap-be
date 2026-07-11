package com.fptu.exe.skillswap.modules.academic;

import com.fptu.exe.skillswap.modules.academic.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.modules.catalog.repository.TagRepository;
import com.fptu.exe.skillswap.modules.catalog.service.CatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class MasterDataCacheBehaviorTest {

    @Autowired
    private AcademicService academicService;

    @Autowired
    private CatalogService catalogService;

    @SpyBean
    private CampusRepository campusRepository;

    @SpyBean
    private TagRepository tagRepository;

    @BeforeEach
    void resetSpyInvocations() {
        clearInvocations(campusRepository, tagRepository);
    }

    @Test
    void academicMasterData_shouldBeCachedBetweenCalls() {
        academicService.getAllCampuses();
        academicService.getAllCampuses();

        verify(campusRepository, times(1)).findByIsActiveTrue();
    }

    @Test
    void catalogHelpTopics_shouldBeCachedBetweenCalls() {
        catalogService.getHelpTopics();
        catalogService.getHelpTopics();

        verify(tagRepository, times(1)).findByTypeAndStatusOrderByWeightDescNameViAsc(
                com.fptu.exe.skillswap.modules.catalog.domain.TagType.HELP_TOPIC,
                com.fptu.exe.skillswap.modules.catalog.domain.TagStatus.ACTIVE
        );
    }
}
