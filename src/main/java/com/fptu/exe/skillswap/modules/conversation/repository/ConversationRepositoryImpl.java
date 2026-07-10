package com.fptu.exe.skillswap.modules.conversation.repository;

import com.fptu.exe.skillswap.modules.conversation.domain.Conversation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class ConversationRepositoryImpl implements ConversationRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Conversation> findConversationWindowByParticipant(UUID userId,
                                                                  LocalDateTime cursorActivityAt,
                                                                  UUID cursorConversationId,
                                                                  int fetchLimit) {
        StringBuilder jpql = new StringBuilder("""
                select c
                from Conversation c
                join ConversationParticipant cp on c.id = cp.conversation.id
                where cp.user.id = :userId
                """);
        if (cursorActivityAt != null && cursorConversationId != null) {
            jpql.append("""
                      and (
                            coalesce(c.lastMessageAt, c.createdAt) < :cursorActivityAt
                            or (
                                coalesce(c.lastMessageAt, c.createdAt) = :cursorActivityAt
                                and c.id < :cursorConversationId
                            )
                      )
                    """);
        }
        jpql.append(" order by coalesce(c.lastMessageAt, c.createdAt) desc, c.id desc");

        TypedQuery<Conversation> query = entityManager.createQuery(jpql.toString(), Conversation.class)
                .setParameter("userId", userId)
                .setMaxResults(fetchLimit);
        if (cursorActivityAt != null && cursorConversationId != null) {
            query.setParameter("cursorActivityAt", cursorActivityAt);
            query.setParameter("cursorConversationId", cursorConversationId);
        }
        return query.getResultList();
    }
}
