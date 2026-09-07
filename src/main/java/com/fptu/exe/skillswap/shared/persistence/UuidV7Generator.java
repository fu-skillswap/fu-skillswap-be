package com.fptu.exe.skillswap.shared.persistence;

import com.fptu.exe.skillswap.shared.util.UuidUtil;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import java.util.EnumSet;
import java.util.UUID;

public class UuidV7Generator implements BeforeExecutionGenerator {

    @Override
    public UUID generate(SharedSessionContractImplementor session, Object owner, Object currentValue, EventType eventType) {
        if (currentValue instanceof UUID existing && existing != null) {
            return existing;
        }
        return UuidUtil.generateUuidV7();
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EnumSet.of(EventType.INSERT);
    }
}
