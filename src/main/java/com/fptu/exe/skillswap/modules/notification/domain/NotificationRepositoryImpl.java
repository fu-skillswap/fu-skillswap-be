package com.fptu.exe.skillswap.modules.notification.domain;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class NotificationRepositoryImpl implements NotificationRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Notification> findNotificationWindow(UUID recipientUserId,
                                                     boolean unreadOnly,
                                                     LocalDateTime cursorCreatedAt,
                                                     UUID cursorNotificationId,
                                                     int fetchLimit) {
        StringBuilder jpql = new StringBuilder("""
                select n
                from Notification n
                where n.recipientUser.id = :recipientUserId
                """);
        if (unreadOnly) {
            jpql.append(" and n.readAt is null");
        }
        if (cursorCreatedAt != null && cursorNotificationId != null) {
            jpql.append("""
                      and (
                            n.createdAt < :cursorCreatedAt
                            or (n.createdAt = :cursorCreatedAt and n.id < :cursorNotificationId)
                      )
                    """);
        }
        jpql.append(" order by n.createdAt desc, n.id desc");

        TypedQuery<Notification> query = entityManager.createQuery(jpql.toString(), Notification.class)
                .setParameter("recipientUserId", recipientUserId)
                .setMaxResults(fetchLimit);
        if (cursorCreatedAt != null && cursorNotificationId != null) {
            query.setParameter("cursorCreatedAt", cursorCreatedAt);
            query.setParameter("cursorNotificationId", cursorNotificationId);
        }
        return query.getResultList();
    }
}
