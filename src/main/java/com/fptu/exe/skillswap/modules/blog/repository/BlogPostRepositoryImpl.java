package com.fptu.exe.skillswap.modules.blog.repository;

import com.fptu.exe.skillswap.modules.blog.domain.BlogPost;
import com.fptu.exe.skillswap.modules.blog.domain.BlogPostStatus;
import com.fptu.exe.skillswap.modules.blog.domain.BlogVisibility;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public class BlogPostRepositoryImpl implements BlogPostRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<BlogPost> findPublicWindow(
            Collection<BlogVisibility> allowedVisibilities,
            UUID categoryId,
            UUID tagId,
            String keywordPattern,
            LocalDateTime cursorPublishedAt,
            UUID cursorPostId,
            int fetchLimit
    ) {
        StringBuilder jpql = new StringBuilder("""
                select distinct p
                from BlogPost p
                where p.status = :status
                  and p.publishedAt is not null
                  and p.visibility in :allowedVisibilities
                """);
        List<String> filters = new ArrayList<>();
        if (categoryId != null) {
            filters.add("exists (select 1 from p.categories c where c.id = :categoryId)");
        }
        if (tagId != null) {
            filters.add("exists (select 1 from p.tags t where t.id = :tagId)");
        }
        if (keywordPattern != null) {
            filters.add("""
                    (lower(p.title) like :keywordPattern
                     or lower(coalesce(p.excerpt, '')) like :keywordPattern
                     )
                    """);
        }
        if (cursorPublishedAt != null && cursorPostId != null) {
            filters.add("(p.publishedAt < :cursorPublishedAt or (p.publishedAt = :cursorPublishedAt and p.id < :cursorPostId))");
        }
        appendFilters(jpql, filters);
        jpql.append(" order by p.publishedAt desc, p.id desc");
        TypedQuery<BlogPost> query = entityManager.createQuery(jpql.toString(), BlogPost.class);
        query.setParameter("status", BlogPostStatus.PUBLISHED);
        query.setParameter("allowedVisibilities", allowedVisibilities);
        bindCommon(query, categoryId, tagId, keywordPattern);
        if (cursorPublishedAt != null && cursorPostId != null) {
            query.setParameter("cursorPublishedAt", cursorPublishedAt);
            query.setParameter("cursorPostId", cursorPostId);
        }
        return query.setMaxResults(fetchLimit).getResultList();
    }

    @Override
    public List<BlogPost> findAdminWindow(
            BlogPostStatus status,
            UUID authorUserId,
            UUID categoryId,
            UUID tagId,
            String keywordPattern,
            LocalDateTime cursorUpdatedAt,
            UUID cursorPostId,
            int fetchLimit
    ) {
        StringBuilder jpql = new StringBuilder("""
                select distinct p
                from BlogPost p
                where 1 = 1
                """);
        List<String> filters = new ArrayList<>();
        if (status != null) {
            filters.add("p.status = :status");
        }
        if (authorUserId != null) {
            filters.add("p.authorUserId = :authorUserId");
        }
        if (categoryId != null) {
            filters.add("exists (select 1 from p.categories c where c.id = :categoryId)");
        }
        if (tagId != null) {
            filters.add("exists (select 1 from p.tags t where t.id = :tagId)");
        }
        if (keywordPattern != null) {
            filters.add("""
                    (lower(p.title) like :keywordPattern
                     or lower(coalesce(p.excerpt, '')) like :keywordPattern
                     or lower(coalesce(p.slug, '')) like :keywordPattern
                     )
                    """);
        }
        if (cursorUpdatedAt != null && cursorPostId != null) {
            filters.add("(p.updatedAt < :cursorUpdatedAt or (p.updatedAt = :cursorUpdatedAt and p.id < :cursorPostId))");
        }
        appendFilters(jpql, filters);
        jpql.append(" order by p.updatedAt desc, p.id desc");
        TypedQuery<BlogPost> query = entityManager.createQuery(jpql.toString(), BlogPost.class);
        if (status != null) {
            query.setParameter("status", status);
        }
        if (authorUserId != null) {
            query.setParameter("authorUserId", authorUserId);
        }
        bindCommon(query, categoryId, tagId, keywordPattern);
        if (cursorUpdatedAt != null && cursorPostId != null) {
            query.setParameter("cursorUpdatedAt", cursorUpdatedAt);
            query.setParameter("cursorPostId", cursorPostId);
        }
        return query.setMaxResults(fetchLimit).getResultList();
    }

    @Override
    public List<BlogPost> findPersonalizedFeedWindow(
            Collection<BlogVisibility> allowedVisibilities,
            Collection<UUID> followedCategoryIds,
            Collection<UUID> followedMentorIds,
            LocalDateTime cursorPublishedAt,
            UUID cursorPostId,
            int fetchLimit
    ) {
        boolean hasCategories = followedCategoryIds != null && !followedCategoryIds.isEmpty();
        boolean hasMentors = followedMentorIds != null && !followedMentorIds.isEmpty();
        StringBuilder jpql = new StringBuilder("""
                select p
                from BlogPost p
                where p.status = :status
                  and p.publishedAt is not null
                  and p.visibility in :allowedVisibilities
                """);
        List<String> filters = new ArrayList<>();
        if (cursorPublishedAt != null && cursorPostId != null) {
            filters.add("(p.publishedAt < :cursorPublishedAt or (p.publishedAt = :cursorPublishedAt and p.id < :cursorPostId))");
        }
        if (hasCategories || hasMentors) {
            filters.add("""
                     ((:hasCategories = true and exists (select 1 from p.categories fc where fc.id in :followedCategoryIds))
                     or (:hasMentors = true and p.authorUserId in :followedMentorIds))
                    """);
        }
        appendFilters(jpql, filters);
        jpql.append(" order by p.publishedAt desc, p.id desc");
        TypedQuery<BlogPost> query = entityManager.createQuery(jpql.toString(), BlogPost.class);
        query.setParameter("status", BlogPostStatus.PUBLISHED);
        query.setParameter("allowedVisibilities", allowedVisibilities);
        query.setParameter("hasCategories", hasCategories);
        query.setParameter("hasMentors", hasMentors);
        query.setParameter("followedCategoryIds", hasCategories ? followedCategoryIds : List.of(new UUID(0L, 0L)));
        query.setParameter("followedMentorIds", hasMentors ? followedMentorIds : List.of(new UUID(0L, 0L)));
        if (cursorPublishedAt != null && cursorPostId != null) {
            query.setParameter("cursorPublishedAt", cursorPublishedAt);
            query.setParameter("cursorPostId", cursorPostId);
        }
        return query.setMaxResults(fetchLimit).getResultList();
    }

    private void appendFilters(StringBuilder jpql, List<String> filters) {
        for (String filter : filters) {
            jpql.append(" and ").append(filter).append('\n');
        }
    }

    private void bindCommon(TypedQuery<BlogPost> query,
                            UUID categoryId,
                            UUID tagId,
                            String keywordPattern) {
        if (categoryId != null) {
            query.setParameter("categoryId", categoryId);
        }
        if (tagId != null) {
            query.setParameter("tagId", tagId);
        }
        if (keywordPattern != null) {
            query.setParameter("keywordPattern", keywordPattern);
        }
    }
}
