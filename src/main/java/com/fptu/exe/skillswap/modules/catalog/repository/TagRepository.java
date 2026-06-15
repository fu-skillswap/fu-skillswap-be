package com.fptu.exe.skillswap.modules.catalog.repository;

import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findByIdInAndStatus(Collection<UUID> ids, TagStatus status);

    List<Tag> findByTypeAndStatusOrderByWeightDescNameViAsc(com.fptu.exe.skillswap.modules.catalog.domain.TagType type,
                                                           TagStatus status);

    Optional<Tag> findByCode(String code);
}
