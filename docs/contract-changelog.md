# Contract Changelog

> 记录 `docs/api-v1.md` / `docs/error-codes.md` 的字段/接口变化。
> AI 合并冲突时，**此文件优先级仅次于 `api-v1.md`**。

---

## 格式

```text
## [YYYY-MM-DD] <类型> <接口/字段>

- 旧：<旧值>
- 新：<新值>
- 原因：<一句话>
- 影响：<受影响 PR / 模块>
```

类型：
- `ADD` — 新增字段 / 接口
- `CHANGE` — 修改字段语义
- `DEPRECATE` — 标记弃用
- `REMOVE` — 删除字段 / 接口（仅 v1 → v2 升级时用，v1 内不允许）

---

## 2026-08-12 — Sprint 0 初稿

- `ADD` — 全部 7+1 v1 资源契约（参见 `api-v1.md` §1-9）
- `ADD` — `ErrorCode` 公共枚举（参见 `error-codes.md`）
- `ADD` — 12 + 6 个决策（参见 `decisions.md`）
- 暂无变更
