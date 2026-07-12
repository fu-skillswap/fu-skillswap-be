package com.fptu.exe.skillswap.modules.blog.repository;

import com.fptu.exe.skillswap.modules.blog.domain.BlogAudienceType;
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
            BlogAudienceType audienceType,
            String keywordPattern,
            LocalDateTime cursorPublishedAt,
            UUID cursorPostId,
            int fetchLimit
    ) {
        StringBuilder jpql = new StringBuilder("""
                select distinct p
                from BlogPost p
                join fetch p.authorUser author
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
        if (audienceType != null) {
            filters.add("(p.audienceType = :audienceType or p.audienceType = :bothAudience)");
        }
        if (keywordPattern != null) {
            filters.add("""
                    (lower(p.title) like :keywordPattern
                     or lower(coalesce(p.excerpt, '')) like :keywordPattern
                     or lower(coalesce(author.fullName, '')) like :keywordPattern)
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
        bindCommon(query, categoryId, tagId, audienceType, keywordPattern);
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
                join fetch p.authorUser author
                where 1 = 1
                """);
        List<String> filters = new ArrayList<>();
        if (status != null) {
            filters.add("p.status = :status");
        }
        if (authorUserId != null) {
            filters.add("author.id = :authorUserId");
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
                     or lower(coalesce(author.fullName, '')) like :keywordPattern)
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
        bindCommon(query, categoryId, tagId, null, keywordPattern);
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
            Collection<UUID> followedTagIds,
            LocalDateTime cursorPublishedAt,
            UUID cursorPostId,
            int fetchLimit
    ) {
        boolean hasCategories = followedCategoryIds != null && !followedCategoryIds.isEmpty();
        boolean hasTags = followedTagIds != null && !followedTagIds.isEmpty();
        StringBuilder jpql = new StringBuilder("""
                select p
                from BlogPost p
                join fetch p.authorUser author
                where p.status = :status
                  and p.publishedAt is not null
                  and p.visibility in :allowedVisibilities
                """);
        List<String> filters = new ArrayList<>();
        if (cursorPublishedAt != null && cursorPostId != null) {
            filters.add("(p.publishedAt < :cursorPublishedAt or (p.publishedAt = :cursorPublishedAt and p.id < :cursorPostId))");
        }
        if (hasCategories || hasTags) {
            filters.add("""
                    ((:hasCategories = true and exists (select 1 from p.categories fc where fc.id in :followedCategoryIds))
                     or (:hasTags = true and exists (select 1 from p.tags ft where ft.id in :followedTagIds)))
                    """);
        }
        appendFilters(jpql, filters);
        jpql.append(" order by p.publishedAt desc, p.id desc");
        TypedQuery<BlogPost> query = entityManager.createQuery(jpql.toString(), BlogPost.class);
        query.setParameter("status", BlogPostStatus.PUBLISHED);
        query.setParameter("allowedVisibilities", allowedVisibilities);
        query.setParameter("hasCategories", hasCategories);
        query.setParameter("hasTags", hasTags);
        query.setParameter("followedCategoryIds", hasCategories ? followedCategoryIds : List.of(new UUID(0L, 0L)));
        query.setParameter("followedTagIds", hasTags ? followedTagIds : List.of(new UUID(0L, 0L)));
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
                            BlogAudienceType audienceType,
                            String keywordPattern) {
        if (categoryId != null) {
            query.setParameter("categoryId", categoryId);
        }
        if (tagId != null) {
            query.setParameter("tagId", tagId);
        }
        if (audienceType != null) {
            query.setParameter("audienceType", audienceType);
            query.setParameter("bothAudience", BlogAudienceType.BOTH);
        }
        if (keywordPattern != null) {
            query.setParameter("keywordPattern", keywordPattern);
        }
    }
}
