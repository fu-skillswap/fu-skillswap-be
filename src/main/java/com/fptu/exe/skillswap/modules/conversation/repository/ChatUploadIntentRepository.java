package com.fptu.exe.skillswap.modules.conversation.repository;
import com.fptu.exe.skillswap.modules.conversation.domain.ChatUploadIntent;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface ChatUploadIntentRepository extends JpaRepository<ChatUploadIntent, UUID> {
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select i from ChatUploadIntent i where i.id=:id") Optional<ChatUploadIntent> findByIdForUpdate(@Param("id") UUID id);
}
