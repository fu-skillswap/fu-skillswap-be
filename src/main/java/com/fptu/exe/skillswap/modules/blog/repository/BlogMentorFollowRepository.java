package com.fptu.exe.skillswap.modules.blog.repository;

import com.fptu.exe.skillswap.modules.blog.domain.BlogMentorFollow;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface BlogMentorFollowRepository extends JpaRepository<BlogMentorFollow, UUID> {
    boolean existsByUserIdAndMentorUserId(UUID userId, UUID mentorUserId);
    long countByUserId(UUID userId);
    void deleteByUserIdAndMentorUserId(UUID userId, UUID mentorUserId);

    @EntityGraph(attributePaths = {"mentor", "mentor.user"})
    List<BlogMentorFollow> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("select f.mentorUserId from BlogMentorFollow f where f.userId = :userId")
    Set<UUID> findMentorIdsByUserId(@Param("userId") UUID userId);

    @Query("select distinct f.userId from BlogMentorFollow f where f.mentorUserId in :mentorIds")
    Set<UUID> findFollowerUserIdsByMentorIds(@Param("mentorIds") Collection<UUID> mentorIds);
}
