package com.fptu.exe.skillswap.modules.catalog.domain;

import com.fptu.exe.skillswap.modules.academic.domain.Specialization;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "specialization_tags", indexes = {
    @Index(name = "idx_spec_tags_tag_id", columnList = "tag_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpecializationTag {

    @EmbeddedId
    private SpecializationTagId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("specializationId")
    @JoinColumn(name = "specialization_id", foreignKey = @ForeignKey(name = "fk_spec_tags_spec"))
    private Specialization specialization;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("tagId")
    @JoinColumn(name = "tag_id", foreignKey = @ForeignKey(name = "fk_spec_tags_tag"))
    private Tag tag;
}
