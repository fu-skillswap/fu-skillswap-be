package com.fptu.exe.skillswap.modules.admin.repository;

import com.fptu.exe.skillswap.modules.admin.domain.AdminNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AdminNoteRepository extends JpaRepository<AdminNote, UUID> {

    @EntityGraph(attributePaths = {"adminUser"})
    @Query(value = """
            select note
            from AdminNote note
            where (:targetType is null or lower(note.targetType) = lower(:targetType))
              and (:targetId is null or note.targetId = :targetId)
            """, countQuery = """
            select count(note.id)
            from AdminNote note
            where (:targetType is null or lower(note.targetType) = lower(:targetType))
              and (:targetId is null or note.targetId = :targetId)
            """)
    Page<AdminNote> searchForAdmin(
            @Param("targetType") String targetType,
            @Param("targetId") UUID targetId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"adminUser"})
    List<AdminNote> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, UUID targetId);
}
