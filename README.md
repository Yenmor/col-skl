# CSg · 大学生成长 Skill 共创场

抖音黑客松参赛项目。社区 + Skill 仓库 + AI 对话三大模块，对话时由 LLM 从「学长.Skill」仓库里挑选若干位学长独立作答。

## 当前阶段

仅搭建主框架，所有回答/学长数据均为 mock。社区 + Skill 仓库 + 对话三大模块均可独立运行，跨模块接口已对齐。

## 三大模块

| 模块 | 入口 | 说明 |
| --- | --- | --- |
| 对话 | `/` | 顶部 tab，默认页。提问 → 后端挑 Top 3 学长.Skill → 并发得到 3 段独立回答 → 红书风三列卡片展示 |
| 社区 | `/community` | 公开帖瀑布流，发帖 / 互动入口预留，详情页 M3 接 |
| Skill 仓库 | `/seniors` | 学长.Skill 瀑布，按领域横切 + 按学校过滤；详情页打开 SKILL.md / 风格摘要 / 来源 |
| 我的 | `/me` | 框架页 |

## 学长.Skill 的形态

沿用 [colleague-skill](https://github.com/titanwings/colleague-skill) 蒸馏产物形态（7 件套）：

```
seniors/<id>/
  SKILL.md            # 能力 + 行为双轨道（prompt 注入内容）
  manifest.json       # 展示元数据（领域 / 标签 / 头像）
  meta.json           # 身份元数据（学校 / 专业 / 年级 / 出处）
  work.md             # 经验 / 能力原文
  persona.md          # 语气 / 行为原文
  work_skill.md       # 能力子 Skill
  persona_skill.md    # 行为子 Skill
```

仓库默认放在 `backend/data/seniors/`。上传：用户拖拽一个含七件套的 zip 到前端 → 后端解压 → 元数据入库 → 列表中可见。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 前端 | Vue 3 + Vite + TypeScript + Tailwind CSS + Pinia + Vue Router |
| 后端 | Spring Boot 3 + WebFlux + Maven |
| 持久 | SQLite（开发） / MySQL（生产） via Repository 抽象 |
| LLM | 默认 mock；预制接口可换 Anthropic Claude / OpenAI |

## 开发与构建（Windows）

项目按两个终端启动：前端 `5173`，后端 `8080`。首次运行先安装前端依赖；后端 Maven 使用 IntelliJ 自带版本。

### 1. 启动后端

在 PowerShell 终端执行：

```powershell
cd backend
$env:JAVA_HOME = 'C:\Users\17551\.jdks\ms-21.0.9'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:MAVEN_OPTS = '-Dfile.encoding=UTF-8'
$mvn = 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.5\plugins\maven\lib\maven3\bin\mvn.cmd'
& $mvn test
& $mvn spring-boot:run
```

看到 `Netty started on port 8080` 后，后端才算启动完成。数据库会自动创建在 `backend/data/skillhub.db`。

首次启动时，后端会**自动迁移社区示例数据**（173 条帖子 + 7460 条评论），详见下方「社区示例数据迁移」。

### 2. 社区示例数据迁移

社区帖子/评论是真实贴吧内容，已由 `backend/scripts/export-community.py` 导出为
`backend/src/main/resources/seed/community-posts.json`（约 3 MB，随代码入库，不提交 `skillhub.db` 本身）。

规则：

- **默认自动导入**：首次启动若数据库中没有非演示帖子，会从该 JSON 批量导入
  （`INSERT OR IGNORE` 按 id 幂等去重，重复启动不会重复插入）。
- **开关**：环境变量 `SKILLHUB_SEED_COMMUNITY=false` 可关闭自动导入；
  已导入过的库再次启动会跳过（不影响已有数据）。
- **与演示数据的关系**：`DemoDataSeeder` 硬编码的 12 条演示帖/20 条演示评论
  是独立的另一份数据（开关 `SKILLHUB_DEMO_DATA=false` 可关），与示例数据互不覆盖。
- **重新导出**：本地 `skillhub.db` 有更新时，运行
  `python backend/scripts/export-community.py` 重新生成 JSON 并提交即可
  （脚本只导出非演示数据，posts 排除 `demo-`/`peer-` 前缀，comments 排除 `demo-`/`reply-`/`peer-` 前缀）。

### 3. 启动前端

打开第二个 PowerShell 终端：

```powershell
chcp 65001 > $null
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = $OutputEncoding
cd frontend
npm install       # 仅首次运行需要
npm run dev -- --host 0.0.0.0
```

浏览器访问 <http://localhost:5173>。Vite 会把 `/api` 请求代理到 `http://localhost:8080`。

### 构建与测试

```powershell
# 前端：类型检查 + Vite 生产构建
cd frontend
npm run build

# 后端：编译并运行 JUnit 测试
cd ..\backend
$env:JAVA_HOME = 'C:\Users\17551\.jdks\ms-21.0.9'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.5\plugins\maven\lib\maven3\bin\mvn.cmd' test
```

前端没有单独的 lint 脚本；`npm run build` 中的 `vue-tsc -b` 是当前类型检查入口。后端当前只有 `src/test/` 下的 JUnit smoke test。

### 使用 IntelliJ IDEA 启动后端

- Project SDK 选择 `ms-21`（JDK 21），Language level 设为 17；不要使用默认 JDK 8 或 JDK 25。
- Maven Runner 的 JRE 也选择 `ms-21`，Maven home 可使用 Bundled Maven。
- 若 IDEA 控制台中文仍乱码，在 Run Configuration 的 VM options 加 `-Dfile.encoding=UTF-8`。
- `java.lang.ExceptionInInitializerError` / `com.sun.tools.javac.code.TypeTag :: UNKNOWN` 通常是 IDEA 使用 JDK 25 与当前 Lombok 版本不匹配；切回 JDK 21 后重新加载 Maven 项目并执行 `Build > Rebuild Project`。
