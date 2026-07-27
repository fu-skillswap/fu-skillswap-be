package com.fptu.exe.skillswap.modules.forum.repository;

import com.fptu.exe.skillswap.modules.forum.domain.ForumPost;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Expression;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;
import java.util.UUID;
import com.fptu.exe.skillswap.modules.academic.domain.AcademicProgram;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPostStatus;

@Repository
public class ForumPostRepositoryImpl implements ForumPostRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<ForumPost> findWindow(Specification<ForumPost> specification, int fetchLimit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<ForumPost> cq = cb.createQuery(ForumPost.class);
        Root<ForumPost> root = cq.from(ForumPost.class);
        root.fetch("authorUser", JoinType.INNER);
        root.fetch("helpTopic", JoinType.INNER);
        root.fetch("authorProgram", JoinType.LEFT);
        cq.select(root).distinct(true);

        Predicate predicate = specification == null ? cb.conjunction() : specification.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
        cq.orderBy(
                cb.desc(root.get("lastActivityAt")),
                cb.desc(root.get("id"))
        );

        return entityManager.createQuery(cq)
                .setMaxResults(fetchLimit)
                .getResultList();
    }

    @Override
    public List<ForumPost> findProgramPrioritizedWindow(UUID programId,
                                                         Integer afterPriority,
                                                         LocalDateTime afterLastActivityAt,
                                                         UUID afterPostId,
                                                         int fetchLimit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<ForumPost> cq = cb.createQuery(ForumPost.class);
        Root<ForumPost> root = cq.from(ForumPost.class);
        root.fetch("authorUser", JoinType.INNER);
        root.fetch("helpTopic", JoinType.INNER);
        root.fetch("authorProgram", JoinType.LEFT);
        Join<ForumPost, AcademicProgram> authorProgram = root.join("authorProgram", JoinType.LEFT);

        Expression<Integer> priority = programId == null
                ? cb.literal(1)
                : cb.<Integer>selectCase()
                        .when(cb.equal(authorProgram.get("id"), programId), 0)
                        .otherwise(1);
        Predicate predicate = cb.equal(root.get("status"), ForumPostStatus.PUBLISHED);
        if (afterPriority != null && afterLastActivityAt != null && afterPostId != null) {
            Predicate lowerPriority = cb.greaterThan(priority, afterPriority);
            Predicate samePriority = cb.equal(priority, afterPriority);
            Predicate olderActivity = cb.lessThan(root.get("lastActivityAt"), afterLastActivityAt);
            Predicate sameActivity = cb.equal(root.get("lastActivityAt"), afterLastActivityAt);
            Predicate olderId = lessThanUuid(cb, root, afterPostId);
            predicate = cb.and(predicate, cb.or(
                    lowerPriority,
                    cb.and(samePriority, cb.or(olderActivity, cb.and(sameActivity, olderId)))
            ));
        }
        // All joins are to-one, so DISTINCT is unnecessary and would make the
        // priority CASE expression invalid in PostgreSQL ORDER BY.
        cq.select(root).where(predicate).orderBy(
                cb.asc(priority),
                cb.desc(root.get("lastActivityAt")),
                cb.desc(root.get("id"))
        );
        return entityManager.createQuery(cq)
                .setMaxResults(fetchLimit)
                .getResultList();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Predicate lessThanUuid(CriteriaBuilder cb, Root<ForumPost> root, UUID id) {
        return cb.lessThan((Expression) root.get("id"), id);
    }
}
