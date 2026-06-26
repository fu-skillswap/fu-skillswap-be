package com.fptu.exe.skillswap.modules.forum.repository;

import com.fptu.exe.skillswap.modules.forum.domain.ForumPostReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ForumPostReactionRepository extends JpaRepository<ForumPostReaction, UUID> {

    Optional<ForumPostReaction> findByPostIdAndUserId(UUID postId, UUID userId);
}
