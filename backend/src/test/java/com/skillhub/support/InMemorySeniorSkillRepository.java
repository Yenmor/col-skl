package com.skillhub.support;

import com.skillhub.model.SeniorSkill;
import com.skillhub.repo.SeniorSkillRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemorySeniorSkillRepository implements SeniorSkillRepository {
    private final Map<String, SeniorSkill> values = new LinkedHashMap<>();

    @Override public SeniorSkill save(SeniorSkill skill) { values.put(skill.id(), skill); return skill; }
    @Override public Optional<SeniorSkill> findById(String id) { return Optional.ofNullable(values.get(id)); }
    @Override public Optional<SeniorSkill> findAccessibleById(String id, String userId) {
        return findById(id).filter(skill -> skill.isPublic() || skill.isOwnedBy(userId));
    }
    @Override public List<SeniorSkill> list(String domain, String school) {
        return filter(values.values().stream().toList(), domain, school, null, false);
    }
    @Override public List<SeniorSkill> listPublic(String domain, String school, String query) {
        return filter(values.values().stream().filter(SeniorSkill::isPublic).toList(), domain, school, query, true);
    }
    @Override public List<SeniorSkill> listOwned(String ownerId) {
        return values.values().stream().filter(skill -> skill.isOwnedBy(ownerId)).toList();
    }
    @Override public boolean existsById(String id) { return values.containsKey(id); }
    @Override public List<String> allIds() { return new ArrayList<>(values.keySet()); }
    @Override public void deleteById(String id) { values.remove(id); }

    public void clear() { values.clear(); }

    private List<SeniorSkill> filter(List<SeniorSkill> input, String domain, String school,
                                     String query, boolean publicOnly) {
        return input.stream()
            .filter(skill -> domain == null || domain.isBlank() || domain.equals(skill.domain()))
            .filter(skill -> school == null || school.isBlank() || school.equals(skill.school()))
            .filter(skill -> query == null || query.isBlank()
                || safe(skill.name()).contains(query) || safe(skill.summary()).contains(query))
            .toList();
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
