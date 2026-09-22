package com.fptu.exe.skillswap.modules.mentor.repository;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
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

    @Query("select mp.userId from MentorProfile mp where mp.isAvailable = true")
    List<UUID> findPublicMentorUserIds();

    @Query("select mp from MentorProfile mp where mp.userId = :userId")
    Optional<MentorProfile> findWithUserByUserId(@Param("userId") UUID userId);

    @Query(value = """
            select mp.userId
            from MentorProfile mp
            join com.fptu.exe.skillswap.modules.identity.domain.User u on u.id = mp.userId
            left join com.fptu.exe.skillswap.modules.identity.domain.StudentProfile sp on sp.userId = mp.userId
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
              and exists (select 1 from MentorService service where service.mentorProfile.userId = mp.userId
                          and service.isActive = true
                          and service.deliveryMode = com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceDeliveryMode.ONE_TO_ONE)
            """,
            countQuery = """
            select count(mp.userId)
            from MentorProfile mp
            join com.fptu.exe.skillswap.modules.identity.domain.User u on u.id = mp.userId
            left join com.fptu.exe.skillswap.modules.identity.domain.StudentProfile sp on sp.userId = mp.userId
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
              and exists (select 1 from MentorService service where service.mentorProfile.userId = mp.userId
                          and service.isActive = true
                          and service.deliveryMode = com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceDeliveryMode.ONE_TO_ONE)
            """)
    Page<UUID> findDiscoverableCandidateIds(
            @Param("mentorStatus") MentorStatus mentorStatus,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Query(value = """
            select mp.userId
            from MentorProfile mp
            join com.fptu.exe.skillswap.modules.identity.domain.User u on u.id = mp.userId
            left join com.fptu.exe.skillswap.modules.identity.domain.StudentProfile sp on sp.userId = mp.userId
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
              and exists (select 1 from MentorService service where service.mentorProfile.userId = mp.userId
                          and service.isActive = true
                          and service.deliveryMode = com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceDeliveryMode.ONE_TO_ONE)
              and (:keywordPattern is null or :normalizedKeywordPattern is null or (
                   lower(coalesce(u.fullName, '')) like :keywordPattern or
                   function('translate', lower(coalesce(u.fullName, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                   lower(coalesce(mp.headline, '')) like :keywordPattern or
                   function('translate', lower(coalesce(mp.headline, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                   lower(coalesce(mp.expertiseDescription, '')) like :keywordPattern or
                   function('translate', lower(coalesce(mp.expertiseDescription, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                   lower(coalesce(sp.bio, '')) like :keywordPattern or
                   function('translate', lower(coalesce(sp.bio, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
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
                         select 1 from com.fptu.exe.skillswap.modules.mentor.domain.MentorSubjectResult msr_search
                         where msr_search.mentorProfile.userId = mp.userId and
                               (
                                   lower(coalesce(msr_search.subjectCode, '')) like :keywordPattern or
                                   function('translate', lower(coalesce(msr_search.subjectCode, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                                   lower(coalesce(msr_search.subjectName, '')) like :keywordPattern or
                                   function('translate', lower(coalesce(msr_search.subjectName, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern
                               )
                   ) or
                   exists (
                         select 1 from com.fptu.exe.skillswap.modules.mentor.domain.MentorFeaturedProject mfp_search
                         where mfp_search.mentorProfile.userId = mp.userId and
                               (
                                   lower(coalesce(mfp_search.title, '')) like :keywordPattern or
                                   function('translate', lower(coalesce(mfp_search.title, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                                   lower(coalesce(mfp_search.content, '')) like :keywordPattern or
                                   function('translate', lower(coalesce(mfp_search.content, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                                   lower(coalesce(mfp_search.projectDescription, '')) like :keywordPattern or
                                   function('translate', lower(coalesce(mfp_search.projectDescription, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern
                               )
                   ) or
                   exists (
                         select 1 from com.fptu.exe.skillswap.modules.mentor.domain.MentorAchievement ma_search
                         where ma_search.mentorProfile.userId = mp.userId and
                               (
                                   lower(coalesce(ma_search.title, '')) like :keywordPattern or
                                   function('translate', lower(coalesce(ma_search.title, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                                   lower(coalesce(ma_search.awardDescription, '')) like :keywordPattern or
                                   function('translate', lower(coalesce(ma_search.awardDescription, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                                   lower(coalesce(ma_search.productHeader, '')) like :keywordPattern or
                                   function('translate', lower(coalesce(ma_search.productHeader, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                                   lower(coalesce(ma_search.productDescription, '')) like :keywordPattern or
                                   function('translate', lower(coalesce(ma_search.productDescription, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern
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
            join com.fptu.exe.skillswap.modules.identity.domain.User u on u.id = mp.userId
            left join com.fptu.exe.skillswap.modules.identity.domain.StudentProfile sp on sp.userId = mp.userId
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
              and exists (select 1 from MentorService service where service.mentorProfile.userId = mp.userId
                          and service.isActive = true
                          and service.deliveryMode = com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceDeliveryMode.ONE_TO_ONE)
              and (:keywordPattern is null or :normalizedKeywordPattern is null or (
                   lower(coalesce(u.fullName, '')) like :keywordPattern or
                   function('translate', lower(coalesce(u.fullName, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                   lower(coalesce(mp.headline, '')) like :keywordPattern or
                   function('translate', lower(coalesce(mp.headline, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                   lower(coalesce(mp.expertiseDescription, '')) like :keywordPattern or
                   function('translate', lower(coalesce(mp.expertiseDescription, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                   lower(coalesce(sp.bio, '')) like :keywordPattern or
                   function('translate', lower(coalesce(sp.bio, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
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
                        select 1 from com.fptu.exe.skillswap.modules.mentor.domain.MentorSubjectResult msr_search
                        where msr_search.mentorProfile.userId = mp.userId and
                              (
                                  lower(coalesce(msr_search.subjectCode, '')) like :keywordPattern or
                                  function('translate', lower(coalesce(msr_search.subjectCode, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                                  lower(coalesce(msr_search.subjectName, '')) like :keywordPattern or
                                  function('translate', lower(coalesce(msr_search.subjectName, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern
                              )
                   ) or
                  exists (
                        select 1 from com.fptu.exe.skillswap.modules.mentor.domain.MentorFeaturedProject mfp_search
                        where mfp_search.mentorProfile.userId = mp.userId and
                              (
                                  lower(coalesce(mfp_search.title, '')) like :keywordPattern or
                                  function('translate', lower(coalesce(mfp_search.title, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                                  lower(coalesce(mfp_search.content, '')) like :keywordPattern or
                                  function('translate', lower(coalesce(mfp_search.content, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                                  lower(coalesce(mfp_search.projectDescription, '')) like :keywordPattern or
                                  function('translate', lower(coalesce(mfp_search.projectDescription, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern
                              )
                   ) or
                  exists (
                        select 1 from com.fptu.exe.skillswap.modules.mentor.domain.MentorAchievement ma_search
                        where ma_search.mentorProfile.userId = mp.userId and
                              (
                                  lower(coalesce(ma_search.title, '')) like :keywordPattern or
                                  function('translate', lower(coalesce(ma_search.title, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                                  lower(coalesce(ma_search.awardDescription, '')) like :keywordPattern or
                                  function('translate', lower(coalesce(ma_search.awardDescription, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                                  lower(coalesce(ma_search.productHeader, '')) like :keywordPattern or
                                  function('translate', lower(coalesce(ma_search.productHeader, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern or
                                  lower(coalesce(ma_search.productDescription, '')) like :keywordPattern or
                                  function('translate', lower(coalesce(ma_search.productDescription, '')), :accentedCharacters, :plainCharacters) like :normalizedKeywordPattern
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
            @Param("keywordPattern") String keywordPattern,
            @Param("normalizedKeywordPattern") String normalizedKeywordPattern,
            @Param("accentedCharacters") String accentedCharacters,
            @Param("plainCharacters") String plainCharacters,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    // -----------------------------------------------------------------------
    @Query("""
            select new com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow(
                mp.userId,
                u.fullName,
                u.avatarUrl,
                mp.headline,
                mp.expertiseDescription,
                sp.bio,
                mp.foundationSupportLevel,
                mp.outputReviewSupportLevel,
                mp.directionSupportLevel,
                mp.isAvailable,
                mp.averageRating,
                mp.totalReviews,
                mp.totalCompletedSessions,
                mp.verifiedAt,
                mp.totalAcceptedBookings,
                mp.totalRejectedBookings,
                mp.totalMentorCancelledBookings,
                mp.lastActiveAt,
                null
            )
            from MentorProfile mp
            join com.fptu.exe.skillswap.modules.identity.domain.User u on u.id = mp.userId
            left join com.fptu.exe.skillswap.modules.identity.domain.StudentProfile sp on sp.userId = mp.userId
            where mp.userId in :mentorUserIds
            """)
    List<MentorDiscoveryQueryRow> findDiscoveryRowsByMentorUserIds(@Param("mentorUserIds") List<UUID> mentorUserIds);

    @Query("""
            select new com.fptu.exe.skillswap.modules.mentor.repository.MentorDiscoveryQueryRow(
                mp.userId, u.fullName, u.avatarUrl, mp.headline, mp.expertiseDescription,
                sp.bio, mp.foundationSupportLevel, mp.outputReviewSupportLevel, mp.directionSupportLevel,
                mp.isAvailable, mp.averageRating, mp.totalReviews, mp.totalCompletedSessions, mp.verifiedAt,
                mp.totalAcceptedBookings, mp.totalRejectedBookings, mp.totalMentorCancelledBookings, mp.lastActiveAt, null
            )
            from MentorProfile mp
            join com.fptu.exe.skillswap.modules.identity.domain.User u on u.id = mp.userId
            left join com.fptu.exe.skillswap.modules.identity.domain.StudentProfile sp on sp.userId = mp.userId
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
              and exists (select 1 from MentorService service where service.mentorProfile.userId = mp.userId
                          and service.isActive = true
                          and service.deliveryMode = com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceDeliveryMode.ONE_TO_ONE)
            order by mp.totalAcceptedBookings desc nulls last,
                     mp.lastActiveAt desc nulls last,
                     mp.verifiedAt desc nulls last,
                     mp.averageRating desc nulls last,
                     mp.totalCompletedSessions desc nulls last,
                     mp.updatedAt desc nulls last
            """)
    List<MentorDiscoveryQueryRow> findRecommendationCandidatesSortedByRelevance(
            @Param("mentorStatus") MentorStatus mentorStatus,
            @Param("excludedUserId") UUID excludedUserId,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Query("""
            select mp.userId
            from MentorProfile mp
            join com.fptu.exe.skillswap.modules.identity.domain.User u on u.id = mp.userId
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
                mp.totalCompletedSessions,
                mp.averageRating,
                mp.status,
                mp.createdAt
            )
            from MentorProfile mp
            join com.fptu.exe.skillswap.modules.identity.domain.User u on u.id = mp.userId
            left join com.fptu.exe.skillswap.modules.identity.domain.StudentProfile sp on sp.userId = mp.userId
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
            join com.fptu.exe.skillswap.modules.identity.domain.User u on u.id = mp.userId
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
    @Query("select mp from MentorProfile mp where mp.userId = :userId")
    Optional<MentorProfile> findWithUserByUserIdForUpdate(@Param("userId") UUID userId);

    long countByStatus(MentorStatus status);
}
