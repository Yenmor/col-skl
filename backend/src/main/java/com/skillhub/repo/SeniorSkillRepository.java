package com.skillhub.repo;

import com.skillhub.model.SeniorSkill;

import java.util.List;
import java.util.Optional;

public interface SeniorSkillRepository {
    SeniorSkill save(SeniorSkill s);
    Optional<SeniorSkill> findById(String id);
    List<SeniorSkill> list(String domain, String school);
    boolean existsById(String id);
}
