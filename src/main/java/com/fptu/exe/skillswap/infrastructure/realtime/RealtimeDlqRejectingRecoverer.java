package com.fptu.exe.skillswap.infrastructure.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;

@RequiredArgsConstructor
public class RealtimeDlqRejectingRecoverer implements MessageRecoverer {

    private final RealtimeDlqErrorLogger errorLogger;
    private final String queueName;

    @Override
    public void recover(Message message, Throwable cause) {
        errorLogger.logDeadLetter(message, cause, queueName);
        throw new AmqpRejectAndDontRequeueException("Realtime message moved to DLQ", cause);
    }
}
