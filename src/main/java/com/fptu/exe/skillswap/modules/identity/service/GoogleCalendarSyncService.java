package com.fptu.exe.skillswap.modules.identity.service;

import com.fptu.exe.skillswap.modules.booking.domain.Booking;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MeetingPlatform;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.identity.domain.*;
import com.fptu.exe.skillswap.modules.identity.event.CalendarSyncAbortedNearStartTimeEvent;
import com.fptu.exe.skillswap.modules.identity.event.CalendarSyncConnectionRevokedEvent;
import com.fptu.exe.skillswap.modules.identity.event.CalendarSyncFailedEvent;
import com.fptu.exe.skillswap.modules.identity.repository.GoogleCalendarConnectionRepository;
import com.fptu.exe.skillswap.modules.identity.repository.GoogleCalendarEventLinkRepository;
import com.fptu.exe.skillswap.modules.identity.repository.GoogleCalendarSyncJobRepository;
import com.fptu.exe.skillswap.modules.session.domain.Session;
import com.fptu.exe.skillswap.modules.session.service.SessionService;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import com.fptu.exe.skillswap.shared.util.DateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleCalendarSyncService {

    private static final List<Long> RETRY_MINUTES = List.of(1L, 5L, 15L, 30L, 60L, 180L, 360L, 720L);

    private final GoogleCalendarSyncJobRepository jobRepository;
    private final GoogleCalendarEventLinkRepository eventLinkRepository;
    private final BookingRepository bookingRepository;
    private final SessionService sessionService;
    private final GoogleCalendarConnectionService connectionService;
    private final GoogleCalendarConnectionRepository connectionRepository;
    private final GoogleCalendarApiClient apiClient;
    private final ApplicationEventPublisher eventPublisher;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @Transactional
    public void enqueueCreate(UUID bookingId) {
        enqueueJob(bookingId, GoogleCalendarSyncJobType.CREATE_BOOKING_EVENT, "BOOKING_CREATE:" + bookingId);
    }

    @Transactional
    public void enqueueUpdate(UUID bookingId, LocalDateTime bookingUpdatedAt) {
        enqueueJob(bookingId, GoogleCalendarSyncJobType.UPDATE_BOOKING_EVENT,
                "BOOKING_UPDATE:" + bookingId + ":" + (bookingUpdatedAt == null ? "unknown" : bookingUpdatedAt));
    }

    @Transactional
    public void enqueueCancel(UUID bookingId, BookingStatus status) {
        enqueueJob(bookingId, GoogleCalendarSyncJobType.CANCEL_BOOKING_EVENT, "BOOKING_CANCEL:" + bookingId + ":" + status);
    }

    public void processDueJobs() {
        List<UUID> jobIds = transactionTemplate.execute(status -> {
            List<GoogleCalendarSyncJob> jobs = jobRepository.findTop20RunnableForUpdate(
                    List.of(GoogleCalendarSyncJobStatus.PENDING, GoogleCalendarSyncJobStatus.RETRYING),
                    DateTimeUtil.now(),
                    PageRequest.of(0, 20)
            );
            return jobs.stream().map(GoogleCalendarSyncJob::getId).toList();
        });

        if (jobIds != null) {
            for (UUID jobId : jobIds) {
                processSingleJob(jobId);
            }
        }
    }

    public void processSingleJob(UUID jobId) {
        boolean claimed = Boolean.TRUE.equals(transactionTemplate.execute(status ->
                jobRepository.claimForProcessing(
                        jobId,
                        List.of(GoogleCalendarSyncJobStatus.PENDING, GoogleCalendarSyncJobStatus.RETRYING),
                        GoogleCalendarSyncJobStatus.PROCESSING,
                        DateTimeUtil.now()
                ) == 1
        ));
        if (!claimed) {
            return;
        }

        GoogleCalendarSyncJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }

        Booking booking;
        Session session;
        try {
            booking = bookingRepository.findById(job.getBookingId())
                    .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking để sync Google Calendar"));
            Session existingSession = sessionService.findByBookingId(booking.getId());
            if (existingSession == null && job.getJobType() != GoogleCalendarSyncJobType.CANCEL_BOOKING_EVENT) {
                session = sessionService.createForAcceptedBooking(booking);
            } else {
                session = existingSession;
            }
        } catch (Exception ex) {
            handleTerminalFailure(job, null, null, "INTERNAL_ERROR", ex.getMessage(), "INTERNAL_ERROR");
            return;
        }

        try {
            switch (job.getJobType()) {
                case CREATE_BOOKING_EVENT -> handleCreate(job, booking, session);
                case UPDATE_BOOKING_EVENT -> handleUpdate(job, booking, session);
                case CANCEL_BOOKING_EVENT -> handleCancel(job, booking, session);
            }

            transactionTemplate.executeWithoutResult(status -> {
                GoogleCalendarSyncJob j = jobRepository.findById(jobId).orElse(null);
                if (j != null && j.getStatus() == GoogleCalendarSyncJobStatus.PROCESSING) {
                    j.setStatus(GoogleCalendarSyncJobStatus.SUCCEEDED);
                    j.setCompletedAt(DateTimeUtil.now());
                    j.setLastErrorCode(null);
                    j.setLastErrorMessage(null);
                    jobRepository.save(j);
                }
            });
        } catch (GoogleCalendarApiClient.GoogleCalendarTransientException ex) {
            handleRetryableFailure(job, booking, session, ex.getErrorCode(), ex.getMessage());
        } catch (GoogleCalendarApiClient.GoogleCalendarApiException ex) {
            handleTerminalFailure(job, booking, session, ex.getErrorCode(), ex.getMessage(), ex.getErrorCode());
        } catch (BaseException ex) {
            handleTerminalFailure(job, booking, session, ex.getErrorCode().getCode(), ex.getMessage(), ex.getErrorCode().getCode());
        }
    }

    private void handleCreate(GoogleCalendarSyncJob job, Booking booking, Session session) {
        if (session == null) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking chưa có session để sync Google Calendar");
        }
        if (shouldAbortNearStart(job, booking)) {
            markExpiredSync(job, booking, session);
            return;
        }
        GoogleCalendarConnection connection = connectionService.getActiveConnectionForSync(job.getMentorUserId());
        if (connection == null) {
            markNotConnected(session, booking, null);
            return;
        }
        if (connection.getConnectionStatus() == GoogleCalendarConnectionStatus.REVOKED) {
            markRevoked(job, booking, session, connection);
            return;
        }
        if (eventLinkRepository.findByBookingId(booking.getId()).isPresent()) {
            markSynced(session, connection, eventLinkRepository.findByBookingId(booking.getId()).orElseThrow().getGoogleMeetUrl());
            return;
        }
        String accessToken = connectionService.resolveAccessTokenForSync(connection.getId());
        GoogleCalendarApiClient.GoogleCalendarEventResponse response = apiClient.createBookingEvent(
                accessToken,
                connection.getCalendarId(),
                "booking-" + booking.getId(),
                booking,
                session
        );
        eventLinkRepository.save(GoogleCalendarEventLink.builder()
                .bookingId(booking.getId())
                .sessionId(session.getId())
                .mentorUserId(job.getMentorUserId())
                .googleEventId(response.eventId())
                .googleMeetUrl(response.googleMeetUrl())
                .etag(response.etag())
                .eventStatus(GoogleCalendarEventStatus.ACTIVE)
                .build());
        markSynced(session, connection, response.googleMeetUrl());
    }

    private void handleUpdate(GoogleCalendarSyncJob job, Booking booking, Session session) {
        if (session == null) {
            throw new BaseException(ErrorCode.RESOURCE_CONFLICT, "Booking chưa có session để sync Google Calendar");
        }
        sessionService.updateScheduleForBooking(booking.getId(), booking.getSelectedStartTime(), booking.getSelectedEndTime());
        session = sessionService.findByBookingId(booking.getId());
        if (shouldAbortNearStart(job, booking)) {
            markExpiredSync(job, booking, session);
            return;
        }
        GoogleCalendarConnection connection = connectionService.getActiveConnectionForSync(job.getMentorUserId());
        if (connection == null) {
            markNotConnected(session, booking, null);
            return;
        }
        if (connection.getConnectionStatus() == GoogleCalendarConnectionStatus.REVOKED) {
            markRevoked(job, booking, session, connection);
            return;
        }
        GoogleCalendarEventLink link = eventLinkRepository.findByBookingId(booking.getId()).orElse(null);
        if (link == null) {
            handleCreate(job, booking, session);
            return;
        }
        String accessToken = connectionService.resolveAccessTokenForSync(connection.getId());
        GoogleCalendarApiClient.GoogleCalendarEventResponse response = apiClient.updateBookingEvent(
                accessToken,
                connection.getCalendarId(),
                link.getGoogleEventId(),
                booking,
                session
        );
        link.setEtag(response.etag());
        if (response.googleMeetUrl() != null) {
            link.setGoogleMeetUrl(response.googleMeetUrl());
        }
        link.setEventStatus(GoogleCalendarEventStatus.ACTIVE);
        eventLinkRepository.save(link);
        markSynced(session, connection, link.getGoogleMeetUrl());
    }

    private void handleCancel(GoogleCalendarSyncJob job, Booking booking, Session session) {
        GoogleCalendarEventLink link = eventLinkRepository.findByBookingId(booking.getId()).orElse(null);
        if (link == null) {
            if (session != null) {
                session.setCalendarSyncStatus(GoogleCalendarSyncStatus.CANCELLED);
                session.setCalendarLastSyncedAt(DateTimeUtil.now());
                sessionService.save(session);
            }
            return;
        }
        GoogleCalendarConnection connection = connectionService.getActiveConnectionForSync(job.getMentorUserId());
        if (connection == null) {
            if (session != null) {
                session.setCalendarSyncStatus(GoogleCalendarSyncStatus.CANCELLED);
                session.setCalendarLastSyncedAt(DateTimeUtil.now());
                sessionService.save(session);
            }
            link.setEventStatus(GoogleCalendarEventStatus.CANCELLED);
            eventLinkRepository.save(link);
            return;
        }
        if (connection.getConnectionStatus() == GoogleCalendarConnectionStatus.REVOKED) {
            markRevoked(job, booking, session, connection);
            return;
        }
        String accessToken = connectionService.resolveAccessTokenForSync(connection.getId());
        apiClient.cancelBookingEvent(accessToken, connection.getCalendarId(), link.getGoogleEventId());
        link.setEventStatus(GoogleCalendarEventStatus.CANCELLED);
        link.setLastErrorCode(null);
        link.setLastErrorMessage(null);
        eventLinkRepository.save(link);
        if (session != null) {
            session.setCalendarSyncStatus(GoogleCalendarSyncStatus.CANCELLED);
            session.setCalendarSyncErrorCode(null);
            session.setCalendarSyncErrorMessage(null);
            session.setCalendarLastSyncedAt(DateTimeUtil.now());
            sessionService.save(session);
        }
        connection.setLastSyncStatus(GoogleCalendarSyncStatus.CANCELLED);
        connection.setLastSyncAt(DateTimeUtil.now());
        connection.setLastSyncErrorCode(null);
        connection.setLastSyncErrorMessage(null);
        connectionRepository.save(connection);
    }

    private void handleRetryableFailure(GoogleCalendarSyncJob job,
                                        Booking booking,
                                        Session session,
                                        String errorCode,
                                        String errorMessage) {
        int nextAttemptIndex = job.getAttemptCount();
        if (shouldAbortNearStart(job, booking) || nextAttemptIndex >= RETRY_MINUTES.size()) {
            handleTerminalFailure(job, booking, session, errorCode, errorMessage, "GOOGLE_CALENDAR_EXPIRED_SYNC");
            return;
        }
        job.setAttemptCount(job.getAttemptCount() + 1);
        job.setStatus(GoogleCalendarSyncJobStatus.RETRYING);
        job.setRunAfter(DateTimeUtil.now().plusMinutes(RETRY_MINUTES.get(nextAttemptIndex)));
        job.setLastErrorCode(errorCode);
        job.setLastErrorMessage(errorMessage);
        jobRepository.save(job);
        if (session != null) {
            session.setCalendarSyncStatus(GoogleCalendarSyncStatus.PENDING_SYNC);
            session.setCalendarSyncErrorCode(errorCode);
            session.setCalendarSyncErrorMessage(errorMessage);
            sessionService.save(session);
        }
    }

    private void handleTerminalFailure(GoogleCalendarSyncJob job,
                                       Booking booking,
                                       Session session,
                                       String errorCode,
                                       String errorMessage,
                                       String finalCode) {
        if ("GOOGLE_CALENDAR_EXPIRED_SYNC".equals(finalCode) && session != null) {
            markExpiredSync(job, booking, session);
            return;
        }
        GoogleCalendarConnection connection = connectionService.getActiveConnectionForSync(job.getMentorUserId());
        if (connection != null && ("invalid_grant".equalsIgnoreCase(errorCode) || "insufficient_scope".equalsIgnoreCase(errorCode) || "PERMISSION_DENIED".equalsIgnoreCase(errorCode))) {
            connection.setConnectionStatus(GoogleCalendarConnectionStatus.REQUIRES_RECONNECT);
            connection.setLastSyncStatus(GoogleCalendarSyncStatus.SYNC_ERROR);
            connection.setLastSyncAt(DateTimeUtil.now());
            connection.setLastSyncErrorCode(errorCode);
            connection.setLastSyncErrorMessage(errorMessage);
            connectionRepository.save(connection);
        }
        job.setStatus(GoogleCalendarSyncJobStatus.FAILED);
        job.setCompletedAt(DateTimeUtil.now());
        job.setLastErrorCode(errorCode);
        job.setLastErrorMessage(errorMessage);
        jobRepository.save(job);
        if (session != null) {
            session.setCalendarSyncStatus(GoogleCalendarSyncStatus.SYNC_ERROR);
            session.setCalendarSyncErrorCode(errorCode);
            session.setCalendarSyncErrorMessage(errorMessage);
            session.setCalendarLastSyncedAt(DateTimeUtil.now());
            sessionService.save(session);
        }
        eventPublisher.publishEvent(new CalendarSyncFailedEvent(
                booking.getId(),
                booking.getMentorProfile().getUserId(),
                booking.getMentee().getId(),
                errorCode,
                errorMessage
        ));
    }

    private void enqueueJob(UUID bookingId, GoogleCalendarSyncJobType jobType, String idempotencyKey) {
        Booking booking = bookingRepository.findByIdForSessionUpdate(bookingId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy booking để tạo Google Calendar sync job"));
        Session session = sessionService.findByBookingId(bookingId);
        if (jobRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return;
        }
        GoogleCalendarSyncJob job = GoogleCalendarSyncJob.builder()
                .bookingId(bookingId)
                .sessionId(session == null ? null : session.getId())
                .mentorUserId(booking.getMentorProfile().getUserId())
                .jobType(jobType)
                .status(GoogleCalendarSyncJobStatus.PENDING)
                .attemptCount(0)
                .runAfter(DateTimeUtil.now())
                .idempotencyKey(idempotencyKey)
                .build();
        jobRepository.save(job);
        if (session != null) {
            session.setCalendarSyncStatus(GoogleCalendarSyncStatus.PENDING_SYNC);
            session.setCalendarSyncErrorCode(null);
            session.setCalendarSyncErrorMessage(null);
            sessionService.save(session);
        }
    }

    private boolean shouldAbortNearStart(GoogleCalendarSyncJob job, Booking booking) {
        if (job.getJobType() == GoogleCalendarSyncJobType.CANCEL_BOOKING_EVENT) {
            return false;
        }
        LocalDateTime startTime = booking.getSelectedStartTime();
        return startTime != null && !DateTimeUtil.now().isBefore(startTime.minusMinutes(15));
    }

    private void markExpiredSync(GoogleCalendarSyncJob job, Booking booking, Session session) {
        job.setStatus(GoogleCalendarSyncJobStatus.ABORTED);
        job.setCompletedAt(DateTimeUtil.now());
        job.setLastErrorCode("GOOGLE_CALENDAR_EXPIRED_SYNC");
        job.setLastErrorMessage("Quá sát giờ bắt đầu nên hệ thống ngừng retry tạo lịch tự động.");
        jobRepository.save(job);
        if (session != null) {
            session.setCalendarSyncStatus(GoogleCalendarSyncStatus.EXPIRED_SYNC);
            session.setCalendarSyncErrorCode("GOOGLE_CALENDAR_EXPIRED_SYNC");
            session.setCalendarSyncErrorMessage("Quá sát giờ bắt đầu nên hệ thống ngừng retry tạo lịch tự động.");
            session.setCalendarLastSyncedAt(DateTimeUtil.now());
            sessionService.save(session);
        }
        eventPublisher.publishEvent(new CalendarSyncAbortedNearStartTimeEvent(
                booking.getId(),
                booking.getMentorProfile().getUserId(),
                booking.getMentee().getId()
        ));
    }

    private void markNotConnected(Session session, Booking booking, GoogleCalendarConnection connection) {
        session.setGoogleCalendarManaged(false);
        session.setGoogleMeetAutoGenerated(false);
        session.setCalendarSyncStatus(GoogleCalendarSyncStatus.NOT_CONNECTED);
        session.setCalendarSyncErrorCode(null);
        session.setCalendarSyncErrorMessage(null);
        session.setCalendarLastSyncedAt(DateTimeUtil.now());
        sessionService.save(session);
        if (connection != null) {
            connection.setLastSyncStatus(GoogleCalendarSyncStatus.NOT_CONNECTED);
            connection.setLastSyncAt(DateTimeUtil.now());
            connectionRepository.save(connection);
        }
    }

    private void markSynced(Session session, GoogleCalendarConnection connection, String meetUrl) {
        session.setMeetingPlatform(MeetingPlatform.GOOGLE_MEET);
        session.setMeetingLink(meetUrl);
        session.setGoogleCalendarManaged(true);
        session.setGoogleMeetAutoGenerated(true);
        session.setCalendarSyncStatus(GoogleCalendarSyncStatus.SYNCED);
        session.setCalendarSyncErrorCode(null);
        session.setCalendarSyncErrorMessage(null);
        session.setCalendarLastSyncedAt(DateTimeUtil.now());
        sessionService.save(session);

        connection.setLastSyncStatus(GoogleCalendarSyncStatus.SYNCED);
        connection.setLastSyncAt(DateTimeUtil.now());
        connection.setLastSyncErrorCode(null);
        connection.setLastSyncErrorMessage(null);
        connectionRepository.save(connection);
    }

    private void markRevoked(GoogleCalendarSyncJob job, Booking booking, Session session, GoogleCalendarConnection connection) {
        job.setStatus(GoogleCalendarSyncJobStatus.FAILED);
        job.setCompletedAt(DateTimeUtil.now());
        job.setLastErrorCode("GOOGLE_CALENDAR_REVOKED");
        job.setLastErrorMessage("Mentor đã ngắt kết nối Google Calendar.");
        jobRepository.save(job);
        if (session != null) {
            session.setCalendarSyncStatus(GoogleCalendarSyncStatus.REVOKED);
            session.setCalendarSyncErrorCode("GOOGLE_CALENDAR_REVOKED");
            session.setCalendarSyncErrorMessage("Mentor đã ngắt kết nối Google Calendar.");
            session.setCalendarLastSyncedAt(DateTimeUtil.now());
            sessionService.save(session);
        }
        connection.setLastSyncStatus(GoogleCalendarSyncStatus.REVOKED);
        connection.setLastSyncAt(DateTimeUtil.now());
        connection.setLastSyncErrorCode("GOOGLE_CALENDAR_REVOKED");
        connection.setLastSyncErrorMessage("Mentor đã ngắt kết nối Google Calendar.");
        connectionRepository.save(connection);
        eventPublisher.publishEvent(new CalendarSyncConnectionRevokedEvent(
                booking.getId(),
                booking.getMentorProfile().getUserId(),
                booking.getMentee().getId()
        ));
    }
}
