package com.fptu.exe.skillswap.modules.booking.service.meeting;

import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class MeetingProviderFactory {

    private final Map<MeetingPlatform, MeetingProvider> providers = new EnumMap<>(MeetingPlatform.class);

    public MeetingProviderFactory(List<MeetingProvider> providerList) {
        for (MeetingProvider provider : providerList) {
            providers.put(provider.getPlatform(), provider);
        }
        for (MeetingPlatform platform : MeetingPlatform.values()) {
            providers.putIfAbsent(platform, new DefaultMeetingProvider(platform));
        }
    }

    public MeetingProvider getProvider(MeetingPlatform platform) {
        if (platform == null) {
            return providers.get(MeetingPlatform.OTHER);
        }
        return providers.getOrDefault(platform, new DefaultMeetingProvider(platform));
    }

    private static final class DefaultMeetingProvider implements MeetingProvider {
        private final MeetingPlatform platform;

        private DefaultMeetingProvider(MeetingPlatform platform) {
            this.platform = platform;
        }

        @Override
        public MeetingPlatform getPlatform() {
            return platform;
        }

        @Override
        public boolean isOnlinePlatform() {
            return platform != MeetingPlatform.OFFLINE;
        }
    }
}
