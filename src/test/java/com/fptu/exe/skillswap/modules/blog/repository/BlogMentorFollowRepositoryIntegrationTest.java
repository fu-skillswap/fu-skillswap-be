package com.fptu.exe.skillswap.modules.blog.repository;

import com.fptu.exe.skillswap.modules.blog.domain.BlogMentorFollow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BlogMentorFollowRepositoryIntegrationTest {

    @Autowired
    private BlogMentorFollowRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM blog_mentor_follows");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void findByUserIdOrderByCreatedAtDesc_executesCleanlyWithoutInvalidEntityGraph() {
        UUID userId = UUID.randomUUID();
        UUID mentorUserId = UUID.randomUUID();

        repository.save(BlogMentorFollow.builder()
                .userId(userId)
                .mentorUserId(mentorUserId)
                .build());

        List<BlogMentorFollow> follows = repository.findByUserIdOrderByCreatedAtDesc(userId);
        assertThat(follows).hasSize(1);
        assertThat(follows.get(0).getMentorUserId()).isEqualTo(mentorUserId);
    }
}
