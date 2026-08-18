package com.fptu.exe.skillswap.modules.identity.integration;

import com.fptu.exe.skillswap.modules.identity.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.identity.service.AcademicService;
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
    @SpyBean
    private CampusRepository campusRepository;

    @BeforeEach
    void resetSpyInvocations() {
        clearInvocations(campusRepository);
    }

    @Test
    void academicMasterData_shouldBeCachedBetweenCalls() {
        academicService.getAllCampuses();
        academicService.getAllCampuses();

        verify(campusRepository, times(1)).findByIsActiveTrue();
    }

}
