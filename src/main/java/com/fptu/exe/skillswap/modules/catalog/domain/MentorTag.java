package com.fptu.exe.skillswap.modules.catalog.domain;

import com.fptu.exe.skillswap.modules.mentor.domain.MentorProfile;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "mentor_tags", indexes = {
    @Index(name = "idx_mentor_tags_tag_primary", columnList = "tag_id, is_primary"),
    @Index(name = "idx_mentor_tags_mentor_type", columnList = "mentor_user_id, tag_type")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorTag {

    @EmbeddedId
    private MentorTagId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("mentorUserId")
    @JoinColumn(name = "mentor_user_id", foreignKey = @ForeignKey(name = "fk_mentor_tags_mentor"))
    private MentorProfile mentorProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("tagId")
    @JoinColumn(name = "tag_id", foreignKey = @ForeignKey(name = "fk_mentor_tags_tag"))
    private Tag tag;



    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean isPrimary = false;

    public MentorTagType getTagType() {
        return id != null ? id.getTagType() : null;
    }
}
