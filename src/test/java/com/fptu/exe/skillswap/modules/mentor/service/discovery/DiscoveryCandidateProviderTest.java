package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class DiscoveryCandidateProviderTest {

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @Mock
    private DataSource dataSource;

    @Test
    void recallWindowSize_expandsOnlyForRelevanceSearch() {
        DiscoveryCandidateProvider provider = new DiscoveryCandidateProvider(mentorProfileRepository, dataSource);

        assertEquals(200, provider.recallWindowSize(0, 20, true, 200));
        assertEquals(60, provider.recallWindowSize(1, 30, false, 40));
    }
}
