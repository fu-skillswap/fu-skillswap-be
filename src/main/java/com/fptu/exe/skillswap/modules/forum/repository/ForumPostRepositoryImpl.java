package com.fptu.exe.skillswap.modules.forum.repository;

import com.fptu.exe.skillswap.modules.forum.domain.ForumPost;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.List;

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
}
