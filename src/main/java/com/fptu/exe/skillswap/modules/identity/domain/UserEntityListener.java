package com.fptu.exe.skillswap.modules.identity.domain;

import com.fptu.exe.skillswap.shared.event.UserDeletedEvent;
import com.fptu.exe.skillswap.shared.util.ApplicationContextProvider;
import jakarta.persistence.PreRemove;

public class UserEntityListener {

    @PreRemove
    public void preRemove(User user) {
        if (user.getId() != null) {
            ApplicationContextProvider.publishEvent(new UserDeletedEvent(user.getId()));
        }
    }
}
