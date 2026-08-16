package com.fptu.exe.skillswap.modules.forum.service;

import com.fptu.exe.skillswap.modules.forum.domain.ForumActionType;
import com.fptu.exe.skillswap.modules.forum.strategy.CommentCreateActionPolicy;
import com.fptu.exe.skillswap.modules.forum.strategy.ForumActionPolicy;
import com.fptu.exe.skillswap.modules.forum.strategy.ForumActionPolicyRegistry;
import com.fptu.exe.skillswap.modules.forum.strategy.PostCreateActionPolicy;
import com.fptu.exe.skillswap.modules.forum.strategy.ReactionToggleActionPolicy;
import com.fptu.exe.skillswap.modules.forum.strategy.ReportCreateActionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ForumActionPolicyRegistryTest {

    private ForumActionPolicyRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ForumActionPolicyRegistry(List.of(
                new PostCreateActionPolicy(),
                new CommentCreateActionPolicy(),
                new ReportCreateActionPolicy(),
                new ReactionToggleActionPolicy()
        ));
    }

    @Test
    void getPolicy_postCreate() {
        ForumActionPolicy policy = registry.getPolicy(ForumActionType.CREATE_POST);
        assertNotNull(policy);
        assertEquals(5, policy.getLimit());
        assertEquals(Duration.ofMinutes(10), policy.getWindow());
    }

    @Test
    void getPolicy_commentCreate() {
        ForumActionPolicy policy = registry.getPolicy(ForumActionType.CREATE_COMMENT);
        assertNotNull(policy);
        assertEquals(20, policy.getLimit());
        assertEquals(Duration.ofMinutes(10), policy.getWindow());
    }

    @Test
    void getPolicy_reportCreate() {
        ForumActionPolicy policy = registry.getPolicy(ForumActionType.CREATE_REPORT);
        assertNotNull(policy);
        assertEquals(10, policy.getLimit());
        assertEquals(Duration.ofMinutes(30), policy.getWindow());
    }

    @Test
    void getPolicy_reactionToggle() {
        ForumActionPolicy policy = registry.getPolicy(ForumActionType.TOGGLE_REACTION);
        assertNotNull(policy);
        assertEquals(60, policy.getLimit());
        assertEquals(Duration.ofMinutes(10), policy.getWindow());
    }
}
