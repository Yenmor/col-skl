package com.skillhub.dto;

import java.util.List;
import java.util.Set;

public record SkillListResponse(List<SkillSummary> items, Facets facets) {
    public record Facets(Set<String> domains, Set<String> schools) {}
}
