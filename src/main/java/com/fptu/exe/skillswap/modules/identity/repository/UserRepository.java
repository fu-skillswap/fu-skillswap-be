package com.fptu.exe.skillswap.modules.identity.repository;

import com.fptu.exe.skillswap.modules.identity.domain.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") UUID userId);

    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findActiveByEmailIgnoreCase(@Param("email") String email);

    @Query(value = "SELECT u.* FROM users u JOIN oauth_accounts oa ON u.id = oa.user_id WHERE oa.provider = :provider AND oa.provider_user_id = :providerUserId", nativeQuery = true)
    Optional<User> findByOauthProviderAndProviderUserIdIncludingDeleted(@Param("provider") String provider, @Param("providerUserId") String providerUserId);

    @Query(value = "SELECT * FROM users WHERE email = :email", nativeQuery = true)
    Optional<User> findByEmailIncludingDeleted(@Param("email") String email);

    @Query("select r from User u join u.roles r where u.id = :userId")
    List<RoleCode> findRoleCodesByUserId(@Param("userId") UUID userId);

    @Query("select u from User u join u.roles r where r = :role")
    Page<User> findUsersByRole(@Param("role") RoleCode role, Pageable pageable);

    @Query("""
            select distinct u
            from User u
            where (:keywordPattern is null
                    or lower(u.email) like :keywordPattern
                    or lower(u.fullName) like :keywordPattern)
              and (:status is null or u.status = :status)
              and (:targetRole is null or :targetRole member of u.roles)
              and (:menteeRole member of u.roles or :mentorRole member of u.roles)
              and :adminRole not member of u.roles
              and :systemAdminRole not member of u.roles
            """)
    Page<User> searchAdminVisibleUsers(
            @Param("keywordPattern") String keywordPattern,
            @Param("status") UserStatus status,
            @Param("targetRole") RoleCode targetRole,
            @Param("menteeRole") RoleCode menteeRole,
            @Param("mentorRole") RoleCode mentorRole,
            @Param("adminRole") RoleCode adminRole,
            @Param("systemAdminRole") RoleCode systemAdminRole,
            Pageable pageable
    );
}
