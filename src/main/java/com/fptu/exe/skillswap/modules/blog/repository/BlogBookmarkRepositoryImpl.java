package com.fptu.exe.skillswap.modules.blog.repository;

import com.fptu.exe.skillswap.modules.blog.domain.BlogBookmark;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public class BlogBookmarkRepositoryImpl implements BlogBookmarkRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public java.util.List<BlogBookmark> findBookmarkWindow(UUID userId, LocalDateTime cursorCreatedAt, UUID cursorPostId, int fetchLimit) {
        StringBuilder jpql = new StringBuilder("""
                select b
                from BlogBookmark b
                join fetch b.post p
                join fetch p.authorUser
                where b.user.id = :userId
                """);
        if (cursorCreatedAt != null && cursorPostId != null) {
            jpql.append(" and (b.createdAt < :cursorCreatedAt or (b.createdAt = :cursorCreatedAt and p.id < :cursorPostId))");
        }
        jpql.append(" order by b.createdAt desc, p.id desc");

        TypedQuery<BlogBookmark> query = entityManager.createQuery(jpql.toString(), BlogBookmark.class)
                .setParameter("userId", userId)
                .setMaxResults(fetchLimit);
        if (cursorCreatedAt != null && cursorPostId != null) {
            query.setParameter("cursorCreatedAt", cursorCreatedAt);
            query.setParameter("cursorPostId", cursorPostId);
        }
        return query.getResultList();
    }
}
