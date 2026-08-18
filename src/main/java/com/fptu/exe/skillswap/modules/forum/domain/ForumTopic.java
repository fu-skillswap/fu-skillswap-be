package com.fptu.exe.skillswap.modules.forum.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "forum_topics")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForumTopic {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 30)
    private ForumTopicCode code;

    @Column(name = "name_vi", nullable = false, length = 100)
    private String nameVi;

    @Column(name = "name_en", nullable = false, length = 100)
    private String nameEn;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(nullable = false)
    private boolean active;
}
