package com.fptu.exe.skillswap.modules.mentor.service.discovery;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.dto.request.MentorDiscoverySearchRequest;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscoveryCandidateProviderTest {
    @Test
    void searchPageUsesRequestedDatabasePageWithoutRecallWindow() {
        MentorProfileRepository repository = mock(MentorProfileRepository.class);
        UUID id = UUID.randomUUID();
        when(repository.findDiscoverableCandidateIdsWithKeyword(eq(MentorStatus.ACTIVE), eq("%java%"), eq("%java%"), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(id), PageRequest.of(2, 10), 35));
        DiscoveryCandidateProvider provider = new DiscoveryCandidateProvider(repository);
        MentorDiscoverySearchRequest request = new MentorDiscoverySearchRequest();
        request.setPage(2);
        request.setSize(10);

        CandidatePage page = provider.searchPage(request, "%java%", "%java%",
                List.of(new Sort.Order(Sort.Direction.DESC, "averageRating"), new Sort.Order(Sort.Direction.ASC, "userId")),
                LocalDateTime.now());

        assertEquals(List.of(id), page.candidateIds());
        assertEquals(35, page.totalCount());
        verify(repository).findDiscoverableCandidateIdsWithKeyword(eq(MentorStatus.ACTIVE), eq("%java%"), eq("%java%"), any(), any(), any(), any());
    }
}
