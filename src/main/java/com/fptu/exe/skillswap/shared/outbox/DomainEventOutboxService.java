package com.fptu.exe.skillswap.shared.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.TraceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DomainEventOutboxService {

    private final DomainEventOutboxRepository domainEventOutboxRepository;
    private final ObjectMapper objectMapper;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Transactional
    public void enqueue(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        domainEventOutboxRepository.save(DomainEventOutbox.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .traceId(TraceContext.getCurrentTraceId())
                .payloadJson(serialize(payload))
                .status(DomainEventOutboxStatus.PENDING)
                .build());
        eventPublisher.publishEvent(new OutboxWakeupEvent());
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BaseException(ErrorCode.BAD_REQUEST, "Không thể serialize domain event outbox payload", ex);
        }
    }
}
