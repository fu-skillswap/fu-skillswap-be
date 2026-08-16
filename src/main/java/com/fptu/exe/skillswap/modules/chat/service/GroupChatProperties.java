package com.fptu.exe.skillswap.modules.chat.service;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "application.chat.group-fanout")
@Validated
public class GroupChatProperties {

    @Min(1)
    private int batchSize = 25;

    @Min(0)
    private long batchDelayMs = 50L;
}
