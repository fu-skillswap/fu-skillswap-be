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
                return false;
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

    private record SetupData(UUID mentorId, UUID slotId, UUID menteeAId, UUID menteeBId) {
    }
}
