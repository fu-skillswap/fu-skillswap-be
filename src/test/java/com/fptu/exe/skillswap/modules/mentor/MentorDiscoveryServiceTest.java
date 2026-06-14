package com.fptu.exe.skillswap.modules.mentor;

import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.academic.domain.Campus;
import com.fptu.exe.skillswap.modules.academic.domain.CampusCode;
import com.fptu.exe.skillswap.modules.academic.domain.Specialization;
import com.fptu.exe.skillswap.modules.academic.domain.StudentProfile;
import com.fptu.exe.skillswap.modules.academic.repository.StudentProfileRepository;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTag;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagId;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.catalog.repository.MentorTagRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorDiscoveryCardResponse;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorDiscoverySearchRequest;
import com.fptu.exe.skillswap.modules.mentor.dto.MentorRecommendationResponse;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.modules.mentor.service.MentorDiscoveryService;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorDiscoveryServiceTest {

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @Mock
    private MentorTagRepository mentorTagRepository;

    @Mock
    private StudentProfileRepository studentProfileRepository;

    @InjectMocks
    private MentorDiscoveryService mentorDiscoveryService;

    private UUID mentorId;
    private UUID menteeId;
    private UUID campusId;
    private UUID programId;
    private UUID specializationId;
    private MentorDiscoveryQueryRow mentorRow;

    @BeforeEach
    void setUp() {
        mentorId = UUID.randomUUID();
        menteeId = UUID.randomUUID();
        campusId = UUID.randomUUID();
        programId = UUID.randomUUID();
        specializationId = UUID.randomUUID();

        mentorRow = new MentorDiscoveryQueryRow(
                mentorId,
                "Mentor One",
                "https://example.com/avatar.jpg",
                "Java Mentor",
                "Backend Engineer",
                "FPT Software",
                true,
                new BigDecimal("4.70"),
                10,
                18,
                new BigDecimal("150000"),
                TeachingMode.ONLINE,
                java.time.LocalDateTime.now().minusDays(10),
                campusId,
                "HCM",
                programId,
                "CNTT",
                specializationId,
                "KTPM",
                7,
                false
        );
    }

    @Test
    void searchMentors_shouldReturnPagedCardsWithExpertiseTags() {
        MentorDiscoverySearchRequest request = new MentorDiscoverySearchRequest();
        request.setKeyword("java");
        request.setTagIds(List.of(UUID.randomUUID()));

        when(mentorProfileRepository.searchDiscoverableMentors(
                eq(MentorStatus.ACTIVE),
                eq(MentorTagType.EXPERTISE),
                eq(MentorTagType.HELP_TOPIC),
                eq("java"),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(true),
                eq(request.getTagIds()),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(mentorRow)));

        when(mentorTagRepository.findByIdMentorUserIdInAndIdTagTypeIn(eq(List.of(mentorId)), any(Collection.class)))
                .thenReturn(List.of(expertiseTag(mentorId, "JAVA_BACKEND", "Java Backend")));

        PageResponse<MentorDiscoveryCardResponse> response = mentorDiscoveryService.searchMentors(menteeId, request);

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent()).hasSize(1);
        org.mockito.Mockito.verify(studentProfileRepository).findWithDetailsByUserId(menteeId);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(mentorProfileRepository).searchDiscoverableMentors(
                eq(MentorStatus.ACTIVE),
                eq(MentorTagType.EXPERTISE),
                eq(MentorTagType.HELP_TOPIC),
                eq("java"),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(true),
                eq(request.getTagIds()),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(12);
    }

    @Test
    void getRecommendations_sameAcademicContext_shouldRankMentorHigher() {
        StudentProfile menteeProfile = StudentProfile.builder()
                .userId(menteeId)
                .campus(Campus.builder().id(campusId).code(CampusCode.HCM).name("HCM").build())
                .program(AcademicProgram.builder().id(programId).code("CNTT").nameVi("CNTT").build())
                .specialization(Specialization.builder().id(specializationId).code("KTPM").nameVi("KTPM").build())
                .semester(5)
                .build();

        when(studentProfileRepository.findWithDetailsByUserId(menteeId)).thenReturn(Optional.of(menteeProfile));
        when(mentorProfileRepository.findRecommendationCandidates(
                eq(MentorStatus.ACTIVE),
                eq(MentorTagType.EXPERTISE),
                eq(MentorTagType.HELP_TOPIC),
                eq(menteeId),
                any(Pageable.class)
        )).thenReturn(List.of(mentorRow));
        when(mentorTagRepository.findByIdMentorUserIdInAndIdTagTypeIn(eq(List.of(mentorId)), any(Collection.class)))
                .thenReturn(List.of(expertiseTag(mentorId, "JAVA_BACKEND", "Java Backend")));

        List<MentorRecommendationResponse> response = mentorDiscoveryService.getRecommendations(menteeId, 8);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).matchScore()).isGreaterThan(new BigDecimal("80"));
        assertThat(response.get(0).matchReasons()).contains("Cùng chuyên ngành với mentee");
    }

    @Test
    void searchMentors_shouldRerankQueriedListBySmartMatching() {
        UUID otherMentorId = UUID.randomUUID();
        StudentProfile menteeProfile = StudentProfile.builder()
                .userId(menteeId)
                .campus(Campus.builder().id(campusId).code(CampusCode.HCM).name("HCM").build())
                .program(AcademicProgram.builder().id(programId).code("CNTT").nameVi("CNTT").build())
                .specialization(Specialization.builder().id(specializationId).code("KTPM").nameVi("KTPM").build())
                .semester(5)
                .build();

        MentorDiscoveryQueryRow lowerMatchMentor = new MentorDiscoveryQueryRow(
                otherMentorId,
                "Mentor Two",
                "https://example.com/avatar-2.jpg",
                "General Mentor",
                "Engineer",
                "Company B",
                true,
                new BigDecimal("5.00"),
                20,
                40,
                new BigDecimal("100000"),
                TeachingMode.ONLINE,
                LocalDateTime.now().minusDays(1),
                UUID.randomUUID(),
                "DN",
                UUID.randomUUID(),
                "Biz",
                UUID.randomUUID(),
                "Marketing",
                3,
                false
        );

        when(studentProfileRepository.findWithDetailsByUserId(menteeId)).thenReturn(Optional.of(menteeProfile));
        when(mentorProfileRepository.searchDiscoverableMentors(
                eq(MentorStatus.ACTIVE),
                eq(MentorTagType.EXPERTISE),
                eq(MentorTagType.HELP_TOPIC),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(false),
                any(List.class),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(lowerMatchMentor, mentorRow)));
        when(mentorTagRepository.findByIdMentorUserIdInAndIdTagTypeIn(any(Collection.class), any(Collection.class)))
                .thenReturn(List.of(
                        expertiseTag(mentorId, "JAVA_BACKEND", "Java Backend"),
                        expertiseTag(otherMentorId, "CAREER", "Career")
                ));

        PageResponse<MentorDiscoveryCardResponse> response = mentorDiscoveryService.searchMentors(menteeId, new MentorDiscoverySearchRequest());

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).displayName()).isEqualTo("Mentor One");
        assertThat(response.getContent().get(1).displayName()).isEqualTo("Mentor Two");
    }

    private MentorTag expertiseTag(UUID mentorUserId, String code, String nameVi) {
        return MentorTag.builder()
                .id(new MentorTagId(mentorUserId, UUID.randomUUID(), MentorTagType.EXPERTISE))
                .tag(Tag.builder()
                        .id(UUID.randomUUID())
                        .code(code)
                        .nameVi(nameVi)
                        .type(TagType.TECH_SKILL)
                        .build())
                .isPrimary(false)
                .build();
    }
}
