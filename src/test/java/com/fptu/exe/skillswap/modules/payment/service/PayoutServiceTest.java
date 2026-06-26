package com.fptu.exe.skillswap.modules.payment.service;

import com.fptu.exe.skillswap.modules.payment.domain.MentorPayoutProfile;
import com.fptu.exe.skillswap.modules.payment.domain.PayoutRequest;
import com.fptu.exe.skillswap.modules.payment.domain.PayoutRequestStatus;
import com.fptu.exe.skillswap.modules.payment.domain.SettlementAccount;
import com.fptu.exe.skillswap.modules.payment.dto.request.AdminPayoutRequestListRequest;
import com.fptu.exe.skillswap.modules.payment.dto.request.PayoutRequestCreateRequest;
import com.fptu.exe.skillswap.modules.payment.dto.response.PayoutRequestResponse;
import com.fptu.exe.skillswap.modules.payment.repository.PayoutRequestRepository;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayoutServiceTest {

    @Mock
    private SettlementService settlementService;

    @Mock
    private PayoutRequestRepository payoutRequestRepository;

    @Mock
    private MentorPayoutProfileService payoutProfileService;

    @InjectMocks
    private PayoutService payoutService;

    @Test
    void createRequest_shouldSnapshotPayoutProfileWithoutHoldingImmediately() {
        UUID mentorUserId = UUID.randomUUID();
        UUID payoutProfileId = UUID.randomUUID();
        SettlementAccount account = SettlementAccount.builder().id(UUID.randomUUID()).build();
        MentorPayoutProfile profile = MentorPayoutProfile.builder()
                .id(payoutProfileId)
                .mentorUserId(mentorUserId)
                .accountHolderName("VO QUANG TAM")
                .bankName("ACB")
                .accountNumber("1234567890")
                .isDefault(true)
                .isActive(true)
                .build();

        when(settlementService.getMentorAvailableSettlement(mentorUserId)).thenReturn(500);
        when(settlementService.ensureMentorAccount(mentorUserId)).thenReturn(account);
        when(payoutProfileService.getActiveProfileForPayout(mentorUserId, payoutProfileId)).thenReturn(profile);
        when(payoutRequestRepository.save(any(PayoutRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PayoutRequestResponse response = payoutService.createRequest(
                mentorUserId,
                new PayoutRequestCreateRequest(200, payoutProfileId, "Need payout")
        );

        assertEquals(payoutProfileId, response.payoutProfileId());
        assertEquals("ACB", response.bankNameSnapshot());
        assertEquals("******7890", response.bankAccountNumberMaskedSnapshot());
        verify(settlementService, never()).holdPayout(any(), any(), anyInt(), any());
    }

    @Test
    void approve_shouldHoldSettlement() {
        UUID payoutRequestId = UUID.randomUUID();
        UUID mentorUserId = UUID.randomUUID();
        PayoutRequest payoutRequest = PayoutRequest.builder()
                .id(payoutRequestId)
                .mentorUserId(mentorUserId)
                .payoutProfileId(UUID.randomUUID())
                .settlementAccountId(UUID.randomUUID())
                .amountScoin(200)
                .status(PayoutRequestStatus.REQUESTED)
                .bankAccountNameSnapshot("VO QUANG TAM")
                .bankNameSnapshot("ACB")
                .bankAccountNumberMaskedSnapshot("******7890")
                .build();

        when(payoutRequestRepository.findById(payoutRequestId)).thenReturn(Optional.of(payoutRequest));
        when(payoutRequestRepository.save(any(PayoutRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PayoutRequestResponse response = payoutService.approve(UUID.randomUUID(), payoutRequestId, "ok");

        assertEquals(PayoutRequestStatus.APPROVED, response.status());
        verify(settlementService).holdPayout(eq(mentorUserId), eq(payoutRequestId), eq(200), eq("ok"));
    }

    @Test
    void rejectApproved_shouldVoidHold() {
        UUID payoutRequestId = UUID.randomUUID();
        UUID mentorUserId = UUID.randomUUID();
        PayoutRequest payoutRequest = PayoutRequest.builder()
                .id(payoutRequestId)
                .mentorUserId(mentorUserId)
                .payoutProfileId(UUID.randomUUID())
                .settlementAccountId(UUID.randomUUID())
                .amountScoin(200)
                .status(PayoutRequestStatus.APPROVED)
                .bankAccountNameSnapshot("VO QUANG TAM")
                .bankNameSnapshot("ACB")
                .bankAccountNumberMaskedSnapshot("******7890")
                .build();

        when(payoutRequestRepository.findById(payoutRequestId)).thenReturn(Optional.of(payoutRequest));
        when(payoutRequestRepository.save(any(PayoutRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PayoutRequestResponse response = payoutService.reject(UUID.randomUUID(), payoutRequestId, "reject");

        assertEquals(PayoutRequestStatus.REJECTED, response.status());
        verify(settlementService).voidPayoutHold(eq(mentorUserId), eq(payoutRequestId), any());
    }

    @Test
    void getAdminPayoutRequests_shouldReturnPagedQueue() {
        UUID mentorUserId = UUID.randomUUID();
        PayoutRequest payoutRequest = PayoutRequest.builder()
                .id(UUID.randomUUID())
                .mentorUserId(mentorUserId)
                .payoutProfileId(UUID.randomUUID())
                .settlementAccountId(UUID.randomUUID())
                .amountScoin(150)
                .status(PayoutRequestStatus.REQUESTED)
                .bankAccountNameSnapshot("VO QUANG TAM")
                .bankNameSnapshot("ACB")
                .bankAccountNumberMaskedSnapshot("******7890")
                .build();
        AdminPayoutRequestListRequest request = new AdminPayoutRequestListRequest();
        request.setMentorUserId(mentorUserId);
        request.setStatus(PayoutRequestStatus.REQUESTED);

        when(payoutRequestRepository.searchForAdmin(eq(PayoutRequestStatus.REQUESTED), eq(mentorUserId), any()))
                .thenReturn(new PageImpl<>(java.util.List.of(payoutRequest), PageRequest.of(0, 20), 1));

        PageResponse<PayoutRequestResponse> response = payoutService.getAdminPayoutRequests(request);

        assertEquals(1, response.getContent().size());
        assertEquals(mentorUserId, response.getContent().getFirst().mentorUserId());
    }

    @Test
    void getAdminPayoutRequestDetail_shouldReturnMappedResponse() {
        UUID payoutRequestId = UUID.randomUUID();
        PayoutRequest payoutRequest = PayoutRequest.builder()
                .id(payoutRequestId)
                .mentorUserId(UUID.randomUUID())
                .payoutProfileId(UUID.randomUUID())
                .settlementAccountId(UUID.randomUUID())
                .amountScoin(300)
                .status(PayoutRequestStatus.PAID)
                .bankAccountNameSnapshot("VO QUANG TAM")
                .bankNameSnapshot("VCB")
                .bankAccountNumberMaskedSnapshot("******1234")
                .build();

        when(payoutRequestRepository.findById(payoutRequestId)).thenReturn(Optional.of(payoutRequest));

        PayoutRequestResponse response = payoutService.getAdminPayoutRequestDetail(payoutRequestId);

        assertEquals(payoutRequestId, response.payoutRequestId());
        assertEquals(PayoutRequestStatus.PAID, response.status());
    }
}
