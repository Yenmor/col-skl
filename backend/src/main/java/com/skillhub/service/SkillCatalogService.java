package com.skillhub.service;

import com.skillhub.config.ApiException;
import com.skillhub.dto.ErrorCode;
import com.skillhub.dto.SkillDetailResponse;
import com.skillhub.dto.SkillListResponse;
import com.skillhub.dto.SkillSummary;
import com.skillhub.model.SeniorSkill;
import com.skillhub.repo.SeniorSkillRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class SkillCatalogService {
    private final SeniorSkillRepository repo;
    private final SeniorReader reader;
    private final SkillTrustEvidenceService trustEvidence;

    @Autowired
    public SkillCatalogService(SeniorSkillRepository repo, SeniorReader reader,
                               SkillTrustEvidenceService trustEvidence) {
        this.repo = repo;
        this.reader = reader;
        this.trustEvidence = trustEvidence;
    }

    /** Source-compatible constructor for focused unit tests without a database. */
    public SkillCatalogService(SeniorSkillRepository repo, SeniorReader reader) {
        this(repo, reader, null);
    }

    public SkillListResponse publicList(String domain, String school, String query) {
        List<SkillSummary> items = repo.listPublic(clean(domain), clean(school), clean(query)).stream()
            .map(this::summary)
            .toList();
        Set<String> domains = new LinkedHashSet<>();
        Set<String> schools = new LinkedHashSet<>();
        for (SkillSummary item : items) {
            if (item.domain() != null && !item.domain().isBlank()) domains.add(item.domain());
            if (item.school() != null && !item.school().isBlank()) schools.add(item.school());
        }
        return new SkillListResponse(items, new SkillListResponse.Facets(domains, schools));
    }

    public SkillListResponse owned(String userId) {
        List<SkillSummary> items = repo.listOwned(userId).stream().map(this::summary).toList();
        return new SkillListResponse(items, new SkillListResponse.Facets(Set.of(), Set.of()));
    }

    public SkillDetailResponse detail(String id, String userId) {
        accessible(id, userId);
        SkillDetailResponse detail = reader.loadV1Detail(id, userId);
        return trustEvidence == null ? detail : detail.withTrustEvidence(trustEvidence.evidenceFor(id));
    }

    public byte[] bundle(String id, String userId) {
        accessible(id, userId);
        Path dir = reader.checkedSkillDir(id);
        if (!Files.isDirectory(dir)) throw new ApiException(ErrorCode.SKILL_NOT_FOUND, "Skill 文件不存在");
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes)) {
            try (var files = Files.walk(dir)) {
                for (Path path : files.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString)).toList()) {
                    Path normalized = path.toAbsolutePath().normalize();
                    if (!normalized.startsWith(dir.toAbsolutePath().normalize())) continue;
                    String entryName = id + "/" + dir.relativize(path).toString().replace('\\', '/');
                    zip.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, zip);
                    zip.closeEntry();
                }
            }
            zip.finish();
            byte[] archive = bytes.toByteArray();
            if (trustEvidence != null) trustEvidence.recordDownload(id, userId);
            return archive;
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.GENERAL_INTERNAL, "无法创建 Skill zip");
        }
    }

    public SkillSummary summary(SeniorSkill skill) {
        return SkillSummary.from(skill, reader.trustFor(skill.id()));
    }

    private SeniorSkill accessible(String id, String userId) {
        SeniorSkill existing = repo.findById(id).orElseThrow(() ->
            new ApiException(ErrorCode.SKILL_NOT_FOUND, "Skill 不存在: " + id));
        if (!existing.isPublic() && !existing.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.SKILL_FORBIDDEN, "无权访问该私有 Skill");
        }
        return existing;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
