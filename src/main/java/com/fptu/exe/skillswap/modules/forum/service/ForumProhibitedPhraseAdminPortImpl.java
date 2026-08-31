package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.forum.domain.ForumProhibitedPhrase;
import com.fptu.exe.skillswap.modules.forum.event.ForumProhibitedPhraseChangedEvent;
import com.fptu.exe.skillswap.modules.forum.port.ForumProhibitedPhraseAdminPort;
import com.fptu.exe.skillswap.modules.forum.port.ForumProhibitedPhraseView;
import com.fptu.exe.skillswap.modules.forum.port.CreateForumProhibitedPhraseCommand;
import com.fptu.exe.skillswap.modules.forum.port.UpdateForumProhibitedPhraseCommand;
import com.fptu.exe.skillswap.modules.forum.port.SetForumProhibitedPhraseActiveCommand;
import com.fptu.exe.skillswap.modules.forum.repository.ForumProhibitedPhraseRepository;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.port.UserQueryPort;
import com.fptu.exe.skillswap.shared.cursor.CursorCodec;
import com.fptu.exe.skillswap.shared.cursor.CursorTokenPayload;
import com.fptu.exe.skillswap.shared.dto.response.CursorPageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ForumProhibitedPhraseAdminPortImpl implements ForumProhibitedPhraseAdminPort {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final ForumProhibitedPhraseRepository forumProhibitedPhraseRepository;
    private final ForumProhibitedPhrasePolicy prohibitedPhrasePolicy;
    private final CursorCodec cursorCodec;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;
    private final UserQueryPort userQueryPort;

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<ForumProhibitedPhraseView> list(Boolean isActive, String cursor, Integer limit) {
        int resolvedLimit = resolveLimit(limit);
        String filterHash = "forum-prohibited-phrases|isActive=" + (isActive == null ? "all" : isActive);
        DecodedCursor decodedCursor = decodeCursor(cursor, filterHash);
        List<ForumProhibitedPhrase> window = forumProhibitedPhraseRepository.findWindow(
                isActive,
                decodedCursor.createdAt(),
                decodedCursor.ruleId(),
                PageRequest.of(0, resolvedLimit + 1)
        );
        boolean hasNext = window.size() > resolvedLimit;
        List<ForumProhibitedPhrase> items = hasNext ? window.subList(0, resolvedLimit) : window;
        String nextCursor = hasNext && !items.isEmpty()
                ? encodeCursor(items.get(items.size() - 1), filterHash)
                : null;
        return CursorPageResponse.<ForumProhibitedPhraseView>builder()
                .items(items.stream().map(this::toResponse).toList())
                .nextCursor(nextCursor)
                .prevCursor(null)
                .hasNext(hasNext)
                .hasPrev(false)
                .limit(resolvedLimit)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ForumProhibitedPhraseView get(UUID ruleId) {
        return toResponse(requireRule(ruleId));
    }

    @Override
    @Transactional
    public ForumProhibitedPhraseView create(UUID adminUserId, CreateForumProhibitedPhraseCommand command) {
        String phrase = requirePhrase(command.phrase());
        String normalizedPhrase = prohibitedPhrasePolicy.normalizePhrase(phrase);
        if (forumProhibitedPhraseRepository.existsByNormalizedPhrase(normalizedPhrase)) {
            throw new BaseException(ErrorCode.FORUM_PROHIBITED_PHRASE_DUPLICATE);
        }
        User admin = userQueryPort.findUserById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người quản trị"));
        ForumProhibitedPhrase saved = saveAndFlush(ForumProhibitedPhrase.builder()
                .phrase(phrase)
                .normalizedPhrase(normalizedPhrase)
                .active(true)
                .createdByUser(admin)
                .build());
        eventPublisher.publishEvent(new ForumProhibitedPhraseChangedEvent(saved.getId(), "CREATE"));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ForumProhibitedPhraseView update(UUID adminUserId, UUID ruleId, UpdateForumProhibitedPhraseCommand command) {
        ForumProhibitedPhrase rule = requireRule(ruleId);
        requireExpectedVersion(rule, command.expectedVersion());
        String phrase = requirePhrase(command.phrase());
        String normalizedPhrase = prohibitedPhrasePolicy.normalizePhrase(phrase);
        if (!normalizedPhrase.equals(rule.getNormalizedPhrase())
                && forumProhibitedPhraseRepository.existsByNormalizedPhrase(normalizedPhrase)) {
            throw new BaseException(ErrorCode.FORUM_PROHIBITED_PHRASE_DUPLICATE);
        }
        rule.setPhrase(phrase);
        rule.setNormalizedPhrase(normalizedPhrase);
        ForumProhibitedPhrase saved = saveAndFlush(rule);
        eventPublisher.publishEvent(new ForumProhibitedPhraseChangedEvent(saved.getId(), "UPDATE"));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ForumProhibitedPhraseView setActive(UUID adminUserId, UUID ruleId, SetForumProhibitedPhraseActiveCommand command) {
        ForumProhibitedPhrase rule = requireRule(ruleId);
        requireExpectedVersion(rule, command.expectedVersion());
        rule.setActive(command.isActive());
        ForumProhibitedPhrase saved = saveAndFlush(rule);
        eventPublisher.publishEvent(new ForumProhibitedPhraseChangedEvent(saved.getId(), command.isActive() ? "ACTIVATE" : "DEACTIVATE"));
        return toResponse(saved);
    }

    private ForumProhibitedPhrase requireRule(UUID ruleId) {
        return forumProhibitedPhraseRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cụm từ cấm"));
    }

    private ForumProhibitedPhrase saveAndFlush(ForumProhibitedPhrase rule) {
        try {
            ForumProhibitedPhrase saved = forumProhibitedPhraseRepository.save(rule);
            entityManager.flush();
            return saved;
        } catch (OptimisticLockException | ObjectOptimisticLockingFailureException exception) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Rule cụm từ cấm đã được cập nhật bởi thao tác khác", exception);
        } catch (DataIntegrityViolationException exception) {
            throw new BaseException(ErrorCode.FORUM_PROHIBITED_PHRASE_DUPLICATE, ErrorCode.FORUM_PROHIBITED_PHRASE_DUPLICATE.getMessage(), exception);
        }
    }

    private String requirePhrase(String rawPhrase) {
        String phrase = rawPhrase == null ? "" : rawPhrase.trim();
        if (phrase.isEmpty()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cụm từ cấm không được để trống");
        }
        String normalized = prohibitedPhrasePolicy.normalizePhrase(phrase);
        if (normalized.isBlank()) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cụm từ cấm không hợp lệ");
        }
        return phrase;
    }

    private void requireExpectedVersion(ForumProhibitedPhrase rule, Integer expectedVersion) {
        if (!Objects.equals(rule.getVersion(), expectedVersion)) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Rule cụm từ cấm đã được cập nhật bởi thao tác khác");
        }
    }

    private ForumProhibitedPhraseView toResponse(ForumProhibitedPhrase rule) {
        return new ForumProhibitedPhraseView(
                rule.getId(),
                rule.getPhrase(),
                rule.isActive(),
                rule.getVersion(),
                rule.getCreatedByUser() == null ? null : rule.getCreatedByUser().getId(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }

    private int resolveLimit(Integer limit) {
        int resolved = limit == null || limit <= 0 ? DEFAULT_LIMIT : limit;
        return Math.min(resolved, MAX_LIMIT);
    }

    private DecodedCursor decodeCursor(String cursor, String expectedFilterHash) {
        if (cursor == null || cursor.isBlank()) {
            return DecodedCursor.empty();
        }
        CursorTokenPayload payload = cursorCodec.decode(cursor);
        if (!Objects.equals(expectedFilterHash, payload.filterHash())
                || payload.sortKey() == null
                || payload.secondaryKey() == null) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cursor không hợp lệ cho bộ lọc hiện tại");
        }
        try {
            return new DecodedCursor(LocalDateTime.parse(payload.sortKey()), UUID.fromString(payload.secondaryKey()));
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Cursor không hợp lệ", exception);
        }
    }

    private String encodeCursor(ForumProhibitedPhrase rule, String filterHash) {
        return cursorCodec.encode(CursorTokenPayload.builder()
                .sortKey(rule.getCreatedAt().toString())
                .secondaryKey(rule.getId().toString())
                .direction("NEXT")
                .filterHash(filterHash)
                .issuedAt(Instant.now())
                .build());
    }

    private record DecodedCursor(LocalDateTime createdAt, UUID ruleId) {
        private static DecodedCursor empty() {
            return new DecodedCursor(null, null);
        }
    }
}
