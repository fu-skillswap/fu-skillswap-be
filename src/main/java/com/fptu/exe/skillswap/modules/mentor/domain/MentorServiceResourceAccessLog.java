package com.fptu.exe.skillswap.modules.mentor.domain;

import com.fptu.exe.skillswap.shared.persistence.GeneratedUuidV7;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "mentor_service_resource_access_logs")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MentorServiceResourceAccessLog {
    @Id @GeneratedUuidV7 private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "resource_id") private MentorServiceResource resource;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false, length = 40) @Builder.Default private String action="GENERATE_DOWNLOAD_URL";
    @Column(nullable = false) private boolean success;
    @Column(name = "failure_reason_code", length = 80) private String failureReasonCode;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @PrePersist void create(){createdAt=LocalDateTime.now();}
}
