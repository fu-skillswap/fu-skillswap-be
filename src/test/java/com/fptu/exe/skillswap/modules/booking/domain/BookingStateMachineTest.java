package com.fptu.exe.skillswap.modules.booking.domain;

import com.fptu.exe.skillswap.shared.exception.BaseException;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingStateMachineTest {

    private static final Map<BookingTransitionCommand, Map<BookingStatus, BookingStatus>> TRANSITIONS = transitionTable();

    @Test
    void acceptsExactlyTheClosedTransitionTable() {
        for (BookingTransitionCommand command : BookingTransitionCommand.values()) {
            Map<BookingStatus, BookingStatus> allowedOrigins = TRANSITIONS.get(command);
            for (BookingStatus current : BookingStatus.values()) {
                BookingStatus expected = allowedOrigins.get(current);
                if (expected == null) {
                    assertInvalid(current, command);
                    assertTrue(!BookingStateMachine.canTransition(current, command));
                } else {
                    assertEquals(expected, target(current, command));
                    assertTrue(BookingStateMachine.canTransition(current, command));
                }
            }
        }
    }

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

    private static Map<BookingTransitionCommand, Map<BookingStatus, BookingStatus>> transitionTable() {
        Map<BookingTransitionCommand, Map<BookingStatus, BookingStatus>> table =
                new EnumMap<>(BookingTransitionCommand.class);
        put(table, BookingTransitionCommand.ACCEPT_FREE, BookingStatus.PENDING, BookingStatus.PAID);
        put(table, BookingTransitionCommand.ACCEPT_PAID, BookingStatus.PENDING, BookingStatus.ACCEPTED_AWAITING_PAYMENT);
        put(table, BookingTransitionCommand.REJECT, BookingStatus.PENDING, BookingStatus.REJECTED);
        put(table, BookingTransitionCommand.SYSTEM_REJECT, BookingStatus.PENDING, BookingStatus.REJECTED);
        put(table, BookingTransitionCommand.EXPIRE_PENDING, BookingStatus.PENDING, BookingStatus.EXPIRED);
        put(table, BookingTransitionCommand.EXPIRE_PAYMENT, BookingStatus.ACCEPTED_AWAITING_PAYMENT, BookingStatus.EXPIRED);
        put(table, BookingTransitionCommand.CANCEL_BY_MENTEE, BookingStatus.PENDING, BookingStatus.CANCELLED_BY_MENTEE);
        put(table, BookingTransitionCommand.CANCEL_BY_MENTEE, BookingStatus.ACCEPTED_AWAITING_PAYMENT, BookingStatus.CANCELLED_BY_MENTEE);
        put(table, BookingTransitionCommand.CANCEL_BY_MENTEE, BookingStatus.PAID, BookingStatus.CANCELLED_BY_MENTEE);
        put(table, BookingTransitionCommand.CANCEL_BY_MENTOR, BookingStatus.ACCEPTED_AWAITING_PAYMENT, BookingStatus.CANCELLED_BY_MENTOR);
        put(table, BookingTransitionCommand.CANCEL_BY_MENTOR, BookingStatus.PAID, BookingStatus.CANCELLED_BY_MENTOR);
        put(table, BookingTransitionCommand.PAYMENT_CONFIRMED, BookingStatus.ACCEPTED_AWAITING_PAYMENT, BookingStatus.PAID);
        put(table, BookingTransitionCommand.SESSION_ENDED, BookingStatus.PAID, BookingStatus.AWAITING_MENTOR_COMPLETION);
        put(table, BookingTransitionCommand.MENTOR_COMPLETED, BookingStatus.AWAITING_MENTOR_COMPLETION,
                BookingStatus.AWAITING_MENTEE_CONFIRMATION);
        put(table, BookingTransitionCommand.MENTEE_CONFIRMED, BookingStatus.AWAITING_MENTOR_COMPLETION, BookingStatus.COMPLETED);
        put(table, BookingTransitionCommand.MENTEE_CONFIRMED, BookingStatus.AWAITING_MENTEE_CONFIRMATION, BookingStatus.COMPLETED);
        put(table, BookingTransitionCommand.ISSUE_REPORTED, BookingStatus.AWAITING_MENTOR_COMPLETION, BookingStatus.UNDER_REVIEW);
        put(table, BookingTransitionCommand.ISSUE_REPORTED, BookingStatus.AWAITING_MENTEE_CONFIRMATION, BookingStatus.UNDER_REVIEW);
        put(table, BookingTransitionCommand.AUTO_CLOSE, BookingStatus.AWAITING_MENTOR_COMPLETION, BookingStatus.COMPLETED);
        put(table, BookingTransitionCommand.AUTO_CLOSE, BookingStatus.AWAITING_MENTEE_CONFIRMATION, BookingStatus.COMPLETED);
        for (BookingTransitionCommand command : EnumSet.of(
                BookingTransitionCommand.AUTO_RESOLVE_MENTOR_NO_SHOW,
                BookingTransitionCommand.AUTO_RESOLVE_MENTEE_NO_SHOW,
                BookingTransitionCommand.ADMIN_CONFIRM_SESSION,
                BookingTransitionCommand.ADMIN_CONFIRM_MENTOR_NO_SHOW,
                BookingTransitionCommand.ADMIN_CONFIRM_MENTEE_NO_SHOW)) {
            put(table, command, BookingStatus.UNDER_REVIEW, BookingStatus.COMPLETED);
        }
        return table;
    }

    private static void put(Map<BookingTransitionCommand, Map<BookingStatus, BookingStatus>> table,
                            BookingTransitionCommand command,
                            BookingStatus from,
                            BookingStatus to) {
        table.computeIfAbsent(command, ignored -> new EnumMap<>(BookingStatus.class)).put(from, to);
    }
}
