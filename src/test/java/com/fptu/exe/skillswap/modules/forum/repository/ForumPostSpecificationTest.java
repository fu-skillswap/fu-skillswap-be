package com.fptu.exe.skillswap.modules.forum.repository;

import com.fptu.exe.skillswap.modules.forum.domain.ForumPost;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPostStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForumPostSpecificationTest {

    @Mock
    private Root<ForumPost> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock
    private CriteriaBuilder cb;
    @Mock
    private Path<ForumPostStatus> statusPath;
    @Mock
    private Path<Object> helpTopicPath;
    @Mock
    private Path<UUID> helpTopicIdPath;
    @Mock
    private Path<Object> authorPath;
    @Mock
    private Path<UUID> authorIdPath;
    @Mock
    private Path<String> titlePath;
    @Mock
    private Path<String> contentPath;
    @Mock
    private Path<String> fullNamePath;
    @Mock
    private Path<LocalDateTime> lastActivityPath;
    @Mock
    private Path<UUID> idPath;
    @Mock
    private Predicate predicate;
    @Mock
    private Predicate predicate2;
    @Mock
    private Predicate predicate3;
    @Mock
    private Predicate predicate4;
    @Mock
    private Predicate predicate5;
    @Mock
    private Predicate predicate6;

    @SuppressWarnings("unchecked")
    @Test
    void hasStatus_shouldReturnEqualityPredicate() {
        ForumPostStatus status = ForumPostStatus.PUBLISHED;
        when(root.get("status")).thenReturn((Path) statusPath);
        when(cb.equal(statusPath, status)).thenReturn(predicate);

        Predicate result = ForumPostSpecification.hasStatus(status).toPredicate(root, query, cb);

        assertSame(predicate, result);
        verify(cb).equal(statusPath, status);
    }

    @SuppressWarnings("unchecked")
    @Test
    void hasHelpTopic_shouldReturnHelpTopicPredicate() {
        UUID topicId = UUID.fromString("00000000-0000-7000-8000-000000000001");
        when(root.get("helpTopic")).thenReturn((Path) helpTopicPath);
        when(helpTopicPath.get("id")).thenReturn((Path) helpTopicIdPath);
        when(cb.equal(helpTopicIdPath, topicId)).thenReturn(predicate);

        Predicate result = ForumPostSpecification.hasHelpTopic(topicId).toPredicate(root, query, cb);

        assertSame(predicate, result);
        verify(cb).equal(helpTopicIdPath, topicId);
    }

    @SuppressWarnings("unchecked")
    @Test
    void hasAuthor_shouldReturnAuthorPredicate() {
        UUID authorId = UUID.fromString("00000000-0000-7000-8000-000000000002");
        when(root.get("authorUser")).thenReturn((Path) authorPath);
        when(authorPath.get("id")).thenReturn((Path) authorIdPath);
        when(cb.equal(authorIdPath, authorId)).thenReturn(predicate);

        Predicate result = ForumPostSpecification.hasAuthor(authorId).toPredicate(root, query, cb);

        assertSame(predicate, result);
        verify(cb).equal(authorIdPath, authorId);
    }

    @SuppressWarnings("unchecked")
    @Test
    void hasKeyword_shouldCombineTitleContentAndAuthorName() {
        String keywordPattern = "%project%";
        when(root.get("title")).thenReturn((Path) titlePath);
        when(root.get("content")).thenReturn((Path) contentPath);
        when(root.get("authorUser")).thenReturn((Path) authorPath);
        when(authorPath.get("fullName")).thenReturn((Path) fullNamePath);
        when(cb.lower(titlePath)).thenReturn(titlePath);
        when(cb.lower(contentPath)).thenReturn(contentPath);
        when(cb.lower(fullNamePath)).thenReturn(fullNamePath);
        when(cb.like(titlePath, keywordPattern)).thenReturn(predicate);
        when(cb.like(contentPath, keywordPattern)).thenReturn(predicate2);
        when(cb.like(fullNamePath, keywordPattern)).thenReturn(predicate3);
        when(cb.or(predicate, predicate2, predicate3)).thenReturn(predicate4);

        Predicate result = ForumPostSpecification.hasKeyword(keywordPattern).toPredicate(root, query, cb);

        assertSame(predicate4, result);
        verify(cb).like(titlePath, keywordPattern);
        verify(cb).like(contentPath, keywordPattern);
        verify(cb).like(fullNamePath, keywordPattern);
    }

    @SuppressWarnings("unchecked")
    @Test
    void mineOnly_shouldReuseAuthorPredicate() {
        UUID currentUserId = UUID.fromString("00000000-0000-7000-8000-000000000003");
        when(root.get("authorUser")).thenReturn((Path) authorPath);
        when(authorPath.get("id")).thenReturn((Path) authorIdPath);
        when(cb.equal(authorIdPath, currentUserId)).thenReturn(predicate);

        Predicate result = ForumPostSpecification.mineOnly(currentUserId).toPredicate(root, query, cb);

        assertSame(predicate, result);
        verify(cb).equal(authorIdPath, currentUserId);
    }

    @SuppressWarnings("unchecked")
    @Test
    void isBeforeCursor_shouldBuildTupleComparisonPredicate() {
        LocalDateTime cursorTime = LocalDateTime.of(2026, 7, 8, 10, 0);
        UUID cursorId = UUID.fromString("00000000-0000-7000-8000-000000000003");
        when(root.get("lastActivityAt")).thenReturn((Path) lastActivityPath);
        when(root.get("id")).thenReturn((Path) idPath);
        when(cb.lessThan(lastActivityPath, cursorTime)).thenReturn(predicate);
        when(cb.equal(lastActivityPath, cursorTime)).thenReturn(predicate2);
        when(cb.lessThan((Expression) idPath, cursorId)).thenReturn(predicate3);
        when(cb.and(predicate2, predicate3)).thenReturn(predicate4);
        when(cb.or(predicate, predicate4)).thenReturn(predicate5);

        Predicate result = ForumPostSpecification.isBeforeCursor(cursorTime, cursorId).toPredicate(root, query, cb);

        assertSame(predicate5, result);
        verify(cb).lessThan(lastActivityPath, cursorTime);
        verify(cb).equal(lastActivityPath, cursorTime);
        verify(cb).lessThan((Expression) idPath, cursorId);
        verify(cb).and(predicate2, predicate3);
        verify(cb).or(predicate, predicate4);
    }
}
