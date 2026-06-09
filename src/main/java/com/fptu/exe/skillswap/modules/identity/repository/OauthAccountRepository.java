package com.fptu.exe.skillswap.modules.identity.repository;

import com.fptu.exe.skillswap.modules.identity.domain.OauthAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OauthAccountRepository extends JpaRepository<OauthAccount, UUID> {
    Optional<OauthAccount> findByProviderAndProviderUserId(String provider, String providerUserId);
}
