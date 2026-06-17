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
              and (:isAvailable is null or :isAvailable = true)
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
              and (:menteeCampusId is null or campus.id = :menteeCampusId)
              and (:menteeProgramId is null or program.id = :menteeProgramId)
              and (:menteeSpecializationId is null or specialization.id = :menteeSpecializationId)
              and (:menteeSemester is null or sp.semester is null or sp.semester >= :menteeSemester)
              and (:keywordPattern is null
                    or lower(coalesce(u.fullName, '')) like :keywordPattern
                    or lower(coalesce(mp.headline, '')) like :keywordPattern
                    or lower(coalesce(sp.bio, '')) like :keywordPattern
                    or lower(coalesce(mp.supportingSubjects, '')) like :keywordPattern
                    or lower(coalesce(mp.expertiseDescription, '')) like :keywordPattern
                    or lower(coalesce(program.nameVi, '')) like :keywordPattern
                    or lower(coalesce(specialization.nameVi, '')) like :keywordPattern
                    or exists (
                        select 1
                        from com.fptu.exe.skillswap.modules.mentor.domain.MentorService ms
                        where ms.mentorProfile.userId = mp.userId
                          and ms.isActive = true
                      and (
                           lower(coalesce(ms.title, '')) like :keywordPattern
                           or lower(coalesce(ms.description, '')) like :keywordPattern
                      )
                    )
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
            order by mp.verifiedAt desc nulls last, mp.averageRating desc nulls last, mp.totalCompletedSessions desc nulls last, mp.updatedAt desc nulls last
            """, countQuery = """
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
              and (:isAvailable is null or :isAvailable = true)
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
              and (:menteeCampusId is null or campus.id = :menteeCampusId)
              and (:menteeProgramId is null or program.id = :menteeProgramId)
              and (:menteeSpecializationId is null or specialization.id = :menteeSpecializationId)
              and (:menteeSemester is null or sp.semester is null or sp.semester >= :menteeSemester)
              and (:keywordPattern is null
                    or lower(coalesce(u.fullName, '')) like :keywordPattern
                    or lower(coalesce(mp.headline, '')) like :keywordPattern
                    or lower(coalesce(sp.bio, '')) like :keywordPattern
                    or lower(coalesce(mp.supportingSubjects, '')) like :keywordPattern
                    or lower(coalesce(mp.expertiseDescription, '')) like :keywordPattern
                    or lower(coalesce(program.nameVi, '')) like :keywordPattern
                    or lower(coalesce(specialization.nameVi, '')) like :keywordPattern
                    or exists (
                        select 1
                        from com.fptu.exe.skillswap.modules.mentor.domain.MentorService ms
                        where ms.mentorProfile.userId = mp.userId
                          and ms.isActive = true
                      and (
                           lower(coalesce(ms.title, '')) like :keywordPattern
                           or lower(coalesce(ms.description, '')) like :keywordPattern
                      )
                    )
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
    Page<UUID> searchDiscoverableMentorIds(
            @Param("mentorStatus") MentorStatus mentorStatus,
            @Param("helpTopicTagType") MentorTagType helpTopicTagType,
            @Param("keywordPattern") String keywordPattern,
            @Param("campusId") UUID campusId,
            @Param("specializationId") UUID specializationId,
            @Param("teachingMode") TeachingMode teachingMode,
            @Param("isAvailable") Boolean isAvailable,
            @Param("hasTagFilter") boolean hasTagFilter,
            @Param("tagIds") List<UUID> tagIds,
            @Param("menteeCampusId") UUID menteeCampusId,
            @Param("menteeProgramId") UUID menteeProgramId,
            @Param("menteeSpecializationId") UUID menteeSpecializationId,
            @Param("menteeSemester") Integer menteeSemester,
            @Param("now") LocalDateTime now,
            Pageable pageable);

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
              and (:isAvailable is null or :isAvailable = true)
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
              and (:menteeCampusId is null or campus.id = :menteeCampusId)
              and (:menteeProgramId is null or program.id = :menteeProgramId)
              and (:menteeSpecializationId is null or specialization.id = :menteeSpecializationId)
              and (:menteeSemester is null or sp.semester is null or sp.semester >= :menteeSemester)
              and (:keywordPattern is null
                    or lower(coalesce(u.fullName, '')) like :keywordPattern
                    or lower(coalesce(mp.headline, '')) like :keywordPattern
                    or lower(coalesce(sp.bio, '')) like :keywordPattern
                    or lower(coalesce(mp.supportingSubjects, '')) like :keywordPattern
                    or lower(coalesce(mp.expertiseDescription, '')) like :keywordPattern
                    or lower(coalesce(program.nameVi, '')) like :keywordPattern
                    or lower(coalesce(specialization.nameVi, '')) like :keywordPattern
                    or exists (
                        select 1
                        from com.fptu.exe.skillswap.modules.mentor.domain.MentorService ms
                        where ms.mentorProfile.userId = mp.userId
                          and ms.isActive = true
                          and (lower(coalesce(ms.title, '')) like :keywordPattern
                               or lower(coalesce(ms.description, '')) like :keywordPattern)
                    )
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
            order by mp.verifiedAt desc nulls last, mp.averageRating desc nulls last, mp.totalCompletedSessions desc nulls last, mp.updatedAt desc nulls last
            """, countQuery = """
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
              and (:isAvailable is null or :isAvailable = true)
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
              and (:menteeCampusId is null or campus.id = :menteeCampusId)
              and (:menteeProgramId is null or program.id = :menteeProgramId)
              and (:menteeSpecializationId is null or specialization.id = :menteeSpecializationId)
              and (:menteeSemester is null or sp.semester is null or sp.semester >= :menteeSemester)
              and (:keywordPattern is null
                    or lower(coalesce(u.fullName, '')) like :keywordPattern
                    or lower(coalesce(mp.headline, '')) like :keywordPattern
                    or lower(coalesce(sp.bio, '')) like :keywordPattern
                    or lower(coalesce(mp.supportingSubjects, '')) like :keywordPattern
                    or lower(coalesce(mp.expertiseDescription, '')) like :keywordPattern
                    or lower(coalesce(program.nameVi, '')) like :keywordPattern
                    or lower(coalesce(specialization.nameVi, '')) like :keywordPattern
                    or exists (
                        select 1
                        from com.fptu.exe.skillswap.modules.mentor.domain.MentorService ms
                        where ms.mentorProfile.userId = mp.userId
                          and ms.isActive = true
                          and (
                               lower(coalesce(ms.title, '')) like :keywordPattern
                               or lower(coalesce(ms.description, '')) like :keywordPattern
                          )
                    )
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
    Page<UUID> searchDiscoverableMentorIdsSortedByRelevance(
            @Param("mentorStatus") MentorStatus mentorStatus,
            @Param("helpTopicTagType") MentorTagType helpTopicTagType,
            @Param("keywordPattern") String keywordPattern,
            @Param("campusId") UUID campusId,
            @Param("specializationId") UUID specializationId,
            @Param("teachingMode") TeachingMode teachingMode,
            @Param("isAvailable") Boolean isAvailable,
            @Param("hasTagFilter") boolean hasTagFilter,
            @Param("tagIds") List<UUID> tagIds,
            @Param("menteeCampusId") UUID menteeCampusId,
            @Param("menteeProgramId") UUID menteeProgramId,
            @Param("menteeSpecializationId") UUID menteeSpecializationId,
            @Param("menteeSemester") Integer menteeSemester,
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
                    or lower(coalesce(mp.headline, '')) like :keywordPattern)
            """, countQuery = """
            select count(mp.userId)
            from MentorProfile mp
            join mp.user u
            where (:status is null or mp.status = :status)
              and (:isAvailable is null or mp.isAvailable = :isAvailable)
              and (:keywordPattern is null
                    or lower(u.email) like :keywordPattern
                    or lower(u.fullName) like :keywordPattern
                    or lower(coalesce(mp.headline, '')) like :keywordPattern)
            """)
    @EntityGraph(attributePaths = {"user"})
    Page<MentorProfile> searchForAdmin(
            @Param("keywordPattern") String keywordPattern,
            @Param("status") MentorStatus status,
            @Param("isAvailable") Boolean isAvailable,
            Pageable pageable
    );

    List<MentorProfile> findByStatus(MentorStatus status);
}
