package com.fptu.exe.skillswap.shared.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
        classes = DomainEventOutboxRepositoryTest.OutboxJpaTestApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:outboxdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false"
        }
)
class DomainEventOutboxRepositoryTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = DomainEventOutbox.class)
    @EnableJpaRepositories(basePackageClasses = DomainEventOutboxRepository.class)
    static class OutboxJpaTestApplication {
    }

    @Autowired
    private DomainEventOutboxRepository repository;

    @Test
    void shouldPersistOutboxEntityWithJsonPayload() {
        DomainEventOutbox entity = DomainEventOutbox.builder()
                .aggregateType("FORUM_POST")
                .aggregateId(UUID.randomUUID())
                .eventType("forum.post.created")
                .payloadJson("{\"postId\":\"019f1234\",\"authorId\":\"019f5678\"}")
                .availableAt(LocalDateTime.now().minusMinutes(1))
                .build();

        DomainEventOutbox saved = repository.saveAndFlush(entity);

        assertNotNull(saved.getId());
        assertEquals(DomainEventOutboxStatus.PENDING, saved.getStatus());
        assertEquals(entity.getPayloadJson(), saved.getPayloadJson());
    }

    @Test
    void shouldLoadPendingBatchOrderedByCreatedAtAsc() {
        UUID aggregateId = UUID.randomUUID();
        DomainEventOutbox first = repository.saveAndFlush(DomainEventOutbox.builder()
                .aggregateType("CHAT")
                .aggregateId(aggregateId)
                .eventType("chat.message.created")
                .payloadJson("{\"messageId\":\"m1\"}")
                .availableAt(LocalDateTime.now().minusMinutes(2))
                .build());

        DomainEventOutbox second = repository.saveAndFlush(DomainEventOutbox.builder()
                .aggregateType("CHAT")
                .aggregateId(aggregateId)
                .eventType("chat.message.created")
                .payloadJson("{\"messageId\":\"m2\"}")
                .availableAt(LocalDateTime.now().minusMinutes(1))
                .build());

        List<DomainEventOutbox> batch = repository.findTop100ByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
                DomainEventOutboxStatus.PENDING,
                LocalDateTime.now()
        );

        assertEquals(2, batch.size());
        assertEquals(first.getId(), batch.get(0).getId());
        assertEquals(second.getId(), batch.get(1).getId());
    }
}
