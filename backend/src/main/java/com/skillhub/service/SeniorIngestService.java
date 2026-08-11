package com.skillhub.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 上传 zip 解压到 seniors-dir/<id>/ 然后触发索引落库。
 * 七件套校验先不做（开发期，全部 master 手动上传）。
 */
@Service
public class SeniorIngestService {

    private final SeniorReader reader;

    public SeniorIngestService(SeniorReader reader) {
        this.reader = reader;
    }

    public String upload(MultipartFile zip) {
        if (zip == null || zip.isEmpty()) {
            throw new IllegalArgumentException("zip 文件为空");
        }
        Path seniors = reader.seniorsDir();
        Path target = null;
        try (ZipInputStream zis = new ZipInputStream(zip.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                // zip 顶层目录假定为 <id>/
                int slash = name.indexOf('/');
                if (slash <= 0) continue;
                String id = name.substring(0, slash);
                String rest = name.substring(slash + 1);
                if (target == null) target = seniors.resolve(id);
                Path dest = target.resolve(rest);
                if (dest.startsWith(seniors.resolve(id))) {
                    Files.createDirectories(dest.getParent());
                    Files.copy(zis, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("解压失败", e);
        }
        if (target == null) {
            throw new IllegalArgumentException("zip 内容为空或格式异常");
        }
        return target.getFileName().toString();
    }
}
