package com.fptu.exe.skillswap.modules.admin.service;

import com.fptu.exe.skillswap.modules.matching.dto.request.AdminQuestionnaireActivateRequest;
import com.fptu.exe.skillswap.modules.matching.dto.request.AdminQuestionnaireVersionCreateRequest;
import com.fptu.exe.skillswap.modules.matching.dto.response.AdminQuestionnaireActivationResponse;
import com.fptu.exe.skillswap.modules.matching.dto.response.AdminQuestionnaireVersionSummaryResponse;
import com.fptu.exe.skillswap.modules.matching.dto.response.MentoringQuestionnaireResponse;
import com.fptu.exe.skillswap.modules.matching.service.MentoringMatchProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminQuestionnaireService {

    private final MentoringMatchProfileService mentoringMatchProfileService;
    private final AdminAuditWriterService adminAuditWriterService;

    @Transactional(readOnly = true)
    public List<AdminQuestionnaireVersionSummaryResponse> listVersions() {
        return mentoringMatchProfileService.listVersions();
    }

    @Transactional(readOnly = true)
    public MentoringQuestionnaireResponse getVersion(UUID versionId) {
        return mentoringMatchProfileService.getVersion(versionId);
    }

    @Transactional
    public MentoringQuestionnaireResponse createVersion(UUID adminUserId, AdminQuestionnaireVersionCreateRequest request) {
        MentoringQuestionnaireResponse response = mentoringMatchProfileService.createVersion(request);
        
        adminAuditWriterService.writeOperatorEvent(
                adminUserId,
                "QUESTIONNAIRE_VERSION",
                response.versionId(),
                "CREATE_QUESTIONNAIRE_VERSION",
                Map.of(),
                Map.of("status", "CREATED", "versionCode", response.versionNumber().toString())
        );

        return response;
    }

    @Transactional
    public AdminQuestionnaireActivationResponse activateVersion(UUID adminUserId, AdminQuestionnaireActivateRequest request) {
        AdminQuestionnaireActivationResponse response = mentoringMatchProfileService.activateVersion(adminUserId, request);
        
        adminAuditWriterService.writeOperatorEvent(
                adminUserId,
                "QUESTIONNAIRE_VERSION",
                request.versionId(),
                "ACTIVATE_QUESTIONNAIRE_VERSION",
                Map.of("status", "INACTIVE"),
                Map.of("status", "ACTIVE", "activationId", response.activationId().toString())
        );

        return response;
    }

    @Transactional(readOnly = true)
    public AdminQuestionnaireActivationResponse getActiveActivation() {
        return mentoringMatchProfileService.getActiveActivation();
    }
}
