package com.fptu.exe.skillswap.modules.forum.repository;

import com.fptu.exe.skillswap.modules.forum.domain.ForumTopic;
import com.fptu.exe.skillswap.modules.forum.domain.ForumTopicCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ForumTopicRepository extends JpaRepository<ForumTopic, UUID> {

    List<ForumTopic> findByActiveTrueOrderByDisplayOrderAscCodeAsc();

    java.util.Optional<ForumTopic> findByCodeAndActiveTrue(ForumTopicCode code);
}
