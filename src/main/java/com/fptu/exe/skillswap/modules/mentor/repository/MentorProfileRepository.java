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
            select new com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow(
                mp.userId,
                u.fullName,
                u.avatarUrl,
                mp.headline,
                mp.expertiseDescription,
                mp.supportingSubjects,
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
                (
                  ((case when (:menteeSpecializationId is not null and specialization.id = :menteeSpecializationId) then 40.00 else 0.00 end) +
                  (case when (:menteeProgramId is not null and program.id = :menteeProgramId) then 18.00 else 0.00 end) +
                  (case when (:menteeCampusId is not null and campus.id = :menteeCampusId) then 10.00 else 0.00 end) +
                  (case when sp.isAlumni = true then 30.00
                        when (:menteeSemester is not null and sp.semester is not null and sp.semester > :menteeSemester) then 20.00
                        when (:menteeSemester is not null and sp.semester is not null and sp.semester = :menteeSemester) then 10.00
                        else 0.00 end) +
                  (case when mp.verifiedAt is null then 0.00
                        when timestampdiff(DAY, mp.verifiedAt, :now) < 3 then 10.00
                        else (0.20 * floor(timestampdiff(DAY, mp.verifiedAt, :now) / 7.00)) end) +
                  (0.10 * coalesce(mp.totalCompletedSessions, 0)) -
                  (0.05 * coalesce(mp.totalRejectedBookings, 0))) *
                  (case when coalesce(mp.totalReviews, 0) = 0 then 1.00
                       else (coalesce(mp.averageRating, 0.00) / 5.00) end)
                )
            )
            from MentorProfile mp
            join mp.user u
            left join com.fptu.exe.skillswap.modules.academic.domain.StudentProfile sp on sp.userId = mp.userId
            left join sp.campus campus
            left join sp.program program
            left join sp.specialization specialization
            where mp.status = :mentorStatus
              and mp.isAvailable = true
              and (:isAvailable is null or :isAvailable = true)
              and mp.verifiedAt is not null
              and mp.headline is not null and trim(mp.headline) <> ''
              and mp.expertiseDescription is not null and trim(mp.expertiseDescription) <> ''
              and mp.teachingMode is not null
              and mp.sessionDuration is not null
              and exists (
                    select 1 from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                    where mt.id.mentorUserId = mp.userId and mt.id.tagType = :helpTopicTagType
              )
              and (:keyword is null
                    or lower(u.fullName) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(mp.headline, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(mp.expertiseDescription, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(mp.supportingSubjects, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(sp.bio, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(specialization.nameVi, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(specialization.nameEn, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(specialization.code, '')) like lower(concat('%', :keyword, '%'))
                    or exists (
                        select 1
                        from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                        join mt.tag t
                        where mt.id.mentorUserId = mp.userId
                          and mt.id.tagType = :helpTopicTagType
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
              and (:hasTagFilter = false or exists (
                    select 1 from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                    where mt.id.mentorUserId = mp.userId
                      and mt.id.tagType = :helpTopicTagType
                      and mt.id.tagId in :tagIds
              ))
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
              and mp.isAvailable = true
              and (:isAvailable is null or :isAvailable = true)
              and mp.verifiedAt is not null
              and mp.headline is not null and trim(mp.headline) <> ''
              and mp.expertiseDescription is not null and trim(mp.expertiseDescription) <> ''
              and mp.teachingMode is not null
              and mp.sessionDuration is not null
              and exists (
                    select 1 from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                    where mt.id.mentorUserId = mp.userId and mt.id.tagType = :helpTopicTagType
              )
              and (:keyword is null
                    or lower(u.fullName) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(mp.headline, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(mp.expertiseDescription, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(mp.supportingSubjects, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(sp.bio, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(specialization.nameVi, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(specialization.nameEn, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(specialization.code, '')) like lower(concat('%', :keyword, '%'))
                    or exists (
                        select 1
                        from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                        join mt.tag t
                        where mt.id.mentorUserId = mp.userId
                          and mt.id.tagType = :helpTopicTagType
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
              and (:hasTagFilter = false or exists (
                    select 1 from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                    where mt.id.mentorUserId = mp.userId
                      and mt.id.tagType = :helpTopicTagType
                      and mt.id.tagId in :tagIds
              ))
            """)
    Page<MentorDiscoveryQueryRow> searchDiscoverableMentors(
            @Param("mentorStatus") MentorStatus mentorStatus,
            @Param("helpTopicTagType") MentorTagType helpTopicTagType,
            @Param("keyword") String keyword,
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
            Pageable pageable
    );

    @Query(value = """
            select new com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow(
                mp.userId, u.fullName, u.avatarUrl, mp.headline, mp.expertiseDescription, mp.supportingSubjects,
                mp.isAvailable, mp.averageRating, mp.totalReviews, mp.totalCompletedSessions, mp.teachingMode, mp.verifiedAt,
                campus.id, campus.name, program.id, program.nameVi, specialization.id, specialization.nameVi, sp.semester, sp.isAlumni,
                (
                  ((case when (:menteeSpecializationId is not null and specialization.id = :menteeSpecializationId) then 40.00 else 0.00 end) +
                  (case when (:menteeProgramId is not null and program.id = :menteeProgramId) then 18.00 else 0.00 end) +
                  (case when (:menteeCampusId is not null and campus.id = :menteeCampusId) then 10.00 else 0.00 end) +
                  (case when sp.isAlumni = true then 30.00
                        when (:menteeSemester is not null and sp.semester is not null and sp.semester > :menteeSemester) then 20.00
                        when (:menteeSemester is not null and sp.semester is not null and sp.semester = :menteeSemester) then 10.00
                        else 0.00 end) +
                  (case when mp.verifiedAt is null then 0.00
                        when timestampdiff(DAY, mp.verifiedAt, :now) < 3 then 10.00
                        else (0.20 * floor(timestampdiff(DAY, mp.verifiedAt, :now) / 7.00)) end) +
                  (0.10 * coalesce(mp.totalCompletedSessions, 0)) -
                  (0.05 * coalesce(mp.totalRejectedBookings, 0))) *
                  (case when coalesce(mp.totalReviews, 0) = 0 then 1.00
                       else (coalesce(mp.averageRating, 0.00) / 5.00) end)
                )
            )
            from MentorProfile mp
            join mp.user u
            left join com.fptu.exe.skillswap.modules.academic.domain.StudentProfile sp on sp.userId = mp.userId
            left join sp.campus campus
            left join sp.program program
            left join sp.specialization specialization
            where mp.status = :mentorStatus
              and mp.isAvailable = true
              and (:isAvailable is null or :isAvailable = true)
              and mp.verifiedAt is not null
              and mp.headline is not null and trim(mp.headline) <> ''
              and mp.expertiseDescription is not null and trim(mp.expertiseDescription) <> ''
              and mp.teachingMode is not null
              and mp.sessionDuration is not null
              and exists (select 1 from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt where mt.id.mentorUserId = mp.userId and mt.id.tagType = :helpTopicTagType)
              and (:keyword is null
                    or lower(u.fullName) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(mp.headline, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(mp.expertiseDescription, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(mp.supportingSubjects, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(sp.bio, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(specialization.nameVi, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(specialization.nameEn, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(specialization.code, '')) like lower(concat('%', :keyword, '%'))
                    or exists (
                        select 1 from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                        join mt.tag t
                        where mt.id.mentorUserId = mp.userId and mt.id.tagType = :helpTopicTagType
                          and (lower(t.nameVi) like lower(concat('%', :keyword, '%')) or lower(coalesce(t.nameEn, '')) like lower(concat('%', :keyword, '%')) or lower(t.code) like lower(concat('%', :keyword, '%')))
                    ))
              and (:campusId is null or campus.id = :campusId)
              and (:specializationId is null or specialization.id = :specializationId)
              and (:teachingMode is null or mp.teachingMode = :teachingMode)
              and (:hasTagFilter = false or exists (
                    select 1 from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                    where mt.id.mentorUserId = mp.userId and mt.id.tagType = :helpTopicTagType and mt.id.tagId in :tagIds
              ))
            order by
              (
                ((case when (:menteeSpecializationId is not null and specialization.id = :menteeSpecializationId) then 40.00 else 0.00 end) +
                (case when (:menteeProgramId is not null and program.id = :menteeProgramId) then 18.00 else 0.00 end) +
                (case when (:menteeCampusId is not null and campus.id = :menteeCampusId) then 10.00 else 0.00 end) +
                (case when sp.isAlumni = true then 30.00
                      when (:menteeSemester is not null and sp.semester is not null and sp.semester > :menteeSemester) then 20.00
                      when (:menteeSemester is not null and sp.semester is not null and sp.semester = :menteeSemester) then 10.00
                      else 0.00 end) +
                (case when mp.verifiedAt is null then 0.00
                      when timestampdiff(DAY, mp.verifiedAt, :now) < 3 then 10.00
                      else (0.20 * floor(timestampdiff(DAY, mp.verifiedAt, :now) / 7.00)) end) +
                (0.10 * coalesce(mp.totalCompletedSessions, 0)) -
                (0.05 * coalesce(mp.totalRejectedBookings, 0))) *
                (case when coalesce(mp.totalReviews, 0) = 0 then 1.00 else (coalesce(mp.averageRating, 0.00) / 5.00) end)
              ) desc,
              mp.totalCompletedSessions desc,
              mp.averageRating desc
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
              and mp.isAvailable = true
              and (:isAvailable is null or :isAvailable = true)
              and mp.verifiedAt is not null
              and mp.headline is not null and trim(mp.headline) <> ''
              and mp.expertiseDescription is not null and trim(mp.expertiseDescription) <> ''
              and mp.teachingMode is not null
              and mp.sessionDuration is not null
              and exists (select 1 from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt where mt.id.mentorUserId = mp.userId and mt.id.tagType = :helpTopicTagType)
              and (:keyword is null
                    or lower(u.fullName) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(mp.headline, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(mp.expertiseDescription, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(mp.supportingSubjects, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(sp.bio, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(specialization.nameVi, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(specialization.nameEn, '')) like lower(concat('%', :keyword, '%'))
                    or lower(coalesce(specialization.code, '')) like lower(concat('%', :keyword, '%'))
                    or exists (
                        select 1 from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                        join mt.tag t
                        where mt.id.mentorUserId = mp.userId and mt.id.tagType = :helpTopicTagType
                          and (lower(t.nameVi) like lower(concat('%', :keyword, '%')) or lower(coalesce(t.nameEn, '')) like lower(concat('%', :keyword, '%')) or lower(t.code) like lower(concat('%', :keyword, '%')))
                    ))
              and (:campusId is null or campus.id = :campusId)
              and (:specializationId is null or specialization.id = :specializationId)
              and (:teachingMode is null or mp.teachingMode = :teachingMode)
              and (:hasTagFilter = false or exists (
                    select 1 from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt
                    where mt.id.mentorUserId = mp.userId and mt.id.tagType = :helpTopicTagType and mt.id.tagId in :tagIds
              ))
            """)
    Page<MentorDiscoveryQueryRow> searchDiscoverableMentorsSortedByRelevance(
            @Param("mentorStatus") MentorStatus mentorStatus,
            @Param("helpTopicTagType") MentorTagType helpTopicTagType,
            @Param("keyword") String keyword,
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
            Pageable pageable
    );

    @Query("""
            select new com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow(
                mp.userId, u.fullName, u.avatarUrl, mp.headline, mp.expertiseDescription, mp.supportingSubjects,
                mp.isAvailable, mp.averageRating, mp.totalReviews, mp.totalCompletedSessions, mp.teachingMode, mp.verifiedAt,
                campus.id, campus.name, program.id, program.nameVi, specialization.id, specialization.nameVi, sp.semester, sp.isAlumni,
                (
                  ((case when (:menteeSpecializationId is not null and specialization.id = :menteeSpecializationId) then 40.00 else 0.00 end) +
                  (case when (:menteeProgramId is not null and program.id = :menteeProgramId) then 18.00 else 0.00 end) +
                  (case when (:menteeCampusId is not null and campus.id = :menteeCampusId) then 10.00 else 0.00 end) +
                  (case when sp.isAlumni = true then 30.00
                        when (:menteeSemester is not null and sp.semester is not null and sp.semester > :menteeSemester) then 20.00
                        when (:menteeSemester is not null and sp.semester is not null and sp.semester = :menteeSemester) then 10.00
                        else 0.00 end) +
                  (case when mp.verifiedAt is null then 0.00
                        when timestampdiff(DAY, mp.verifiedAt, :now) < 3 then 10.00
                        else (0.20 * floor(timestampdiff(DAY, mp.verifiedAt, :now) / 7.00)) end) +
                  (0.10 * coalesce(mp.totalCompletedSessions, 0)) -
                  (0.05 * coalesce(mp.totalRejectedBookings, 0))) *
                  (case when coalesce(mp.totalReviews, 0) = 0 then 1.00 else (coalesce(mp.averageRating, 0.00) / 5.00) end)
                )
            )
            from MentorProfile mp
            join mp.user u
            left join com.fptu.exe.skillswap.modules.academic.domain.StudentProfile sp on sp.userId = mp.userId
            left join sp.campus campus
            left join sp.program program
            left join sp.specialization specialization
            where mp.status = :mentorStatus
              and mp.userId <> :excludedUserId
              and mp.isAvailable = true
              and mp.verifiedAt is not null
              and mp.headline is not null and trim(mp.headline) <> ''
              and mp.expertiseDescription is not null and trim(mp.expertiseDescription) <> ''
              and mp.teachingMode is not null
              and mp.sessionDuration is not null
              and exists (select 1 from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt where mt.id.mentorUserId = mp.userId and mt.id.tagType = :helpTopicTagType)
            order by
              (
                ((case when (:menteeSpecializationId is not null and specialization.id = :menteeSpecializationId) then 40.00 else 0.00 end) +
                (case when (:menteeProgramId is not null and program.id = :menteeProgramId) then 18.00 else 0.00 end) +
                (case when (:menteeCampusId is not null and campus.id = :menteeCampusId) then 10.00 else 0.00 end) +
                (case when sp.isAlumni = true then 30.00
                      when (:menteeSemester is not null and sp.semester is not null and sp.semester > :menteeSemester) then 20.00
                      when (:menteeSemester is not null and sp.semester is not null and sp.semester = :menteeSemester) then 10.00
                      else 0.00 end) +
                (case when mp.verifiedAt is null then 0.00
                      when timestampdiff(DAY, mp.verifiedAt, :now) < 3 then 10.00
                      else (0.20 * floor(timestampdiff(DAY, mp.verifiedAt, :now) / 7.00)) end) +
                (0.10 * coalesce(mp.totalCompletedSessions, 0)) -
                (0.05 * coalesce(mp.totalRejectedBookings, 0))) *
                (case when coalesce(mp.totalReviews, 0) = 0 then 1.00 else (coalesce(mp.averageRating, 0.00) / 5.00) end)
              ) desc,
              mp.totalCompletedSessions desc,
              mp.averageRating desc
            """)
    List<MentorDiscoveryQueryRow> findRecommendationCandidatesSortedByRelevance(
            @Param("mentorStatus") MentorStatus mentorStatus,
            @Param("helpTopicTagType") MentorTagType helpTopicTagType,
            @Param("excludedUserId") UUID excludedUserId,
            @Param("menteeCampusId") UUID menteeCampusId,
            @Param("menteeProgramId") UUID menteeProgramId,
            @Param("menteeSpecializationId") UUID menteeSpecializationId,
            @Param("menteeSemester") Integer menteeSemester,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
