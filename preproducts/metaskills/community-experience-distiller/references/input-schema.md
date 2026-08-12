# 输入 JSON 契约

## 目录

1. 最小结构
2. 字段说明
3. 上游兼容映射
4. 数据完整性要求
5. 授权与隐私

## 1. 最小结构

```json
{
  "export_version": "1.0",
  "exported_at": "2026-08-12T12:00:00+08:00",
  "target_user": {
    "id": "u_001",
    "display_name": "林学长",
    "profile": {"school": "示例大学", "college": "计算机学院", "major": "软件工程", "year_graduated": "2025"},
    "consent": {"distillation_allowed": true, "publication_allowed": false, "confirmed_at": null}
  },
  "posts": [
    {
      "id": "post_001",
      "title": "大二怎么选方向课",
      "body": "帖子正文",
      "author": {"id": "u_100", "display_name": "提问者"},
      "created_at": "2026-05-01T09:00:00+08:00",
      "tags": ["选课", "软件工程"],
      "comments": [
        {"id": "comment_001", "body": "目标用户的回答", "author": {"id": "u_001", "display_name": "林学长"}, "created_at": "2026-05-01T10:00:00+08:00", "reply_to_id": null, "replies": []}
      ]
    }
  ]
}
```

## 2. 字段说明

### target_user

| 字段 | 必需 | 说明 |
| --- | --- | --- |
| `id` | 是 | 平台内稳定用户 ID，不能只用昵称 |
| `display_name` | 建议 | 展示名，可在发布前匿名化 |
| `profile` | 否 | 学校、专业等本人声明的背景；不可由模型猜测 |
| `consent.distillation_allowed` | 是 | 是否允许将内容用于内部蒸馏 |
| `consent.publication_allowed` | 是 | 是否允许公开产物；默认 false |
| `consent.confirmed_at` | 否 | 本人确认时间 |

### post / comment / reply

| 字段 | 必需 | 说明 |
| --- | --- | --- |
| `id` | 是 | 全局稳定内容 ID |
| `body` | 是 | 原始正文，不要预先让模型改写 |
| `author.id` | 是 | 作者稳定 ID |
| `author.display_name` | 否 | 展示名 |
| `created_at` | 建议 | 用于判断经验时效和冲突演变 |
| `reply_to_id` | 回复建议 | 指向父消息；缺失时只能按嵌套结构推断 |
| `replies` | 否 | 嵌套回复 |
| `title`、`tags` | 帖子建议 | 用于保守的跨帖主题聚合 |

## 3. 上游兼容映射

脚本支持别名：`posts`/`related_posts`/`items`，`comments`/`replies`/`children`，`body`/`content`/`text`，`id`/`post_id`/`comment_id`/`message_id`，`author.id`/`author_id`/`user_id`，`created_at`/`timestamp`/`time`，`reply_to_id`/`parent_id`。

若使用别名，运行后检查 warnings，确认没有映射歧义。建议上游尽快统一为最小结构。

## 4. 数据完整性要求

- JSON 应包含目标用户的全部发言及这些发言所在的完整帖子，而不只是目标用户单句列表。
- 保留被回复消息和直接回复目标用户的消息，否则无法理解问答语境与后续纠正。
- 删除内容应保留 ID 和删除状态；编辑内容建议保留 `edited_at` 或版本。
- 同一个 ID 不得对应不同正文。
- 附件应提供可访问文本，不要只给不可读 URL。

## 5. 授权与隐私

- 私聊和非公开群消息默认不可用于公开 Skill。
- 上游应先脱敏手机号、邮箱、学号、住址、证件号和未授权姓名。
- 第三方发言保留匿名稳定 ID，避免归因给目标用户。
- `distillation_allowed=true` 不等于 `publication_allowed=true`。
- 本人未确认时，产物状态只能是 `draft`。
