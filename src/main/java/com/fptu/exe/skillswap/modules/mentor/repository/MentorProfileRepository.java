package com.fptu.exe.skillswap.modules.mentor.repository;

import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MentorProfileRepository extends JpaRepository<MentorProfile, UUID> {

    @EntityGraph(attributePaths = {"user"})
    Optional<MentorProfile> findWithUserByUserId(UUID userId);

    @Query(value = """
            select mp.userId
            from MentorProfile mp
            join mp.user u
            left join com.fptu.exe.skillswap.modules.academic.domain.StudentProfile sp on sp.userId = mp.userId
            left join sp.campus campus
            left join sp.program program
            left join sp.specialization specialization
            where mp.status = :mentorStatus
              and u.status = com.fptu.exe.skillswap.modules.identity.domain.UserStatus.ACTIVE
              and mp.isAvailable = true
              and (mp.bookingSuspendedUntil is null or mp.bookingSuspendedUntil <= :now)
              and mp.verifiedAt is not null
              and mp.headline is not null and trim(mp.headline) <> ''
              and mp.expertiseDescription is not null and trim(mp.expertiseDescription) <> ''
              and mp.teachingMode is not null
              and mp.sessionDuration is not null
              and exists (
                    select 1
                    from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                    where mt.id.mentorUserId = mp.userId
                      and mt.id.tagType = :helpTopicTagType
              )
              and (:campusId is null or campus.id = :campusId)
              and (:specializationId is null or specialization.id = :specializationId)
              and (:teachingMode is null or mp.teachingMode = :teachingMode)
              and (:hasTagFilter = false or exists (
                    select 1
                    from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                    where mt.id.mentorUserId = mp.userId
                      and mt.id.tagType = :helpTopicTagType
                      and mt.id.tagId in :tagIds
              ))
            order by mp.averageRating desc nulls last, mp.totalCompletedSessions desc nulls last,
                     mp.updatedAt desc nulls last, mp.userId asc
            """,
            countQuery = """
            select count(mp.userId)
            from MentorProfile mp
            join mp.user u
            left join com.fptu.exe.skillswap.modules.academic.domain.StudentProfile sp on sp.userId = mp.userId
            left join sp.campus campus
            left join sp.program program
            left join sp.specialization specialization
            where mp.status = :mentorStatus
              and u.status = com.fptu.exe.skillswap.modules.identity.domain.UserStatus.ACTIVE
              and mp.isAvailable = true
              and (mp.bookingSuspendedUntil is null or mp.bookingSuspendedUntil <= :now)
              and mp.verifiedAt is not null
              and mp.headline is not null and trim(mp.headline) <> ''
              and mp.expertiseDescription is not null and trim(mp.expertiseDescription) <> ''
              and mp.teachingMode is not null
              and mp.sessionDuration is not null
              and exists (
                    select 1
                    from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                    where mt.id.mentorUserId = mp.userId
                      and mt.id.tagType = :helpTopicTagType
              )
              and (:campusId is null or campus.id = :campusId)
              and (:specializationId is null or specialization.id = :specializationId)
              and (:teachingMode is null or mp.teachingMode = :teachingMode)
              and (:hasTagFilter = false or exists (
                    select 1
                    from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                    where mt.id.mentorUserId = mp.userId
                      and mt.id.tagType = :helpTopicTagType
                      and mt.id.tagId in :tagIds
              ))
            """)
    Page<UUID> findDiscoverableCandidateIds(
            @Param("mentorStatus") MentorStatus mentorStatus,
            @Param("helpTopicTagType") MentorTagType helpTopicTagType,
            @Param("campusId") UUID campusId,
            @Param("specializationId") UUID specializationId,
            @Param("teachingMode") TeachingMode teachingMode,
            @Param("hasTagFilter") boolean hasTagFilter,
            @Param("tagIds") List<UUID> tagIds,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Query("""
            select mp.userId
            from MentorProfile mp
            join mp.user u
            left join com.fptu.exe.skillswap.modules.academic.domain.StudentProfile sp on sp.userId = mp.userId
            left join sp.campus campus
            left join sp.program program
            left join sp.specialization specialization
            where mp.status = :mentorStatus
              and u.status = com.fptu.exe.skillswap.modules.identity.domain.UserStatus.ACTIVE
              and com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR member of u.roles
              and com.fptu.exe.skillswap.shared.constant.RoleCode.ADMIN not member of u.roles
              and com.fptu.exe.skillswap.shared.constant.RoleCode.SYSTEM_ADMIN not member of u.roles
              and mp.isAvailable = true
              and (mp.bookingSuspendedUntil is null or mp.bookingSuspendedUntil <= :now)
              and mp.verifiedAt is not null
              and mp.headline is not null and trim(mp.headline) <> ''
              and mp.expertiseDescription is not null and trim(mp.expertiseDescription) <> ''
              and mp.teachingMode is not null
              and mp.sessionDuration is not null
              and exists (
                    select 1
                    from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                    where mt.id.mentorUserId = mp.userId
                      and mt.id.tagType = :helpTopicTagType
              )
              and (:campusId is null or campus.id = :campusId)
              and (:specializationId is null or specialization.id = :specializationId)
              and (:teachingMode is null or mp.teachingMode = :teachingMode)
              and (:hasTagFilter = false or exists (
                    select 1
                    from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                    where mt.id.mentorUserId = mp.userId
                      and mt.id.tagType = :helpTopicTagType
                      and mt.id.tagId in :tagIds
              ))
              and (:keywordPattern is null or (
                   lower(u.fullName) like lower(:keywordPattern) or
                   lower(mp.headline) like lower(:keywordPattern) or
                   lower(mp.expertiseDescription) like lower(:keywordPattern) or
                   lower(sp.bio) like lower(:keywordPattern) or
                   lower(campus.name) like lower(:keywordPattern) or
                   lower(program.nameVi) like lower(:keywordPattern) or
                   lower(specialization.nameVi) like lower(:keywordPattern) or
                   exists (
                        select 1 from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt_search
                        join com.fptu.exe.skillswap.modules.catalog.domain.Tag t on t.id = mt_search.id.tagId
                        where mt_search.id.mentorUserId = mp.userId and
                              (lower(t.nameVi) like lower(:keywordPattern) or lower(t.nameEn) like lower(:keywordPattern) or lower(t.code) like lower(:keywordPattern))
                   ) or
                   exists (
                        select 1 from com.fptu.exe.skillswap.modules.mentor.domain.MentorService ms_search
                        where ms_search.mentorProfile.userId = mp.userId and ms_search.isActive = true and
                              (lower(ms_search.title) like lower(:keywordPattern) or lower(ms_search.description) like lower(:keywordPattern))
                   )
              ))
            """)
    Page<UUID> findDiscoverableCandidateIdsWithKeyword(
            @Param("mentorStatus") MentorStatus mentorStatus,
            @Param("helpTopicTagType") MentorTagType helpTopicTagType,
            @Param("campusId") UUID campusId,
            @Param("specializationId") UUID specializationId,
            @Param("teachingMode") TeachingMode teachingMode,
            @Param("hasTagFilter") boolean hasTagFilter,
            @Param("tagIds") List<UUID> tagIds,
            @Param("keywordPattern") String keywordPattern,
            @Param("now") LocalDateTime now,
            Pageable pageable);


    @Query("""
            select new com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow(
                mp.userId,
                u.fullName,
                u.avatarUrl,
                mp.headline,
                mp.expertiseDescription,
                mp.supportingSubjects,
                sp.bio,
                mp.isAvailable,
                mp.averageRating,
                mp.totalReviews,
                mp.totalCompletedSessions,
                mp.teachingMode,
                mp.verifiedAt,
                campus.id,
                campus.name,
                program.id,
                program.nameVi,
                specialization.id,
                specialization.nameVi,
                sp.semester,
                sp.isAlumni,
                null
            )
            from MentorProfile mp
            join mp.user u
            left join com.fptu.exe.skillswap.modules.academic.domain.StudentProfile sp on sp.userId = mp.userId
            left join sp.campus campus
            left join sp.program program
            left join sp.specialization specialization
            where mp.userId in :mentorUserIds
            """)
    List<MentorDiscoveryQueryRow> findDiscoveryRowsByMentorUserIds(@Param("mentorUserIds") List<UUID> mentorUserIds);

    @Query("""
            select new com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow(
                mp.userId, u.fullName, u.avatarUrl, mp.headline, mp.expertiseDescription, mp.supportingSubjects,
                sp.bio, mp.isAvailable, mp.averageRating, mp.totalReviews, mp.totalCompletedSessions, mp.teachingMode, mp.verifiedAt,
                campus.id, campus.name, program.id, program.nameVi, specialization.id, specialization.nameVi, sp.semester, sp.isAlumni,
                null
            )
            from MentorProfile mp
            join mp.user u
            left join com.fptu.exe.skillswap.modules.academic.domain.StudentProfile sp on sp.userId = mp.userId
            left join sp.campus campus
            left join sp.program program
            left join sp.specialization specialization
            where mp.status = :mentorStatus
              and u.status = com.fptu.exe.skillswap.modules.identity.domain.UserStatus.ACTIVE
              and mp.userId <> :excludedUserId
              and mp.isAvailable = true
              and (mp.bookingSuspendedUntil is null or mp.bookingSuspendedUntil <= :now)
              and mp.verifiedAt is not null
              and mp.headline is not null and trim(mp.headline) <> ''
              and mp.expertiseDescription is not null and trim(mp.expertiseDescription) <> ''
              and mp.teachingMode is not null
              and mp.sessionDuration is not null
              and exists (
                    select 1
                    from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                    where mt.id.mentorUserId = mp.userId
                      and mt.id.tagType = :helpTopicTagType
              )
            order by mp.verifiedAt desc nulls last, mp.averageRating desc nulls last, mp.totalCompletedSessions desc nulls last, mp.updatedAt desc nulls last
            """)
    List<MentorDiscoveryQueryRow> findRecommendationCandidatesSortedByRelevance(
            @Param("mentorStatus") MentorStatus mentorStatus,
            @Param("helpTopicTagType") MentorTagType helpTopicTagType,
            @Param("excludedUserId") UUID excludedUserId,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Query("""
            select mp.userId
            from MentorProfile mp
            join mp.user u
            where mp.status = :mentorStatus
              and u.status = com.fptu.exe.skillswap.modules.identity.domain.UserStatus.ACTIVE
            order by mp.verifiedAt desc nulls last, mp.updatedAt desc nulls last
            """)
    List<UUID> findActiveMentorUserIds(@Param("mentorStatus") MentorStatus mentorStatus);

    @Query(value = """
            select mp
            from MentorProfile mp
            join mp.user u
            where (:status is null or mp.status = :status)
              and (:isAvailable is null or mp.isAvailable = :isAvailable)
              and (:keywordPattern is null
                    or lower(u.email) like :keywordPattern
                    or lower(u.fullName) like :keywordPattern
                    or lower(coalesce(mp.headline, '')) like :keywordPattern
                    or function('translate', lower(u.email), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern
                    or function('translate', lower(u.fullName), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern
                    or function('translate', lower(coalesce(mp.headline, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern)
            """, countQuery = """
            select count(mp.userId)
            from MentorProfile mp
            join mp.user u
            where (:status is null or mp.status = :status)
              and (:isAvailable is null or mp.isAvailable = :isAvailable)
              and (:keywordPattern is null
                    or lower(u.email) like :keywordPattern
                    or lower(u.fullName) like :keywordPattern
                    or lower(coalesce(mp.headline, '')) like :keywordPattern
                    or function('translate', lower(u.email), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern
                    or function('translate', lower(u.fullName), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern
                    or function('translate', lower(coalesce(mp.headline, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern)
            """)
    @EntityGraph(attributePaths = {"user"})
    Page<MentorProfile> searchForAdmin(
            @Param("keywordPattern") String keywordPattern,
            @Param("normalizedKeywordPattern") String normalizedKeywordPattern,
            @Param("accentedCharacters") String accentedCharacters,
            @Param("plainCharacters") String plainCharacters,
            @Param("status") MentorStatus status,
            @Param("isAvailable") Boolean isAvailable,
            Pageable pageable
    );

    List<MentorProfile> findByStatus(MentorStatus status);
}
