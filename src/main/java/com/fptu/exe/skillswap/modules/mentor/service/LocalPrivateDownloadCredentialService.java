package com.fptu.exe.skillswap.modules.mentor.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fptu.exe.skillswap.infrastructure.config.CacheProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.UUID;

@Service @Profile({"local","test"})
public class LocalPrivateDownloadCredentialService {
    private final Cache<String, Credential> credentials;

    public LocalPrivateDownloadCredentialService(CacheProperties cacheProperties) {
        CacheProperties.TimedCache settings = cacheProperties.getLocalPrivateDownloadCredential();
        this.credentials = Caffeine.newBuilder()
                .maximumSize(settings.getMaximumSize())
                .expireAfterWrite(settings.getTtl())
                .recordStats()
                .build();
    }
    public String issue(UUID resourceId, UUID userId) { String token=UUID.randomUUID().toString()+UUID.randomUUID().toString().replace("-",""); credentials.put(token,new Credential(resourceId,userId)); return token; }
    public boolean matches(String token, UUID resourceId, UUID userId) { Credential c=credentials.getIfPresent(token); return c!=null&&c.resourceId.equals(resourceId)&&c.userId.equals(userId); }
    public Credential credential(String token) { return credentials.getIfPresent(token); }
    public record Credential(UUID resourceId, UUID userId) {}
}
