package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorDiscoverySearchRequest;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscoveryCandidateProviderTest {

    private static final UUID MENTOR_ID = UUID.fromString("018f3abf-0a22-7192-9748-6cf000c47b6e");

    @Mock
    private MentorProfileRepository mentorProfileRepository;
    @Mock
    private DataSource dataSource;
    @Mock
    private Connection connection;
    @Mock
    private DatabaseMetaData metaData;

    private DiscoveryCandidateProvider candidateProvider;

    @BeforeEach
    void setUp() {
        candidateProvider = new DiscoveryCandidateProvider(mentorProfileRepository, dataSource);
    }

    @Test
    void recallWindowSize_shouldExpandForRelevance() {
        assertEquals(200, candidateProvider.recallWindowSize(0, 20, true, 200));
        assertEquals(60, candidateProvider.recallWindowSize(1, 30, false, 40));
    }

    @Test
    void isPostgresDataSource_shouldMemoizeDetection() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        assertTrue(candidateProvider.isPostgresDataSource());
        assertTrue(candidateProvider.isPostgresDataSource());

        verify(dataSource, times(1)).getConnection();
    }

    @Test
    void recallForSearch_withoutKeyword_shouldUseBrowseQueryAndNeutralTagId() {
        MentorDiscoverySearchRequest request = new MentorDiscoverySearchRequest();
        request.setPage(0);
        request.setSize(20);

        when(mentorProfileRepository.findDiscoverableCandidateIds(
                eq(MentorStatus.ACTIVE),
                eq(MentorTagType.HELP_TOPIC),
                eq(null),
                eq(null),
                eq(false),
                any(),
                any(),
                any(PageRequest.class)
        )).thenReturn(new PageImpl<>(List.of(MENTOR_ID), PageRequest.of(0, 200), 1));

        CandidateWindow window = candidateProvider.recallForSearch(
                request,
                "",
                null,
                null,
                false,
                List.of(new Sort.Order(Sort.Direction.DESC, "averageRating")),
                LocalDateTime.now(),
                200
        );

        assertEquals(List.of(MENTOR_ID), window.candidateIds());
        ArgumentCaptor<List<UUID>> tagIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(mentorProfileRepository).findDiscoverableCandidateIds(
                eq(MentorStatus.ACTIVE),
                eq(MentorTagType.HELP_TOPIC),
                eq(null),
                eq(null),
                eq(false),
                tagIdsCaptor.capture(),
                any(),
                any(PageRequest.class)
        );
        assertEquals(List.of(UUID.fromString("00000000-0000-0000-0000-000000000000")), tagIdsCaptor.getValue());
    }

    @Test
    void recallForSearch_withKeywordAndPostgres_shouldUseFtsQuery() throws Exception {
        MentorDiscoverySearchRequest request = new MentorDiscoverySearchRequest();
        request.setKeyword("spring boot");
        request.setPage(0);
        request.setSize(20);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(mentorProfileRepository.findDiscoverableCandidateIdsByFts(anyString(), any(), any(), anyBoolean(), anyString(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(MENTOR_ID));
        when(mentorProfileRepository.countDiscoverableCandidatesByFts(anyString(), any(), any(), anyBoolean(), anyString(), any()))
                .thenReturn(1L);

        CandidateWindow window = candidateProvider.recallForSearch(
                request,
                "spring boot",
                "%spring boot%",
                "%spring boot%",
                true,
                List.of(new Sort.Order(Sort.Direction.DESC, "averageRating")),
                LocalDateTime.now(),
                200
        );

        assertEquals(1L, window.totalCount());
        verify(mentorProfileRepository).findDiscoverableCandidateIdsByFts(eq("spring boot"), eq(null), eq(null), eq(false), anyString(), any(), eq(200), eq(0));
    }

    @Test
    void recallForRecommendation_shouldReturnRepositoryRows() {
        MentorDiscoveryQueryRow row = new MentorDiscoveryQueryRow(
                MENTOR_ID,
                "Mentor",
                "avatar",
                "headline",
                "expertise",
                "bio",
                3,
                3,
                3,
                true,
                null,
                0,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                0,
                0,
                0,
                null,
                null
        );
        when(mentorProfileRepository.findRecommendationCandidatesSortedByRelevance(eq(MentorStatus.ACTIVE), eq(MentorTagType.HELP_TOPIC), eq(MENTOR_ID), any(), any(PageRequest.class)))
                .thenReturn(List.of(row));

        List<MentorDiscoveryQueryRow> result = candidateProvider.recallForRecommendation(MENTOR_ID, true, 3, LocalDateTime.now(), 200);

        assertEquals(List.of(row), result);
        verify(mentorProfileRepository).findRecommendationCandidatesSortedByRelevance(eq(MentorStatus.ACTIVE), eq(MentorTagType.HELP_TOPIC), eq(MENTOR_ID), any(), any(PageRequest.class));
    }
}
