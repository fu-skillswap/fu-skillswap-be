package com.fptu.exe.skillswap.modules.forum.repository;

import com.fptu.exe.skillswap.modules.forum.domain.ForumPost;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public interface ForumPostRepositoryCustom {

    List<ForumPost> findWindow(Specification<ForumPost> specification, int fetchLimit);
}
