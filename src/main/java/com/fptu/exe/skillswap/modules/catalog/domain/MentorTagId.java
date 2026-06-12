package com.fptu.exe.skillswap.modules.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MentorTagId implements Serializable {

    @Column(name = "mentor_user_id")
    private UUID mentorUserId;

    @Column(name = "tag_id")
    private UUID tagId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag_type", length = 30)
    private MentorTagType tagType;
}
