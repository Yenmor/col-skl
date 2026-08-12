package com.skillhub.dto;

/**
 * 异步任务状态枚举（v1 占位）。
 *
 * <p>当前 {@code /api/v1/seniors/:id/distill} 采用同步返回（D14），本枚举
 * 仅用于 v2 异步队列。
 *
 * <p>v2 路由候选：
 * <pre>
 *   POST /api/v2/seniors/:id/distill  → DistillJobDto(status="QUEUED")
 *   GET  /api/v2/seniors/:id/distill/:jobId  → DistillJobDto(status=...)
 * </pre>
 */
public enum JobStatus {
    QUEUED,
    RUNNING,
    DONE,
    FAILED
}
