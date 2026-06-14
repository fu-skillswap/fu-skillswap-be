package com.fptu.exe.skillswap.modules.mentor.repository;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MentorProfileRepository extends JpaRepository<MentorProfile, UUID> {

    @EntityGraph(attributePaths = {"user"})
    Optional<MentorProfile> findWithUserByUserId(UUID userId);

    @Query(value = """
            select new com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow(
                mp.userId,
                u.fullName,
                u.avatarUrl,
                mp.headline,
                mp.currentPosition,
                mp.currentCompany,
                mp.isAvailable,
                mp.averageRating,
                mp.totalReviews,
                mp.totalCompletedSessions,
                mp.hourlyRate,
                mp.teachingMode,
                mp.verifiedAt,
                campus.id,
                campus.name,
                program.id,
                program.nameVi,
                specialization.id,
                specialization.nameVi,
                sp.semester,
                sp.isAlumni
            )
            from MentorProfile mp
            join mp.user u
            left join com.fptu.exe.skillswap.modules.academic.domain.StudentProfile sp on sp.userId = mp.userId
            left join sp.campus campus
            left join sp.program program
            left join sp.specialization specialization
            where mp.status = :mentorStatus
              and mp.verifiedAt is not null
              and mp.headline is not null and trim(mp.headline) <> ''
              and mp.bio is not null and trim(mp.bio) <> ''
              and mp.currentPosition is not null and trim(mp.currentPosition) <> ''
              and mp.currentCompany is not null and trim(mp.currentCompany) <> ''
              and mp.yearsOfExperience is not null
              and mp.industry is not null and trim(mp.industry) <> ''
              and mp.teachingMode is not null
              and mp.sessionDuration is not null
              and mp.hourlyRate is not null
              and exists (
                    select 1 from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                    where mt.id.mentorUserId = mp.userId and mt.id.tagType = :expertiseTagType
              )
              and exists (
                    select 1 from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                    where mt.id.mentorUserId = mp.userId and mt.id.tagType = :helpTopicTagType
              )
              and (:keyword is null
                    or lower(u.fullName) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(mp.headline, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(mp.currentPosition, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(mp.currentCompany, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(mp.bio, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(mp.expertiseSummary, '')) like lower(concat('%', :keyword, '%'))
                    or exists (
                        select 1
                        from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                        join mt.tag t
                        where mt.id.mentorUserId = mp.userId
                          and mt.id.tagType = :expertiseTagType
                          and (
                              lower(t.nameVi) like lower(concat('%', :keyword, '%'))
                              or lower(coalesce(t.nameEn, '')) like lower(concat('%', :keyword, '%'))
                              or lower(t.code) like lower(concat('%', :keyword, '%'))
                          )
                    )
              )
              and (:campusId is null or campus.id = :campusId)
              and (:specializationId is null or specialization.id = :specializationId)
              and (:teachingMode is null or mp.teachingMode = :teachingMode)
              and (:isAvailable is null or mp.isAvailable = :isAvailable)
              and (:hasTagFilter = false or exists (
                    select 1 from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                    where mt.id.mentorUserId = mp.userId
                      and mt.id.tagType = :expertiseTagType
                      and mt.id.tagId in :tagIds
              ))
            """,
            countQuery = """
            select count(mp.userId)
            from MentorProfile mp
            join mp.user u
            left join com.fptu.exe.skillswap.modules.academic.domain.StudentProfile sp on sp.userId = mp.userId
            left join sp.campus campus
            left join sp.specialization specialization
            where mp.status = :mentorStatus
              and mp.verifiedAt is not null
              and mp.headline is not null and trim(mp.headline) <> ''
              and mp.bio is not null and trim(mp.bio) <> ''
              and mp.currentPosition is not null and trim(mp.currentPosition) <> ''
              and mp.currentCompany is not null and trim(mp.currentCompany) <> ''
              and mp.yearsOfExperience is not null
              and mp.industry is not null and trim(mp.industry) <> ''
              and mp.teachingMode is not null
              and mp.sessionDuration is not null
              and mp.hourlyRate is not null
              and exists (
                    select 1 from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                    where mt.id.mentorUserId = mp.userId and mt.id.tagType = :expertiseTagType
              )
              and exists (
                    select 1 from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                    where mt.id.mentorUserId = mp.userId and mt.id.tagType = :helpTopicTagType
              )
              and (:keyword is null
                    or lower(u.fullName) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(mp.headline, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(mp.currentPosition, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(mp.currentCompany, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(mp.bio, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(mp.expertiseSummary, '')) like lower(concat('%', :keyword, '%'))
                    or exists (
                        select 1
                        from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                        join mt.tag t
                        where mt.id.mentorUserId = mp.userId
                          and mt.id.tagType = :expertiseTagType
                          and (
                              lower(t.nameVi) like lower(concat('%', :keyword, '%'))
                              or lower(coalesce(t.nameEn, '')) like lower(concat('%', :keyword, '%'))
                              or lower(t.code) like lower(concat('%', :keyword, '%'))
                          )
                    )
              )
              and (:campusId is null or campus.id = :campusId)
              and (:specializationId is null or specialization.id = :specializationId)
              and (:teachingMode is null or mp.teachingMode = :teachingMode)
              and (:isAvailable is null or mp.isAvailable = :isAvailable)
              and (:hasTagFilter = false or exists (
                    select 1 from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                    where mt.id.mentorUserId = mp.userId
                      and mt.id.tagType = :expertiseTagType
                      and mt.id.tagId in :tagIds
              ))
            """)
    Page<MentorDiscoveryQueryRow> searchDiscoverableMentors(
            @Param("mentorStatus") MentorStatus mentorStatus,
            @Param("expertiseTagType") MentorTagType expertiseTagType,
            @Param("helpTopicTagType") MentorTagType helpTopicTagType,
            @Param("keyword") String keyword,
            @Param("campusId") UUID campusId,
            @Param("specializationId") UUID specializationId,
            @Param("teachingMode") TeachingMode teachingMode,
            @Param("isAvailable") Boolean isAvailable,
            @Param("hasTagFilter") boolean hasTagFilter,
            @Param("tagIds") List<UUID> tagIds,
            Pageable pageable
    );

    @Query("""
            select new com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow(
                mp.userId,
                u.fullName,
                u.avatarUrl,
                mp.headline,
                mp.currentPosition,
                mp.currentCompany,
                mp.isAvailable,
                mp.averageRating,
                mp.totalReviews,
                mp.totalCompletedSessions,
                mp.hourlyRate,
                mp.teachingMode,
                mp.verifiedAt,
                campus.id,
                campus.name,
                program.id,
                program.nameVi,
                specialization.id,
                specialization.nameVi,
                sp.semester,
                sp.isAlumni
            )
            from MentorProfile mp
            join mp.user u
            left join com.fptu.exe.skillswap.modules.academic.domain.StudentProfile sp on sp.userId = mp.userId
            left join sp.campus campus
            left join sp.program program
            left join sp.specialization specialization
            where mp.status = :mentorStatus
              and mp.userId <> :excludedUserId
              and mp.verifiedAt is not null
              and mp.headline is not null and trim(mp.headline) <> ''
              and mp.bio is not null and trim(mp.bio) <> ''
              and mp.currentPosition is not null and trim(mp.currentPosition) <> ''
              and mp.currentCompany is not null and trim(mp.currentCompany) <> ''
              and mp.yearsOfExperience is not null
              and mp.industry is not null and trim(mp.industry) <> ''
              and mp.teachingMode is not null
              and mp.sessionDuration is not null
              and mp.hourlyRate is not null
              and exists (
                    select 1 from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                    where mt.id.mentorUserId = mp.userId and mt.id.tagType = :expertiseTagType
              )
              and exists (
                    select 1 from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                    where mt.id.mentorUserId = mp.userId and mt.id.tagType = :helpTopicTagType
              )
            """)
    List<MentorDiscoveryQueryRow> findRecommendationCandidates(
            @Param("mentorStatus") MentorStatus mentorStatus,
            @Param("expertiseTagType") MentorTagType expertiseTagType,
            @Param("helpTopicTagType") MentorTagType helpTopicTagType,
            @Param("excludedUserId") UUID excludedUserId,
            Pageable pageable
    );
}
