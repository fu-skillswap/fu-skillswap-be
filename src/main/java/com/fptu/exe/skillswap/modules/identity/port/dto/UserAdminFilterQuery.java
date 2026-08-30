package com.fptu.exe.skillswap.modules.identity.port.dto;

import com.fptu.exe.skillswap.shared.dto.request.BasePageRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserAdminFilterQuery extends BasePageRequest {
    private String keyword;
    private String role;
    private String status;
}
