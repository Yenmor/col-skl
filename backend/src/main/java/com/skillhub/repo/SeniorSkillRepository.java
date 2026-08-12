package com.skillhub.repo;

import com.skillhub.model.SeniorSkill;

import java.util.List;
import java.util.Optional;

public interface SeniorSkillRepository {
    SeniorSkill save(SeniorSkill s);
    Optional<SeniorSkill> findById(String id);
    List<SeniorSkill> list(String domain, String school);
    boolean existsById(String id);
    /** 返回 DB 中全部 id（用于扫描后清理已删除目录的孤儿记录）。 */
    List<String> allIds();
    void deleteById(String id);
}
