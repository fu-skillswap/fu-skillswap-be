package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.GroupSession;
import com.fptu.exe.skillswap.modules.booking.domain.GroupSessionRegistrationStatus;
import com.fptu.exe.skillswap.modules.booking.domain.GroupSessionStatus;
import com.fptu.exe.skillswap.modules.booking.dto.response.GroupSessionDiscoveryResponse;
import com.fptu.exe.skillswap.modules.booking.repository.GroupSessionRepository;
import com.fptu.exe.skillswap.shared.cursor.CursorCodec;
import com.fptu.exe.skillswap.shared.cursor.CursorTokenPayload;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupSessionDiscoveryService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private final GroupSessionRepository groupSessionRepository;
    private final CursorCodec cursorCodec;

    @Transactional(readOnly = true)
    public CursorPageResponse<GroupSessionDiscoveryResponse> list(
            UUID mentorUserId, UUID serviceId, Instant from, String cursor, Integer limit) {
        int resolvedLimit = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(1, limit), MAX_LIMIT);
        LocalDateTime fromAt = from == null ? utcNow() : LocalDateTime.ofInstant(from, ZoneOffset.UTC);
        String filterHash = hash("group-sessions|" + mentorUserId + "|" + serviceId + "|" + fromAt);
        DecodedCursor decoded = decode(cursor, filterHash);
        List<GroupSession> window = groupSessionRepository.findPublicWindow(
                GroupSessionStatus.OPEN, GroupSessionRegistrationStatus.OPEN, fromAt, mentorUserId, serviceId,
                decoded.startAt(), decoded.id(), PageRequest.of(0, resolvedLimit + 1));
        boolean hasNext = window.size() > resolvedLimit;
        List<GroupSession> items = hasNext ? window.subList(0, resolvedLimit) : window;
        String next = hasNext && !items.isEmpty() ? encode(items.get(items.size() - 1), filterHash) : null;
        return CursorPageResponse.<GroupSessionDiscoveryResponse>builder()
                .items(items.stream().map(this::toResponse).toList())
                .nextCursor(next).prevCursor(null).hasNext(hasNext).hasPrev(false).limit(resolvedLimit).build();
    }

    @Transactional(readOnly = true)
    public GroupSessionDiscoveryResponse detail(UUID groupSessionId) {
        GroupSession session = groupSessionRepository.findById(groupSessionId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy group session"));
        if (session.getStatus() != GroupSessionStatus.OPEN) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy group session");
        }
        return toResponse(session);
    }

    private GroupSessionDiscoveryResponse toResponse(GroupSession session) {
        int available = Math.max(0, session.getMaxParticipants() - session.getReservedSeatCount());
        boolean joinable = session.getStatus() == GroupSessionStatus.OPEN
                && session.getRegistrationStatus() == GroupSessionRegistrationStatus.OPEN
                && session.getRegistrationClosesAt().isAfter(utcNow()) && available > 0;
        var mentorUser = session.getMentorProfile().getUser();
        return new GroupSessionDiscoveryResponse(session.getId(), session.getMentorProfile().getUserId(),
                mentorUser.getFullName(), mentorUser.getAvatarUrl(), session.getService().getId(),
                session.getServiceTitleSnapshot(), session.getServiceDescriptionSnapshot(), session.getServiceExpectedOutcomeSnapshot(),
                session.getServiceDurationSnapshot(), session.getServiceIsFreeSnapshot(), session.getServicePriceScoinSnapshot(),
                instant(session.getScheduledStartAt()), instant(session.getScheduledEndAt()), instant(session.getRegistrationClosesAt()),
                session.getMaxParticipants(), session.getReservedSeatCount(), available, joinable, session.getSessionNote());
    }

    private DecodedCursor decode(String cursor, String filterHash) {
        if (cursor == null || cursor.isBlank()) return new DecodedCursor(null, null);
        CursorTokenPayload payload = cursorCodec.decode(cursor);
        if (!filterHash.equals(payload.filterHash())) throw new BaseException(ErrorCode.BAD_REQUEST, "Cursor không khớp bộ lọc group session");
        try {
            return new DecodedCursor(LocalDateTime.parse(payload.sortKey()), UUID.fromString(payload.secondaryKey()));
        } catch (RuntimeException ex) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cursor group session không hợp lệ");
        }
    }

    private String encode(GroupSession session, String filterHash) {
        return cursorCodec.encode(CursorTokenPayload.builder().sortKey(session.getScheduledStartAt().toString())
                .secondaryKey(session.getId().toString()).direction("NEXT").filterHash(filterHash).issuedAt(Instant.now()).build());
    }

    private String hash(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BaseException(ErrorCode.CONFIGURATION_ERROR, "Không thể tạo cursor group session", ex);
        }
    }

    private LocalDateTime utcNow() { return LocalDateTime.ofInstant(DateTimeUtil.getClock().instant(), ZoneOffset.UTC); }
    private Instant instant(LocalDateTime value) { return value.toInstant(ZoneOffset.UTC); }
    private record DecodedCursor(LocalDateTime startAt, UUID id) {}
}
