package com.fptu.exe.skillswap.modules.mentor.service;

import com.fptu.exe.skillswap.modules.mentor.dto.response.MentorDiscoveryDetailResponse;
import com.fptu.exe.skillswap.modules.mentor.port.MentorShareMetadata;
import com.fptu.exe.skillswap.modules.mentor.port.MentorShareQueryPort;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Adapts mentor's internal discovery use case to its small SEO-facing public contract. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MentorShareQueryPortImpl implements MentorShareQueryPort {

    private final MentorDiscoveryService mentorDiscoveryService;
    private final MentorProfileRepository mentorProfileRepository;

    @Override
    public MentorShareMetadata findShareMetadata(java.util.UUID mentorUserId) {
        MentorDiscoveryDetailResponse mentor = mentorDiscoveryService.getMentorDetail(mentorUserId);
        return new MentorShareMetadata(
                mentor.identity().displayName(),
                mentor.identity().headline(),
                mentor.identity().avatarUrl());
    }

    @Override
    public List<java.util.UUID> findPublicMentorUserIds() {
        return mentorProfileRepository.findPublicMentorUserIds();
    }
}
