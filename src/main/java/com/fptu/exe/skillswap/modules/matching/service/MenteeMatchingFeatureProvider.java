package com.fptu.exe.skillswap.modules.matching.service;

import java.util.UUID;

public interface MenteeMatchingFeatureProvider {

    MenteeMatchingFeatures getLatestFeatures(UUID userId);
}
