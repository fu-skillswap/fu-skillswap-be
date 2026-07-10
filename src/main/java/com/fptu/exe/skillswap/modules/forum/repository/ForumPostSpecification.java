package com.fptu.exe.skillswap.modules.forum.repository;

import com.fptu.exe.skillswap.modules.forum.domain.ForumPost;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPostStatus;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.time.LocalDateTime;
import java.util.UUID;

public final class ForumPostSpecification {

    private ForumPostSpecification() {
    }

    public static Specification<ForumPost> hasStatus(ForumPostStatus status) {
        return (root, query, cb) -> status == null
                ? cb.conjunction()
                : cb.equal(root.get("status"), status);
    }

    public static Specification<ForumPost> hasHelpTopic(UUID topicId) {
        return (root, query, cb) -> topicId == null
                ? cb.conjunction()
                : cb.equal(root.get("helpTopic").get("id"), topicId);
    }

    public static Specification<ForumPost> hasAuthor(UUID authorId) {
        return (root, query, cb) -> authorId == null
                ? cb.conjunction()
                : cb.equal(root.get("authorUser").get("id"), authorId);
    }

    public static Specification<ForumPost> hasKeyword(String keywordPattern) {
        return (root, query, cb) -> {
            if (keywordPattern == null || keywordPattern.isBlank()) {
                return cb.conjunction();
            }
            Expression<String> title = cb.lower(root.get("title"));
            Expression<String> content = cb.lower(root.get("content"));
            Expression<String> authorFullName = cb.lower(root.get("authorUser").get("fullName"));
            return cb.or(
                    cb.like(title, keywordPattern),
                    cb.like(content, keywordPattern),
                    cb.like(authorFullName, keywordPattern)
            );
        };
    }

    public static Specification<ForumPost> mineOnly(UUID currentUserId) {
        return hasAuthor(currentUserId);
    }

    public static Specification<ForumPost> isBeforeCursor(LocalDateTime lastActivityAt, UUID id) {
        return (root, query, cb) -> {
            if (lastActivityAt == null || id == null) {
                return cb.conjunction();
            }
            Predicate olderActivity = cb.lessThan(root.get("lastActivityAt"), lastActivityAt);
            Predicate sameActivity = cb.equal(root.get("lastActivityAt"), lastActivityAt);
            Predicate olderId = lessThanUuid(cb, root, id);
            return cb.or(olderActivity, cb.and(sameActivity, olderId));
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Predicate lessThanUuid(CriteriaBuilder cb, Root<ForumPost> root, UUID id) {
        return cb.lessThan((Expression) root.get("id"), id);
    }
}
