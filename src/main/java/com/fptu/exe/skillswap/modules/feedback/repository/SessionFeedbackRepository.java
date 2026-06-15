package com.fptu.exe.skillswap.modules.feedback.repository;

import com.fptu.exe.skillswap.modules.feedback.domain.SessionFeedback;
import com.fptu.exe.skillswap.modules.feedback.repository.query.MentorReviewQueryRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SessionFeedbackRepository extends JpaRepository<SessionFeedback, UUID> {

    @Query(value = """
            select new com.fptu.exe.skillswap.modules.feedback.repository.query.MentorReviewQueryRow(
                sf.id,
                reviewer.id,
                reviewer.fullName,
                reviewer.avatarUrl,
                sf.rating,
                sf.comment,
                sf.createdAt
            )
            from SessionFeedback sf
            join sf.reviewer reviewer
            where sf.reviewee.id = :mentorUserId
              and sf.isPublic = true
            """,
            countQuery = """
            select count(sf.id)
            from SessionFeedback sf
            where sf.reviewee.id = :mentorUserId
              and sf.isPublic = true
            """)
    Page<MentorReviewQueryRow> findPublicMentorReviews(@Param("mentorUserId") UUID mentorUserId, Pageable pageable);
}
