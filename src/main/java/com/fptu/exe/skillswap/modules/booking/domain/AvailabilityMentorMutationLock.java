package com.fptu.exe.skillswap.modules.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "availability_mentor_mutation_locks")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityMentorMutationLock {
    @Id
    @Column(name = "mentor_user_id")
    private UUID mentorUserId;
}
