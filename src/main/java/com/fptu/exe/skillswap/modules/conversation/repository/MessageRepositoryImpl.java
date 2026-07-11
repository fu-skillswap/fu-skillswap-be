package com.fptu.exe.skillswap.modules.conversation.repository;

import com.fptu.exe.skillswap.modules.conversation.domain.Message;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class MessageRepositoryImpl implements MessageRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Message> findMessageWindow(UUID conversationId,
                                           LocalDateTime cursorCreatedAt,
                                           UUID cursorMessageId,
                                           int fetchLimit) {
        StringBuilder jpql = new StringBuilder("""
                select m
                from Message m
                left join fetch m.sender sender
                where m.conversation.id = :conversationId
                """);
        if (cursorCreatedAt != null && cursorMessageId != null) {
            jpql.append("""
                      and (
                            m.createdAt < :cursorCreatedAt
                            or (m.createdAt = :cursorCreatedAt and m.id < :cursorMessageId)
                      )
                    """);
        }
        jpql.append(" order by m.createdAt desc, m.id desc");

        TypedQuery<Message> query = entityManager.createQuery(jpql.toString(), Message.class)
                .setParameter("conversationId", conversationId)
                .setMaxResults(fetchLimit);
        if (cursorCreatedAt != null && cursorMessageId != null) {
            query.setParameter("cursorCreatedAt", cursorCreatedAt);
            query.setParameter("cursorMessageId", cursorMessageId);
        }
        return query.getResultList();
    }
}
