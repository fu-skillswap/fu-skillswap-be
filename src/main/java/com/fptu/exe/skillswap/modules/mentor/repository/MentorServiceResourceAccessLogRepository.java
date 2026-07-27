package com.fptu.exe.skillswap.modules.mentor.repository;
import com.fptu.exe.skillswap.modules.mentor.domain.MentorServiceResourceAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface MentorServiceResourceAccessLogRepository extends JpaRepository<MentorServiceResourceAccessLog, UUID> {}
