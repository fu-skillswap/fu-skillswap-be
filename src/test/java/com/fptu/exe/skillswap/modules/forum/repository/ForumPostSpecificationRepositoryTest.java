package com.fptu.exe.skillswap.modules.forum.repository;

import com.fptu.exe.skillswap.modules.catalog.domain.Tag;
import com.fptu.exe.skillswap.modules.catalog.domain.TagStatus;
import com.fptu.exe.skillswap.modules.catalog.domain.TagType;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPost;
import com.fptu.exe.skillswap.modules.forum.domain.ForumPostStatus;
import com.fptu.exe.skillswap.modules.identity.domain.User;
import com.fptu.exe.skillswap.modules.identity.domain.UserStatus;
import com.fptu.exe.skillswap.shared.constant.RoleCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class ForumPostSpecificationRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ForumPostRepository forumPostRepository;

    @Test
    void hasStatusHasHelpTopicHasAuthorHasKeyword_shouldFilterCorrectly() {
        User author = persistUser("author-filter@test.com", "Mentor Reviewer");
        User anotherAuthor = persistUser("another-filter@test.com", "Another Writer");
        Tag helpTopic = persistHelpTopic("FILTER_TOPIC_1", "Góp ý dự án/case study");
        Tag otherTopic = persistHelpTopic("FILTER_TOPIC_2", "Giải đáp thắc mắc");

        ForumPost matched = persistPost(uuidV7(1_700_000_000_100L, 0x101, 0x111L), author, helpTopic,
                "Need project feedback", "Please mentor review this project", LocalDateTime.of(2026, 7, 8, 10, 0));
        persistPost(uuidV7(1_700_000_000_200L, 0x102, 0x222L), author, otherTopic,
                "Need project feedback", "Wrong topic", LocalDateTime.of(2026, 7, 8, 10, 0));
        persistPost(uuidV7(1_700_000_000_300L, 0x103, 0x333L), anotherAuthor, helpTopic,
                "Need project feedback", "Wrong author", LocalDateTime.of(2026, 7, 8, 10, 0));
        persistPost(uuidV7(1_700_000_000_400L, 0x104, 0x444L), author, helpTopic,
                "Different title", "Different content", LocalDateTime.of(2026, 7, 8, 10, 0));

        entityManager.flush();

        Specification<ForumPost> specification = Specification.where(ForumPostSpecification.hasStatus(ForumPostStatus.PUBLISHED))
                .and(ForumPostSpecification.hasHelpTopic(helpTopic.getId()))
                .and(ForumPostSpecification.hasAuthor(author.getId()))
                .and(ForumPostSpecification.hasKeyword("%project%"));

        List<ForumPost> result = forumPostRepository.findWindow(specification, 10);

        assertEquals(1, result.size());
        assertEquals(matched.getId(), result.getFirst().getId());
    }

    @Test
    void mineOnly_shouldFilterByCurrentUser() {
        User author = persistUser("mine-author@test.com", "Mine Author");
        User otherAuthor = persistUser("mine-other@test.com", "Other Author");
        Tag helpTopic = persistHelpTopic("FILTER_TOPIC_3", "Định hướng ngành/chuyên ngành");

        ForumPost minePost = persistPost(uuidV7(1_700_000_001_000L, 0x201, 0x111L), author, helpTopic,
                "Mine post", "Mine content", LocalDateTime.of(2026, 7, 8, 10, 0));
        persistPost(uuidV7(1_700_000_001_100L, 0x202, 0x222L), otherAuthor, helpTopic,
                "Other post", "Other content", LocalDateTime.of(2026, 7, 8, 10, 0));

        entityManager.flush();

        List<ForumPost> result = forumPostRepository.findWindow(
                Specification.where(ForumPostSpecification.hasStatus(ForumPostStatus.PUBLISHED))
                        .and(ForumPostSpecification.mineOnly(author.getId())),
                10
        );

        assertEquals(1, result.size());
        assertEquals(minePost.getId(), result.getFirst().getId());
    }

    @Test
    void isBeforeCursor_shouldApplyTupleComparison() {
        User author = persistUser("cursor-author@test.com", "Cursor Author");
        Tag helpTopic = persistHelpTopic("FILTER_TOPIC_4", "Thích nghi FPTU & campus life");

        UUID cursorId = UUID.fromString("00000000-0000-7000-8000-000000000003");
        UUID smallerSameTimeId = UUID.fromString("00000000-0000-7000-8000-000000000002");
        UUID largerSameTimeId = UUID.fromString("00000000-0000-7000-8000-000000000004");

        ForumPost earlier = persistPost(UUID.fromString("00000000-0000-7000-8000-000000000001"), author, helpTopic,
                "Earlier", "Earlier content", LocalDateTime.of(2026, 7, 8, 9, 0));
        ForumPost sameTimeSmaller = persistPost(smallerSameTimeId, author, helpTopic,
                "Same time smaller", "Same time smaller content", LocalDateTime.of(2026, 7, 8, 10, 0));
        persistPost(largerSameTimeId, author, helpTopic,
                "Same time larger", "Same time larger content", LocalDateTime.of(2026, 7, 8, 10, 0));

        entityManager.flush();

        List<ForumPost> result = forumPostRepository.findWindow(
                Specification.where(ForumPostSpecification.hasStatus(ForumPostStatus.PUBLISHED))
                        .and(ForumPostSpecification.isBeforeCursor(LocalDateTime.of(2026, 7, 8, 10, 0), cursorId)),
                10
        );

        assertEquals(1, result.size());
        assertEquals(earlier.getId(), result.getFirst().getId());
        assertTrue(result.stream().noneMatch(post -> post.getId().equals(largerSameTimeId)));
    }

    private User persistUser(String email, String fullName) {
        User user = User.builder()
                .email(email)
                .fullName(fullName)
                .status(UserStatus.ACTIVE)
                .roles(Set.of(RoleCode.MENTEE))
                .build();
        return entityManager.persist(user);
    }

    private Tag persistHelpTopic(String code, String nameVi) {
        Tag tag = Tag.builder()
                .code(code)
                .nameVi(nameVi)
                .type(TagType.HELP_TOPIC)
                .status(TagStatus.ACTIVE)
                .build();
        return entityManager.persist(tag);
    }

    private ForumPost persistPost(UUID id, User author, Tag helpTopic, String title, String content, LocalDateTime lastActivityAt) {
        ForumPost post = ForumPost.builder()
                .id(id)
                .authorUser(author)
                .helpTopic(helpTopic)
                .title(title)
                .content(content)
                .status(ForumPostStatus.PUBLISHED)
                .lastActivityAt(lastActivityAt)
                .build();
        return entityManager.merge(post);
    }

    private static UUID uuidV7(long epochMillis, int randA, long randB) {
        long msb = (epochMillis << 16) | (7L << 12) | (randA & 0xFFFL);
        long lsb = (2L << 62) | (randB & 0x3FFFFFFFFFFFFFFFL);
        return new UUID(msb, lsb);
    }
}
