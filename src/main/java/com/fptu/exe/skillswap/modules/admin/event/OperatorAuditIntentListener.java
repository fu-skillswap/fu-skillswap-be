package com.fptu.exe.skillswap.modules.admin.event;

import com.fptu.exe.skillswap.modules.admin.service.AdminAuditWriterService;
import com.fptu.exe.skillswap.shared.event.OperatorAuditIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OperatorAuditIntentListener {
    private final AdminAuditWriterService adminAuditWriterService;

    @EventListener
    public void on(OperatorAuditIntent intent) {
        adminAuditWriterService.writeOperatorEvent(intent.actorUserId(), intent.entityType(), intent.entityId(),
                intent.operatorEventType(), intent.oldValue(), intent.newValue());
    }
}
