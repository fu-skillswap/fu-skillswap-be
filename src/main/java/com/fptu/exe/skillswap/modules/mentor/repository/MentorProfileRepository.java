package com.fptu.exe.skillswap.modules.mentor.repository;

import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorListItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
              and (:keywordPattern is null or :normalizedKeywordPattern is null or (
                   lower(coalesce(u.fullName, '')) like :keywordPattern or
                   function('translate', lower(coalesce(u.fullName, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                   lower(coalesce(mp.headline, '')) like :keywordPattern or
                   function('translate', lower(coalesce(mp.headline, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                   lower(coalesce(mp.expertiseDescription, '')) like :keywordPattern or
                   function('translate', lower(coalesce(mp.expertiseDescription, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                   lower(coalesce(mp.supportingSubjects, '')) like :keywordPattern or
                   function('translate', lower(coalesce(mp.supportingSubjects, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                   lower(coalesce(sp.bio, '')) like :keywordPattern or
                   function('translate', lower(coalesce(sp.bio, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                   lower(coalesce(campus.name, '')) like :keywordPattern or
                   function('translate', lower(coalesce(campus.name, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                   lower(coalesce(program.nameVi, '')) like :keywordPattern or
                   function('translate', lower(coalesce(program.nameVi, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                   lower(coalesce(specialization.nameVi, '')) like :keywordPattern or
                   function('translate', lower(coalesce(specialization.nameVi, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                   exists (
                         select 1 from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt_search
                         join com.fptu.exe.skillswap.modules.catalog.domain.Tag t on t.id = mt_search.id.tagId
                         where mt_search.id.mentorUserId = mp.userId and
                               (
                                   lower(coalesce(t.nameVi, '')) like :keywordPattern or
                                   function('translate', lower(coalesce(t.nameVi, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                                   lower(coalesce(t.nameEn, '')) like :keywordPattern or
                                   function('translate', lower(coalesce(t.nameEn, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                                   lower(coalesce(t.code, '')) like :keywordPattern or
                                   function('translate', lower(coalesce(t.code, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern
                               )
                   ) or
                   exists (
                         select 1 from com.fptu.exe.skillswap.modules.mentor.domain.MentorService ms_search
                         where ms_search.mentorProfile.userId = mp.userId and ms_search.isActive = true and
                               (
                                   lower(coalesce(ms_search.title, '')) like :keywordPattern or
                                   function('translate', lower(coalesce(ms_search.title, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                                   lower(coalesce(ms_search.description, '')) like :keywordPattern or
                                   function('translate', lower(coalesce(ms_search.description, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                                   lower(coalesce(ms_search.expectedOutcome, '')) like :keywordPattern or
                                   function('translate', lower(coalesce(ms_search.expectedOutcome, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern
                               )
                    )
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
              and (:keywordPattern is null or :normalizedKeywordPattern is null or (
                   lower(coalesce(u.fullName, '')) like :keywordPattern or
                   function('translate', lower(coalesce(u.fullName, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                   lower(coalesce(mp.headline, '')) like :keywordPattern or
                   function('translate', lower(coalesce(mp.headline, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                   lower(coalesce(mp.expertiseDescription, '')) like :keywordPattern or
                   function('translate', lower(coalesce(mp.expertiseDescription, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                   lower(coalesce(mp.supportingSubjects, '')) like :keywordPattern or
                   function('translate', lower(coalesce(mp.supportingSubjects, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                   lower(coalesce(sp.bio, '')) like :keywordPattern or
                   function('translate', lower(coalesce(sp.bio, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                   lower(coalesce(campus.name, '')) like :keywordPattern or
                   function('translate', lower(coalesce(campus.name, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                   lower(coalesce(program.nameVi, '')) like :keywordPattern or
                   function('translate', lower(coalesce(program.nameVi, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                   lower(coalesce(specialization.nameVi, '')) like :keywordPattern or
                   function('translate', lower(coalesce(specialization.nameVi, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                   exists (
                        select 1 from com.fptu.exe.skillswap.modules.catalog.domain.MentorTag mt_search
                        join com.fptu.exe.skillswap.modules.catalog.domain.Tag t on t.id = mt_search.id.tagId
                        where mt_search.id.mentorUserId = mp.userId and
                              (
                                  lower(coalesce(t.nameVi, '')) like :keywordPattern or
                                  function('translate', lower(coalesce(t.nameVi, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                                  lower(coalesce(t.nameEn, '')) like :keywordPattern or
                                  function('translate', lower(coalesce(t.nameEn, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                                  lower(coalesce(t.code, '')) like :keywordPattern or
                                  function('translate', lower(coalesce(t.code, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern
                              )
                   ) or
                  exists (
                        select 1 from com.fptu.exe.skillswap.modules.mentor.domain.MentorService ms_search
                        where ms_search.mentorProfile.userId = mp.userId and ms_search.isActive = true and
                              (
                                  lower(coalesce(ms_search.title, '')) like :keywordPattern or
                                  function('translate', lower(coalesce(ms_search.title, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                                  lower(coalesce(ms_search.description, '')) like :keywordPattern or
                                  function('translate', lower(coalesce(ms_search.description, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                                  lower(coalesce(ms_search.expectedOutcome, '')) like :keywordPattern or
                                  function('translate', lower(coalesce(ms_search.expectedOutcome, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern
                              )
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
            @Param("normalizedKeywordPattern") String normalizedKeywordPattern,
            @Param("accentedCharacters") String accentedCharacters,
            @Param("plainCharacters") String plainCharacters,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    // -----------------------------------------------------------------------
    // Phase S1: PostgreSQL FTS native queries (tsvector + GIN index)
    // These use PostgreSQL-specific syntax (@@, plainto_tsquery, ANY).
    // Guard: MentorDiscoveryService.isPostgres() must return true before calling.
    // H2 tests never reach these methods.
    // -----------------------------------------------------------------------

    @Query(value = """
            SELECT mp.user_id
            FROM mentor_profiles mp
            JOIN users u ON u.id = mp.user_id
            LEFT JOIN student_profiles sp ON sp.user_id = mp.user_id
            WHERE mp.status = 'ACTIVE'
              AND u.status = 'ACTIVE'
              AND mp.is_available = true
              AND (mp.booking_suspended_until IS NULL OR mp.booking_suspended_until <= CAST(:now AS timestamp))
              AND mp.verified_at IS NOT NULL
              AND mp.headline IS NOT NULL AND trim(mp.headline) <> ''
              AND mp.expertise_description IS NOT NULL AND trim(mp.expertise_description) <> ''
              AND mp.teaching_mode IS NOT NULL
              AND mp.session_duration IS NOT NULL
              AND EXISTS (
                  SELECT 1 FROM mentor_tags mt0
                  WHERE mt0.mentor_user_id = mp.user_id AND mt0.tag_type = 'HELP_TOPIC'
              )
              AND (:campusId IS NULL OR sp.campus_id = CAST(:campusId AS uuid))
              AND (:specializationId IS NULL OR sp.specialization_id = CAST(:specializationId AS uuid))
              AND (:teachingMode IS NULL OR mp.teaching_mode = CAST(:teachingMode AS varchar))
              AND (:hasTagFilter = false OR EXISTS (
                  SELECT 1 FROM mentor_tags mt1
                  WHERE mt1.mentor_user_id = mp.user_id AND mt1.tag_type = 'HELP_TOPIC'
                    AND mt1.tag_id = ANY(CAST(:tagIds AS uuid[]))
              ))
              AND (
                  mp.search_vector @@ plainto_tsquery('simple', :keyword)
                  OR EXISTS (
                      SELECT 1 FROM mentor_tags mt2
                      JOIN tags t ON t.id = mt2.tag_id
                      WHERE mt2.mentor_user_id = mp.user_id
                        AND to_tsvector(
                            'simple',
                            translate(
                                lower(coalesce(t.name_vi,'') || ' ' || coalesce(t.name_en,'') || ' ' || coalesce(t.code,'')),
                                'àáạảãăắằẳẵặâấầẩẫậđèéẹẻẽêếềểễệìíịỉĩòóọỏõôốồổỗộơớờởỡợùúụủũưứừửữựỳýỵỷỹ',
                                'aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyy'
                            )
                        )
                            @@ plainto_tsquery('simple', :keyword)
                  )
                  OR EXISTS (
                      SELECT 1 FROM mentor_services ms
                      WHERE ms.mentor_user_id = mp.user_id AND ms.is_active = true
                        AND to_tsvector(
                            'simple',
                            translate(
                                lower(coalesce(ms.title,'') || ' ' || coalesce(ms.description,'') || ' ' || coalesce(ms.expected_outcome,'')),
                                'àáạảãăắằẳẵặâấầẩẫậđèéẹẻẽêếềểễệìíịỉĩòóọỏõôốồổỗộơớờởỡợùúụủũưứừửữựỳýỵỷỹ',
                                'aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyy'
                            )
                        )
                            @@ plainto_tsquery('simple', :keyword)
                  )
              )
            ORDER BY mp.average_rating DESC NULLS LAST,
                     mp.total_completed_sessions DESC NULLS LAST,
                     mp.verified_at DESC NULLS LAST,
                     mp.user_id ASC
            LIMIT :limitSize OFFSET :offsetVal
            """, nativeQuery = true)
    List<UUID> findDiscoverableCandidateIdsByFts(
            @Param("keyword") String keyword,
            @Param("campusId") UUID campusId,
            @Param("specializationId") UUID specializationId,
            @Param("teachingMode") String teachingMode,
            @Param("hasTagFilter") boolean hasTagFilter,
            @Param("tagIds") String tagIds,
            @Param("now") LocalDateTime now,
            @Param("limitSize") int limitSize,
            @Param("offsetVal") int offsetVal);

    @Query(value = """
            SELECT COUNT(mp.user_id)
            FROM mentor_profiles mp
            JOIN users u ON u.id = mp.user_id
            LEFT JOIN student_profiles sp ON sp.user_id = mp.user_id
            WHERE mp.status = 'ACTIVE'
              AND u.status = 'ACTIVE'
              AND mp.is_available = true
              AND (mp.booking_suspended_until IS NULL OR mp.booking_suspended_until <= CAST(:now AS timestamp))
              AND mp.verified_at IS NOT NULL
              AND mp.headline IS NOT NULL AND trim(mp.headline) <> ''
              AND mp.expertise_description IS NOT NULL AND trim(mp.expertise_description) <> ''
              AND mp.teaching_mode IS NOT NULL
              AND mp.session_duration IS NOT NULL
              AND EXISTS (
                  SELECT 1 FROM mentor_tags mt0
                  WHERE mt0.mentor_user_id = mp.user_id AND mt0.tag_type = 'HELP_TOPIC'
              )
              AND (:campusId IS NULL OR sp.campus_id = CAST(:campusId AS uuid))
              AND (:specializationId IS NULL OR sp.specialization_id = CAST(:specializationId AS uuid))
              AND (:teachingMode IS NULL OR mp.teaching_mode = CAST(:teachingMode AS varchar))
              AND (:hasTagFilter = false OR EXISTS (
                  SELECT 1 FROM mentor_tags mt1
                  WHERE mt1.mentor_user_id = mp.user_id AND mt1.tag_type = 'HELP_TOPIC'
                    AND mt1.tag_id = ANY(CAST(:tagIds AS uuid[]))
              ))
              AND (
                  mp.search_vector @@ plainto_tsquery('simple', :keyword)
                  OR EXISTS (
                      SELECT 1 FROM mentor_tags mt2
                      JOIN tags t ON t.id = mt2.tag_id
                      WHERE mt2.mentor_user_id = mp.user_id
                        AND to_tsvector(
                            'simple',
                            translate(
                                lower(coalesce(t.name_vi,'') || ' ' || coalesce(t.name_en,'') || ' ' || coalesce(t.code,'')),
                                'àáạảãăắằẳẵặâấầẩẫậđèéẹẻẽêếềểễệìíịỉĩòóọỏõôốồổỗộơớờởỡợùúụủũưứừửữựỳýỵỷỹ',
                                'aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyy'
                            )
                        )
                            @@ plainto_tsquery('simple', :keyword)
                  )
                  OR EXISTS (
                      SELECT 1 FROM mentor_services ms
                      WHERE ms.mentor_user_id = mp.user_id AND ms.is_active = true
                        AND to_tsvector(
                            'simple',
                            translate(
                                lower(coalesce(ms.title,'') || ' ' || coalesce(ms.description,'') || ' ' || coalesce(ms.expected_outcome,'')),
                                'àáạảãăắằẳẵặâấầẩẫậđèéẹẻẽêếềểễệìíịỉĩòóọỏõôốồổỗộơớờởỡợùúụủũưứừửữựỳýỵỷỹ',
                                'aaaaaaaaaaaaaaaaadeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyy'
                            )
                        )
                            @@ plainto_tsquery('simple', :keyword)
                  )
              )
            """, nativeQuery = true)
    long countDiscoverableCandidatesByFts(
            @Param("keyword") String keyword,
            @Param("campusId") UUID campusId,
            @Param("specializationId") UUID specializationId,
            @Param("teachingMode") String teachingMode,
            @Param("hasTagFilter") boolean hasTagFilter,
            @Param("tagIds") String tagIds,
            @Param("now") LocalDateTime now);


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
              and com.fptu.exe.skillswap.shared.constant.RoleCode.MENTOR member of u.roles
              and com.fptu.exe.skillswap.shared.constant.RoleCode.ADMIN not member of u.roles
              and com.fptu.exe.skillswap.shared.constant.RoleCode.SYSTEM_ADMIN not member of u.roles
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
            select new com.fptu.exe.skillswap.modules.mentor.dto.response.AdminMentorListItemResponse(
                mp.userId,
                u.fullName,
                u.email,
                u.avatarUrl,
                program.code,
                mp.totalCompletedSessions,
                mp.averageRating,
                mp.status,
                mp.createdAt
            )
            from MentorProfile mp
            join mp.user u
            left join com.fptu.exe.skillswap.modules.academic.domain.StudentProfile sp on sp.userId = mp.userId
            left join sp.program program
            where ((:status is not null and mp.status = :status)
                or (:status is null and mp.status <> com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus.DRAFT))
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
            where ((:status is not null and mp.status = :status)
                or (:status is null and mp.status <> com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus.DRAFT))
              and (:isAvailable is null or mp.isAvailable = :isAvailable)
              and (:keywordPattern is null
                    or lower(u.email) like :keywordPattern
                    or lower(u.fullName) like :keywordPattern
                    or lower(coalesce(mp.headline, '')) like :keywordPattern
                    or function('translate', lower(u.email), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern
                    or function('translate', lower(u.fullName), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern
                    or function('translate', lower(coalesce(mp.headline, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern)
            """)
    Page<AdminMentorListItemResponse> searchForAdmin(
            @Param("keywordPattern") String keywordPattern,
            @Param("normalizedKeywordPattern") String normalizedKeywordPattern,
            @Param("accentedCharacters") String accentedCharacters,
            @Param("plainCharacters") String plainCharacters,
            @Param("status") MentorStatus status,
            @Param("isAvailable") Boolean isAvailable,
            Pageable pageable
    );

    List<MentorProfile> findByStatus(MentorStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select mp from MentorProfile mp where mp.userId = :userId")
    Optional<MentorProfile> findByIdForUpdate(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"user"})
    @Query("select mp from MentorProfile mp where mp.userId = :userId")
    Optional<MentorProfile> findWithUserByUserIdForUpdate(@Param("userId") UUID userId);
}
