package com.fptu.exe.skillswap.modules.forum.repository;

import com.fptu.exe.skillswap.modules.forum.domain.ForumComment;
import com.fptu.exe.skillswap.modules.forum.domain.ForumCommentStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class ForumCommentRepositoryImpl implements ForumCommentRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<ForumComment> findVisibleCommentsWindow(UUID postId,
                                                        ForumCommentStatus status,
                                                        LocalDateTime cursorCreatedAt,
                                                        UUID cursorCommentId,
                                                        int fetchLimit) {
        StringBuilder jpql = new StringBuilder("""
                select c
                from ForumComment c
                join fetch c.authorUser author
                join fetch c.post post
                join fetch post.helpTopic helpTopic
                where post.id = :postId
                  and c.status = :status
                """);
        if (cursorCreatedAt != null && cursorCommentId != null) {
            jpql.append("""
                      and (
                            c.createdAt > :cursorCreatedAt
                            or (c.createdAt = :cursorCreatedAt and c.id > :cursorCommentId)
                      )
                    """);
        }
        jpql.append(" order by c.createdAt asc, c.id asc");

        TypedQuery<ForumComment> query = entityManager.createQuery(jpql.toString(), ForumComment.class)
                .setParameter("postId", postId)
                .setParameter("status", status)
                .setMaxResults(fetchLimit);
        if (cursorCreatedAt != null && cursorCommentId != null) {
            query.setParameter("cursorCreatedAt", cursorCreatedAt);
            query.setParameter("cursorCommentId", cursorCommentId);
        }
        return query.getResultList();
    }

    @Override
    public List<ForumComment> findAdminCommentsWindow(ForumCommentStatus status,
                                                      UUID postId,
                                                      UUID authorId,
                                                      String keywordPattern,
                                                      LocalDateTime cursorCreatedAt,
                                                      UUID cursorCommentId,
                                                      int fetchLimit) {
        StringBuilder jpql = new StringBuilder("""
                select c
                from ForumComment c
                join fetch c.authorUser author
                join fetch c.post post
                join fetch post.helpTopic helpTopic
                where 1 = 1
                """);
        List<ParameterBinder> binders = new ArrayList<>();
        if (status != null) {
            jpql.append(" and c.status = :status");
            binders.add(query -> query.setParameter("status", status));
        }
        if (postId != null) {
            jpql.append(" and post.id = :postId");
            binders.add(query -> query.setParameter("postId", postId));
        }
        if (authorId != null) {
            jpql.append(" and author.id = :authorId");
            binders.add(query -> query.setParameter("authorId", authorId));
        }
        if (keywordPattern != null) {
            jpql.append("""
                     and (
                           lower(c.content) like :keywordPattern
                           or lower(author.fullName) like :keywordPattern
                     )
                    """);
            binders.add(query -> query.setParameter("keywordPattern", keywordPattern));
        }
        if (cursorCreatedAt != null && cursorCommentId != null) {
            jpql.append("""
                      and (
                            c.createdAt < :cursorCreatedAt
                            or (c.createdAt = :cursorCreatedAt and c.id < :cursorCommentId)
                      )
                    """);
            binders.add(query -> query.setParameter("cursorCreatedAt", cursorCreatedAt));
            binders.add(query -> query.setParameter("cursorCommentId", cursorCommentId));
        }
        jpql.append(" order by c.createdAt desc, c.id desc");

        TypedQuery<ForumComment> query = entityManager.createQuery(jpql.toString(), ForumComment.class)
                .setMaxResults(fetchLimit);
        binders.forEach(binder -> binder.bind(query));
        return query.getResultList();
    }

    @FunctionalInterface
    private interface ParameterBinder {
        void bind(TypedQuery<ForumComment> query);
    }
}
