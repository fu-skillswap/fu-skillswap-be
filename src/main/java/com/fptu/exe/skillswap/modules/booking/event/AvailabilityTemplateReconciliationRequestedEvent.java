package com.fptu.exe.skillswap.modules.booking.event;

import java.util.UUID;

/** Published inside the booking/group transaction; handled only after a successful commit. */
public record AvailabilityTemplateReconciliationRequestedEvent(UUID templateId) {}
