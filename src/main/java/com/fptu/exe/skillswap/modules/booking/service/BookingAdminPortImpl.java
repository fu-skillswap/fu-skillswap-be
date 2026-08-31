package com.fptu.exe.skillswap.modules.booking.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptu.exe.skillswap.modules.booking.domain.AdminBookingIssueResolutionAction;
import com.fptu.exe.skillswap.modules.booking.domain.AdminBookingIssueResolutionReasonCode;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.dto.request.AdminBookingListRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.AdminResolveBookingIssueRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.AdminReverseResolutionRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.port.BookingAdminPort;
import com.fptu.exe.skillswap.shared.dto.response.PageResponse;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;

/** Maps Booking's internal web DTO at its owner boundary. */
@Service
@RequiredArgsConstructor
class BookingAdminPortImpl implements BookingAdminPort {
    private final BookingService bookingService;
    private final ObjectMapper objectMapper;
    public PageResponse<Map<String, Object>> getAdminBookings(AdminBookingQuery query) {
        PageResponse<BookingResponse> page = bookingService.getAdminBookings(toRequest(query));
        return PageResponse.<Map<String, Object>>builder().content(page.getContent().stream().map(this::view).toList())
                .page(page.getPage()).size(page.getSize()).totalElements(page.getTotalElements()).totalPages(page.getTotalPages()).last(page.isLast()).build();
    }
    public Map<String, Object> getAdminBookingDetail(UUID id) { return view(bookingService.getAdminBookingDetail(id)); }
    public Map<String, Object> resolveBookingIssue(UUID adminId, UUID id, ResolveBookingIssueCommand c) {
        return view(bookingService.resolveBookingIssue(adminId, id, new AdminResolveBookingIssueRequest(value(AdminBookingIssueResolutionAction.class,c.action()), value(AdminBookingIssueResolutionReasonCode.class,c.reasonCode()),c.adminNote(),c.menteeBps(),c.mentorBps(),c.platformBps())));
    }
    public Map<String, Object> reverseBookingIssueResolution(UUID adminId, UUID id, ReverseBookingIssueResolutionCommand c) {
        return view(bookingService.reverseBookingIssueResolution(adminId, id, new AdminReverseResolutionRequest(value(AdminBookingIssueResolutionReasonCode.class,c.reasonCode()),c.adminNote())));
    }
    public List<String> bookingStatusNames() { return java.util.Arrays.stream(BookingStatus.values()).map(Enum::name).toList(); }
    private AdminBookingListRequest toRequest(AdminBookingQuery q) {
        AdminBookingListRequest r = new AdminBookingListRequest(); if(q==null)return r;
        r.setStatus(q.getStatus()==null||q.getStatus().isBlank()?null:value(BookingStatus.class,q.getStatus())); r.setMentorUserId(q.getMentorUserId()); r.setMenteeUserId(q.getMenteeUserId());
        r.setPage(Math.max(0,q.getPage())); r.setSize(Math.max(1,q.getSize())); r.setSortBy(q.getSortBy()); r.setDirection(q.getDirection()); return r;
    }
    private Map<String,Object> view(BookingResponse response) { return Collections.unmodifiableMap(objectMapper.convertValue(response,new TypeReference<LinkedHashMap<String,Object>>(){})); }
    private <T extends Enum<T>> T value(Class<T> type,String raw) { try{return Enum.valueOf(type,raw);}catch(RuntimeException ex){throw new BaseException(ErrorCode.BAD_REQUEST,"Giá trị admin booking không hợp lệ");} }
}
