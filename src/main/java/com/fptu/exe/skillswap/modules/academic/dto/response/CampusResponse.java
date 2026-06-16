package com.fptu.exe.skillswap.modules.academic.dto.response;

import com.fptu.exe.skillswap.modules.academic.domain.CampusCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampusResponse {
    private UUID id;
    private CampusCode code;
    private String name;
    private String city;
}
