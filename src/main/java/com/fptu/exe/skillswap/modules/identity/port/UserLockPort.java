package com.fptu.exe.skillswap.modules.identity.port;

import com.fptu.exe.skillswap.modules.identity.domain.User;

import java.util.List;
import java.util.UUID;

public interface UserLockPort {

    List<User> lockUsersForUpdate(List<UUID> userIds);
}
