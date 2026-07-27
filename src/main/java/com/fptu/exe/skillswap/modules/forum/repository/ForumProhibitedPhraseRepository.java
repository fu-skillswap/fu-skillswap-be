package com.fptu.exe.skillswap.modules.forum.repository;

import com.fptu.exe.skillswap.modules.forum.domain.ForumProhibitedPhrase;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ForumProhibitedPhraseRepository extends JpaRepository<ForumProhibitedPhrase, UUID> {

    @EntityGraph(attributePaths = "createdByUser")
    Optional<ForumProhibitedPhrase> findById(UUID id);

    boolean existsByNormalizedPhrase(String normalizedPhrase);

    List<ForumProhibitedPhrase> findAllByActiveTrueOrderByCreatedAtAscIdAsc();

    @Query("""
            select r from ForumProhibitedPhrase r
            join fetch r.createdByUser
            where (:isActive is null or r.active = :isActive)
              and (
                    :cursorCreatedAt is null
                    or r.createdAt < :cursorCreatedAt
                    or (r.createdAt = :cursorCreatedAt and r.id < :cursorId)
              )
            order by r.createdAt desc, r.id desc
            """)
    List<ForumProhibitedPhrase> findWindow(
            @Param("isActive") Boolean isActive,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable
    );
}
