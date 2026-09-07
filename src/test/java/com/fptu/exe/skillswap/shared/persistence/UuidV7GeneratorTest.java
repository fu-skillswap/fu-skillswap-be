package com.fptu.exe.skillswap.shared.persistence;

import org.hibernate.generator.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7GeneratorTest {

    private final UuidV7Generator generator = new UuidV7Generator();

    @Test
    @DisplayName("Khi currentValue là null thì sinh UUIDv7 mới")
    void generate_whenCurrentValueIsNull_shouldGenerateNewUuidV7() {
        UUID generated = generator.generate(null, null, null, EventType.INSERT);

        assertThat(generated).isNotNull();
        // UUID v7 có chữ số version là 7 ở nibble đầu của block 3
        assertThat(generated.version()).isEqualTo(7);
    }

    @Test
    @DisplayName("Khi currentValue đã có sẵn UUID thì bảo toàn nguyên vẹn ID đó")
    void generate_whenCurrentValueIsProvided_shouldPreserveExistingUuid() {
        UUID predefined = UUID.fromString("a2a3234f-4601-4d3f-b403-93955677411b");

        UUID generated = generator.generate(null, null, predefined, EventType.INSERT);

        assertThat(generated).isEqualTo(predefined);
    }

    @Test
    @DisplayName("Sự kiện kích hoạt chỉ gồm EventType.INSERT")
    void getEventTypes_shouldOnlyContainInsert() {
        assertThat(generator.getEventTypes()).containsExactly(EventType.INSERT);
    }
}
