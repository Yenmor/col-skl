package com.skillhub.repo;

import com.skillhub.model.CommunityPost;

import java.util.List;
import java.util.Optional;

public interface CommunityRepository {
    CommunityPost save(CommunityPost p);
    Optional<CommunityPost> findById(String id);
    List<CommunityPost> recent(int limit);
}
