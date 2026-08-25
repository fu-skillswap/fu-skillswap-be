package com.fptu.exe.skillswap.modules.booking.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking_issue_evidences")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingIssueEvidence {

    @Id
    @GeneratedUuidV7
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upload_intent_id", nullable = false, unique = true)
    private BookingIssueEvidenceUploadIntent uploadIntent;

    @Column(name = "submitted_by_user_id", nullable = false)
    private UUID submittedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "submission_side", length = 20)
    private BookingIssueEvidenceSubmissionSide submissionSide;

    @Column(name = "storage_key", nullable = false, unique = true, length = 600)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private BookingIssueEvidenceState state = BookingIssueEvidenceState.PENDING_ATTACH;

    @Column(name = "confirmed_at_utc", nullable = false)
    private Instant confirmedAtUtc;

    @Column(name = "attached_at_utc")
    private Instant attachedAtUtc;

    @Column(name = "hidden_at_utc")
    private Instant hiddenAtUtc;

    @Column(name = "hidden_by_user_id")
    private UUID hiddenByUserId;

    @Column(name = "hidden_reason", length = 1000)
    private String hiddenReason;

    @Column(name = "deleted_at_utc")
    private Instant deletedAtUtc;

    @Version
    @Builder.Default
    private Integer version = 0;
}
