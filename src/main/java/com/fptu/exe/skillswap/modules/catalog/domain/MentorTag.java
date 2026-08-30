package com.fptu.exe.skillswap.modules.catalog.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

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
    @MapsId("tagId")
    @JoinColumn(name = "tag_id", foreignKey = @ForeignKey(name = "fk_mentor_tags_tag"))
    private Tag tag;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean isPrimary = false;

    public UUID getMentorUserId() {
        return id != null ? id.getMentorUserId() : null;
    }

    public MentorTagType getTagType() {
        return id != null ? id.getTagType() : null;
    }
}
