package com.fptu.exe.skillswap.modules.booking.support;

import com.fptu.exe.skillswap.modules.mentor.service.DummyMentorInternalService;

public class DummyBookingViolatingConsumer {
    private DummyMentorInternalService service = new DummyMentorInternalService();

    public DummyMentorInternalService getService() {
        return service;
    }

    public void callInternal() {
        new DummyMentorInternalService();
    }
}
