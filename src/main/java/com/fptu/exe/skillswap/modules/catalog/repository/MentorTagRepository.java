package com.fptu.exe.skillswap.modules.catalog.repository;

import com.fptu.exe.skillswap.modules.catalog.domain.MentorTag;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagId;
import com.fptu.exe.skillswap.modules.catalog.domain.MentorTagType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface MentorTagRepository extends JpaRepository<MentorTag, MentorTagId> {

    @EntityGraph(attributePaths = {"tag"})
    List<MentorTag> findByIdMentorUserIdAndIdTagTypeIn(UUID mentorUserId, Collection<MentorTagType> tagTypes);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    void deleteByIdMentorUserIdAndIdTagType(UUID mentorUserId, MentorTagType tagType);
}
