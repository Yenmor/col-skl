package com.skillhub.dto;

/** Five auditable trust dimensions, each scored from 0 to 100. */
public record SkillTrust(
        int campusCoverage,
        int sourceTraceability,
        int methodCompleteness,
        int boundaryCompleteness,
        int packageCompleteness,
        int overall
) {}
