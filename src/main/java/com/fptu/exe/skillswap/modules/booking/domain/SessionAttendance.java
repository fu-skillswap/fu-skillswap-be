package com.fptu.exe.skillswap.modules.booking.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable self-check-in evidence. A row proves only that the participant made a
 * server-timestamped declaration; it does not by itself decide a no-show dispute.
 */
@Entity
@Table(name = "session_attendances", uniqueConstraints = {
        @UniqueConstraint(name = "uq_session_attendances_session_role", columnNames = {"session_id", "participant_role"})
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionAttendance {

    @jakarta.persistence.Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false, foreignKey = @ForeignKey(name = "fk_session_attendances_session"))
    private Session session;

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_role", nullable = false, length = 20, updatable = false)
    private SessionParticipantRole participantRole;

    @Column(name = "participant_user_id", nullable = false, updatable = false)
    private UUID participantUserId;

    @Column(name = "checked_in_at_utc", nullable = false, updatable = false)
    private Instant checkedInAtUtc;
}
