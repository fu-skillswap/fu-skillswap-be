package com.fptu.exe.skillswap.modules.booking;

import com.fptu.exe.skillswap.modules.academic.dto.request.StudentProfileRequest;
import com.fptu.exe.skillswap.modules.academic.repository.AcademicProgramRepository;
import com.fptu.exe.skillswap.modules.academic.repository.CampusRepository;
import com.fptu.exe.skillswap.modules.academic.repository.SpecializationRepository;
import com.fptu.exe.skillswap.modules.academic.service.AcademicService;
import com.fptu.exe.skillswap.modules.booking.domain.BookingStatus;
import com.fptu.exe.skillswap.modules.booking.domain.MentorAvailabilitySlot;
import com.fptu.exe.skillswap.modules.booking.dto.request.AcceptBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.request.CreateBookingRequest;
import com.fptu.exe.skillswap.modules.booking.dto.response.BookingResponse;
import com.fptu.exe.skillswap.modules.booking.repository.BookingRepository;
import com.fptu.exe.skillswap.modules.booking.repository.MentorAvailabilitySlotRepository;
import com.fptu.exe.skillswap.modules.booking.service.BookingService;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.modules.identity.repository.UserRepository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorStatus;
import com.fptu.exe.skillswap.modules.mentor.domain.TeachingMode;
import com.fptu.exe.skillswap.modules.mentor.repository.MentorProfileRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
class BookingConcurrencyIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MentorProfileRepository mentorProfileRepository;

    @Autowired
    private MentorAvailabilitySlotRepository mentorAvailabilitySlotRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private AcademicService academicService;

    @Autowired
    private CampusRepository campusRepository;

    @Autowired
    private AcademicProgramRepository academicProgramRepository;

    @Autowired
    private SpecializationRepository specializationRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private com.fptu.exe.skillswap.modules.feedback.service.SessionFeedbackService sessionFeedbackService;

    @Autowired
    private com.fptu.exe.skillswap.modules.feedback.repository.SessionFeedbackRepository sessionFeedbackRepository;

    @Test
    void mentorAccept_concurrentAcceptSameSlot_onlyOneSucceeds() throws Exception {
        SetupData setupData = transactionTemplate.execute(status -> {
            User mentorUser = userRepository.save(User.builder()
                    .email(uniqueEmail("mentor"))
                    .fullName("Phan Hoang Minh")
                    .status(UserStatus.ACTIVE)
                    .build());

            User menteeA = userRepository.save(User.builder()
                    .email(uniqueEmail("mentee-a"))
                    .fullName("Nguyen Quoc An")
                    .status(UserStatus.ACTIVE)
                    .build());
            User menteeB = userRepository.save(User.builder()
                    .email(uniqueEmail("mentee-b"))
                    .fullName("Tran Gia Bao")
                    .status(UserStatus.ACTIVE)
                    .build());

            completeAcademicProfile(menteeA.getId(), "SE" + randomSixDigits());
            completeAcademicProfile(menteeB.getId(), "SE" + randomSixDigits());

            MentorProfile mentorProfile = mentorProfileRepository.save(MentorProfile.builder()
                    .user(mentorUser)
                    .status(MentorStatus.ACTIVE)
                    .verifiedAt(LocalDateTime.now().minusDays(1))
                    .isAvailable(true)
                    .headline("Spring Boot Mentor")
                    .expertiseDescription("Hỗ trợ Java backend và thiết kế REST API.")
                    .teachingMode(TeachingMode.ONLINE)
                    .sessionDuration(60)
                    .build());

            MentorAvailabilitySlot slot = mentorAvailabilitySlotRepository.save(MentorAvailabilitySlot.builder()
                    .mentorProfile(mentorProfile)
                    .startTime(LocalDateTime.now().plusDays(2))
                    .endTime(LocalDateTime.now().plusDays(2).plusHours(1))
                    .timezone("Asia/Ho_Chi_Minh")
                    .isActive(true)
                    .isBooked(false)
                    .build());

            return new SetupData(mentorUser.getId(), slot.getId(), menteeA.getId(), menteeB.getId());
        });
        assertNotNull(setupData);

        BookingResponse bookingA = bookingService.createBooking(
                setupData.menteeAId(),
                new CreateBookingRequest(setupData.mentorId(), setupData.slotId(), null, "Need help A", "Spring transaction")
        );
        BookingResponse bookingB = bookingService.createBooking(
                setupData.menteeBId(),
                new CreateBookingRequest(setupData.mentorId(), setupData.slotId(), null, "Need help B", "REST API")
        );

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executorService.submit(acceptTask(setupData.mentorId(), bookingA.bookingId(), readyLatch, startLatch));
            Future<Boolean> second = executorService.submit(acceptTask(setupData.mentorId(), bookingB.bookingId(), readyLatch, startLatch));

            assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
            startLatch.countDown();

            boolean firstSucceeded = getFuture(first);
            boolean secondSucceeded = getFuture(second);

            assertEquals(1, (firstSucceeded ? 1 : 0) + (secondSucceeded ? 1 : 0));

            List<com.fptu.exe.skillswap.modules.booking.domain.Booking> bookings =
                    bookingRepository.findBySlotIdAndStatus(setupData.slotId(), BookingStatus.ACCEPTED);
            assertEquals(1, bookings.size());

            long rejectedCount = bookingRepository.findBySlotIdAndStatus(setupData.slotId(), BookingStatus.REJECTED).size();
            assertEquals(1L, rejectedCount);

            MentorAvailabilitySlot storedSlot = mentorAvailabilitySlotRepository.findById(setupData.slotId()).orElseThrow();
            assertTrue(storedSlot.isBooked());
        } finally {
            executorService.shutdownNow();
        }
    }

    private Callable<Boolean> acceptTask(UUID mentorId, UUID bookingId, CountDownLatch readyLatch, CountDownLatch startLatch) {
        return () -> {
            readyLatch.countDown();
            assertTrue(startLatch.await(5, TimeUnit.SECONDS));
            try {
                bookingService.acceptBooking(mentorId, bookingId, new AcceptBookingRequest("Confirmed"));
                return true;
            } catch (Exception exception) {
                throw new RuntimeException("acceptTask failed", exception);
            }
        };
    }

    private boolean getFuture(Future<Boolean> future) throws InterruptedException {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException executionException) {
            fail(executionException.getCause());
            return false;
        } catch (java.util.concurrent.TimeoutException timeoutException) {
            fail(timeoutException);
            return false;
        }
    }

    private void completeAcademicProfile(UUID userId, String studentCode) {
        var campus = campusRepository.findAll().stream().findFirst().orElseThrow();
        var program = academicProgramRepository.findAll().stream().findFirst().orElseThrow();
        var specialization = specializationRepository.findByProgramIdAndIsActiveTrue(program.getId()).stream().findFirst().orElseThrow();

        academicService.updateStudentProfile(userId, StudentProfileRequest.builder()
                .studentCode(studentCode)
                .campusId(campus.getId())
                .programId(program.getId())
                .specializationId(specialization.getId())
                .semester(5)
                .intakeYear(2022)
                .isAlumni(false)
                .bio("Concurrency test profile")
                .build());
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@test.com";
    }

    private String randomSixDigits() {
        String value = String.valueOf(Math.abs(UUID.randomUUID().hashCode()));
        return value.substring(0, Math.min(6, value.length()));
    }

    @Test
    void concurrentFeedbackSubmissions_updatesRatingCorrectly() throws Exception {
        SetupFeedbackData setupData = transactionTemplate.execute(status -> {
            User mentorUser = userRepository.save(User.builder()
                    .email(uniqueEmail("mentor-fb"))
                    .fullName("Phan Hoang Minh Feedback")
                    .status(UserStatus.ACTIVE)
                    .build());

            User mentee1 = userRepository.save(User.builder()
                    .email(uniqueEmail("mentee-1"))
                    .fullName("Nguyen Quoc An 1")
                    .status(UserStatus.ACTIVE)
                    .build());
            User mentee2 = userRepository.save(User.builder()
                    .email(uniqueEmail("mentee-2"))
                    .fullName("Tran Gia Bao 2")
                    .status(UserStatus.ACTIVE)
                    .build());
            User mentee3 = userRepository.save(User.builder()
                    .email(uniqueEmail("mentee-3"))
                    .fullName("Tran Gia Bao 3")
                    .status(UserStatus.ACTIVE)
                    .build());

            completeAcademicProfile(mentee1.getId(), "SE" + randomSixDigits());
            completeAcademicProfile(mentee2.getId(), "SE" + randomSixDigits());
            completeAcademicProfile(mentee3.getId(), "SE" + randomSixDigits());

            MentorProfile mentorProfile = mentorProfileRepository.save(MentorProfile.builder()
                    .user(mentorUser)
                    .status(MentorStatus.ACTIVE)
                    .verifiedAt(LocalDateTime.now().minusDays(1))
                    .isAvailable(true)
                    .headline("Spring Boot Mentor")
                    .expertiseDescription("Hỗ trợ Java backend")
                    .teachingMode(TeachingMode.ONLINE)
                    .sessionDuration(60)
                    .build());

            MentorAvailabilitySlot slot1 = mentorAvailabilitySlotRepository.save(MentorAvailabilitySlot.builder()
                    .mentorProfile(mentorProfile)
                    .startTime(LocalDateTime.now().plusDays(2))
                    .endTime(LocalDateTime.now().plusDays(2).plusHours(1))
                    .timezone("Asia/Ho_Chi_Minh")
                    .isActive(true)
                    .isBooked(true)
                    .build());

            MentorAvailabilitySlot slot2 = mentorAvailabilitySlotRepository.save(MentorAvailabilitySlot.builder()
                    .mentorProfile(mentorProfile)
                    .startTime(LocalDateTime.now().plusDays(3))
                    .endTime(LocalDateTime.now().plusDays(3).plusHours(1))
                    .timezone("Asia/Ho_Chi_Minh")
                    .isActive(true)
                    .isBooked(true)
                    .build());

            MentorAvailabilitySlot slot3 = mentorAvailabilitySlotRepository.save(MentorAvailabilitySlot.builder()
                    .mentorProfile(mentorProfile)
                    .startTime(LocalDateTime.now().plusDays(4))
                    .endTime(LocalDateTime.now().plusDays(4).plusHours(1))
                    .timezone("Asia/Ho_Chi_Minh")
                    .isActive(true)
                    .isBooked(true)
                    .build());

            com.fptu.exe.skillswap.modules.booking.domain.Booking booking1 = bookingRepository.save(com.fptu.exe.skillswap.modules.booking.domain.Booking.builder()
                    .mentee(mentee1)
                    .mentorProfile(mentorProfile)
                    .slot(slot1)
                    .learningGoalTitle("Goal 1")
                    .status(BookingStatus.COMPLETED)
                    .requestedStartTime(slot1.getStartTime())
                    .requestedEndTime(slot1.getEndTime())
                    .build());

            com.fptu.exe.skillswap.modules.booking.domain.Booking booking2 = bookingRepository.save(com.fptu.exe.skillswap.modules.booking.domain.Booking.builder()
                    .mentee(mentee2)
                    .mentorProfile(mentorProfile)
                    .slot(slot2)
                    .learningGoalTitle("Goal 2")
                    .status(BookingStatus.COMPLETED)
                    .requestedStartTime(slot2.getStartTime())
                    .requestedEndTime(slot2.getEndTime())
                    .build());

            com.fptu.exe.skillswap.modules.booking.domain.Booking booking3 = bookingRepository.save(com.fptu.exe.skillswap.modules.booking.domain.Booking.builder()
                    .mentee(mentee3)
                    .mentorProfile(mentorProfile)
                    .slot(slot3)
                    .learningGoalTitle("Goal 3")
                    .status(BookingStatus.COMPLETED)
                    .requestedStartTime(slot3.getStartTime())
                    .requestedEndTime(slot3.getEndTime())
                    .build());

            return new SetupFeedbackData(mentorUser.getId(), booking1.getId(), booking2.getId(), booking3.getId(), mentee1.getId(), mentee2.getId(), mentee3.getId());
        });
        assertNotNull(setupData);

        CountDownLatch readyLatch = new CountDownLatch(3);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(3);

        try {
            Future<Boolean> f1 = executorService.submit(submitFeedbackTask(setupData.mentee1Id(), setupData.booking1Id(), 5, readyLatch, startLatch));
            Future<Boolean> f2 = executorService.submit(submitFeedbackTask(setupData.mentee2Id(), setupData.booking2Id(), 4, readyLatch, startLatch));
            Future<Boolean> f3 = executorService.submit(submitFeedbackTask(setupData.mentee3Id(), setupData.booking3Id(), 3, readyLatch, startLatch));

            assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
            startLatch.countDown();

            assertTrue(f1.get(10, TimeUnit.SECONDS));
            assertTrue(f2.get(10, TimeUnit.SECONDS));
            assertTrue(f3.get(10, TimeUnit.SECONDS));

            MentorProfile updatedProfile = mentorProfileRepository.findById(setupData.mentorId()).orElseThrow();
            assertEquals(3, updatedProfile.getTotalReviews());
            assertEquals(0, BigDecimal.valueOf(4.00).setScale(2).compareTo(updatedProfile.getAverageRating()));
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void concurrentFeedbackAndCancellationPenalty_updatesBothCorrectly() throws Exception {
        SetupFeedbackAndCancelData setupData = transactionTemplate.execute(status -> {
            User mentorUser = userRepository.save(User.builder()
                    .email(uniqueEmail("mentor-fc"))
                    .fullName("Mentor Feedback Cancel")
                    .status(UserStatus.ACTIVE)
                    .build());

            User mentee1 = userRepository.save(User.builder()
                    .email(uniqueEmail("mentee-fc1"))
                    .fullName("Mentee FC 1")
                    .status(UserStatus.ACTIVE)
                    .build());
            User mentee2 = userRepository.save(User.builder()
                    .email(uniqueEmail("mentee-fc2"))
                    .fullName("Mentee FC 2")
                    .status(UserStatus.ACTIVE)
                    .build());

            completeAcademicProfile(mentee1.getId(), "SE" + randomSixDigits());
            completeAcademicProfile(mentee2.getId(), "SE" + randomSixDigits());

            MentorProfile mentorProfile = mentorProfileRepository.save(MentorProfile.builder()
                    .user(mentorUser)
                    .status(MentorStatus.ACTIVE)
                    .verifiedAt(LocalDateTime.now().minusDays(1))
                    .isAvailable(true)
                    .headline("Spring Boot Mentor")
                    .expertiseDescription("Hỗ trợ Java backend")
                    .teachingMode(TeachingMode.ONLINE)
                    .sessionDuration(60)
                    .lateCancellationPenaltyPoints(BigDecimal.valueOf(1.00))
                    .build());

            MentorAvailabilitySlot slot1 = mentorAvailabilitySlotRepository.save(MentorAvailabilitySlot.builder()
                    .mentorProfile(mentorProfile)
                    .startTime(LocalDateTime.now().plusDays(2))
                    .endTime(LocalDateTime.now().plusDays(2).plusHours(1))
                    .timezone("Asia/Ho_Chi_Minh")
                    .isActive(true)
                    .isBooked(true)
                    .build());

            MentorAvailabilitySlot slot2 = mentorAvailabilitySlotRepository.save(MentorAvailabilitySlot.builder()
                    .mentorProfile(mentorProfile)
                    .startTime(LocalDateTime.now().plusHours(5))
                    .endTime(LocalDateTime.now().plusHours(6))
                    .timezone("Asia/Ho_Chi_Minh")
                    .isActive(true)
                    .isBooked(true)
                    .build());

            com.fptu.exe.skillswap.modules.booking.domain.Booking booking1 = bookingRepository.save(com.fptu.exe.skillswap.modules.booking.domain.Booking.builder()
                    .mentee(mentee1)
                    .mentorProfile(mentorProfile)
                    .slot(slot1)
                    .learningGoalTitle("Completed Booking")
                    .status(BookingStatus.COMPLETED)
                    .requestedStartTime(slot1.getStartTime())
                    .requestedEndTime(slot1.getEndTime())
                    .build());

            com.fptu.exe.skillswap.modules.booking.domain.Booking booking2 = bookingRepository.save(com.fptu.exe.skillswap.modules.booking.domain.Booking.builder()
                    .mentee(mentee2)
                    .mentorProfile(mentorProfile)
                    .slot(slot2)
                    .learningGoalTitle("Accepted Booking")
                    .status(BookingStatus.ACCEPTED)
                    .requestedStartTime(slot2.getStartTime())
                    .requestedEndTime(slot2.getEndTime())
                    .build());

            return new SetupFeedbackAndCancelData(mentorUser.getId(), booking1.getId(), booking2.getId(), mentee1.getId());
        });
        assertNotNull(setupData);

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> feedbackFuture = executorService.submit(submitFeedbackTask(setupData.menteeId(), setupData.bookingCompletedId(), 5, readyLatch, startLatch));
            Future<Boolean> cancelFuture = executorService.submit(cancelBookingTask(setupData.mentorId(), setupData.bookingAcceptedId(), readyLatch, startLatch));

            assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
            startLatch.countDown();

            assertTrue(feedbackFuture.get(10, TimeUnit.SECONDS));
            assertTrue(cancelFuture.get(10, TimeUnit.SECONDS));

            MentorProfile updatedProfile = mentorProfileRepository.findById(setupData.mentorId()).orElseThrow();
            assertEquals(1, updatedProfile.getTotalReviews());
            assertEquals(0, BigDecimal.valueOf(5.00).setScale(2).compareTo(updatedProfile.getAverageRating()));
            assertNotNull(updatedProfile.getBookingSuspendedUntil());
            assertEquals(0, BigDecimal.valueOf(1.00).setScale(2).compareTo(updatedProfile.getLateCancellationPenaltyPoints()));
        } finally {
            executorService.shutdownNow();
        }
    }

    private Callable<Boolean> submitFeedbackTask(UUID reviewerId, UUID bookingId, int rating, CountDownLatch readyLatch, CountDownLatch startLatch) {
        return () -> {
            readyLatch.countDown();
            assertTrue(startLatch.await(5, TimeUnit.SECONDS));
            try {
                com.fptu.exe.skillswap.modules.feedback.dto.request.SubmitFeedbackRequest req = new com.fptu.exe.skillswap.modules.feedback.dto.request.SubmitFeedbackRequest();
                req.setRating(rating);
                req.setSatisfactionLevel(rating);
                req.setComment("Comment " + rating);
                req.setWouldRecommend(true);
                req.setIsPublic(true);
                sessionFeedbackService.submitFeedback(reviewerId, bookingId, req);
                return true;
            } catch (Exception e) {
                throw new RuntimeException("submitFeedbackTask failed", e);
            }
        };
    }

    private Callable<Boolean> cancelBookingTask(UUID mentorId, UUID bookingId, CountDownLatch readyLatch, CountDownLatch startLatch) {
        return () -> {
            readyLatch.countDown();
            assertTrue(startLatch.await(5, TimeUnit.SECONDS));
            try {
                bookingService.cancelBookingByMentor(mentorId, bookingId, new com.fptu.exe.skillswap.modules.booking.dto.request.CancelBookingRequest("Late cancellation"));
                return true;
            } catch (Exception e) {
                throw new RuntimeException("cancelBookingTask failed", e);
            }
        };
    }

    private record SetupData(UUID mentorId, UUID slotId, UUID menteeAId, UUID menteeBId) {}
    private record SetupFeedbackData(UUID mentorId, UUID booking1Id, UUID booking2Id, UUID booking3Id, UUID mentee1Id, UUID mentee2Id, UUID mentee3Id) {}
    private record SetupFeedbackAndCancelData(UUID mentorId, UUID bookingCompletedId, UUID bookingAcceptedId, UUID menteeId) {}
}
