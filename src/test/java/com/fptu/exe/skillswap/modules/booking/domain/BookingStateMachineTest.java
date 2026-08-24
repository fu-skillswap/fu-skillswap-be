package com.fptu.exe.skillswap.modules.booking.domain;

import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingStateMachineTest {

    @Test
    void acceptsEverySupportedHappyPathTransition() {
        assertEquals(BookingStatus.PAID, target(BookingStatus.PENDING, BookingTransitionCommand.ACCEPT_FREE));
        assertEquals(BookingStatus.ACCEPTED_AWAITING_PAYMENT, target(BookingStatus.PENDING, BookingTransitionCommand.ACCEPT_PAID));
        assertEquals(BookingStatus.PAID, target(BookingStatus.ACCEPTED_AWAITING_PAYMENT, BookingTransitionCommand.PAYMENT_CONFIRMED));
        assertEquals(BookingStatus.AWAITING_MENTOR_COMPLETION, target(BookingStatus.PAID, BookingTransitionCommand.SESSION_ENDED));
        assertEquals(BookingStatus.AWAITING_MENTEE_CONFIRMATION, target(BookingStatus.AWAITING_MENTOR_COMPLETION, BookingTransitionCommand.MENTOR_COMPLETED));
        assertEquals(BookingStatus.COMPLETED, target(BookingStatus.AWAITING_MENTEE_CONFIRMATION, BookingTransitionCommand.MENTEE_CONFIRMED));
        assertEquals(BookingStatus.UNDER_REVIEW, target(BookingStatus.AWAITING_MENTOR_COMPLETION, BookingTransitionCommand.ISSUE_REPORTED));
        assertEquals(BookingStatus.COMPLETED, target(BookingStatus.UNDER_REVIEW, BookingTransitionCommand.ADMIN_CONFIRM_SESSION));
    }

    @Test
    void allowsOnlyTheDefinedCancellationOrigins() {
        assertEquals(BookingStatus.CANCELLED_BY_MENTEE, target(BookingStatus.PENDING, BookingTransitionCommand.CANCEL_BY_MENTEE));
        assertEquals(BookingStatus.CANCELLED_BY_MENTEE, target(BookingStatus.ACCEPTED_AWAITING_PAYMENT, BookingTransitionCommand.CANCEL_BY_MENTEE));
        assertEquals(BookingStatus.CANCELLED_BY_MENTEE, target(BookingStatus.PAID, BookingTransitionCommand.CANCEL_BY_MENTEE));
        assertEquals(BookingStatus.CANCELLED_BY_MENTOR, target(BookingStatus.ACCEPTED_AWAITING_PAYMENT, BookingTransitionCommand.CANCEL_BY_MENTOR));
        assertEquals(BookingStatus.CANCELLED_BY_MENTOR, target(BookingStatus.PAID, BookingTransitionCommand.CANCEL_BY_MENTOR));

        assertInvalid(BookingStatus.PENDING, BookingTransitionCommand.CANCEL_BY_MENTOR);
        assertInvalid(BookingStatus.AWAITING_MENTOR_COMPLETION, BookingTransitionCommand.CANCEL_BY_MENTEE);
        assertInvalid(BookingStatus.UNDER_REVIEW, BookingTransitionCommand.CANCEL_BY_MENTEE);
    }

    @Test
    void rejectsAllCommandsFromTerminalStatuses() {
        EnumSet<BookingStatus> terminalStatuses = EnumSet.of(
                BookingStatus.REJECTED, BookingStatus.EXPIRED,
                BookingStatus.CANCELLED_BY_MENTEE, BookingStatus.CANCELLED_BY_MENTOR,
                BookingStatus.COMPLETED);
        for (BookingStatus terminal : terminalStatuses) {
            assertTrue(BookingStateMachine.isTerminal(terminal));
            for (BookingTransitionCommand command : BookingTransitionCommand.values()) {
                assertInvalid(terminal, command);
            }
        }
    }

    @Test
    void keepsScheduledAndTerminalClassificationCentralized() {
        assertTrue(BookingStateMachine.isScheduled(BookingStatus.PAID));
        assertTrue(BookingStateMachine.isScheduled(BookingStatus.AWAITING_MENTOR_COMPLETION));
        assertTrue(BookingStateMachine.isScheduled(BookingStatus.AWAITING_MENTEE_CONFIRMATION));
        assertTrue(BookingStateMachine.isTerminal(BookingStatus.COMPLETED));
    }

    private BookingStatus target(BookingStatus current, BookingTransitionCommand command) {
        return BookingStateMachine.target(current, command);
    }

    private void assertInvalid(BookingStatus current, BookingTransitionCommand command) {
        assertThrows(BaseException.class, () -> BookingStateMachine.target(current, command));
    }
}
