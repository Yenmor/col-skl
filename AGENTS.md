# AGENTS.md

## Commands

Run each app from its own directory:

```powershell
# frontend
cd frontend
npm install
npm run dev                 # Vite: http://localhost:5173; /api proxies to :8080
npm run build               # vue-tsc -b && vite build  (this IS the typecheck+lint gate)

# backend (JDK 21; Maven is bundled with IntelliJ on this machine)
cd backend
$env:JAVA_HOME = 'C:\Users\17551\.jdks\ms-21.0.9'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:MAVEN_OPTS = '-Dfile.encoding=UTF-8'
$mvn = 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.5\plugins\maven\lib\maven3\bin\mvn.cmd'
& $mvn test
& $mvn spring-boot:run

# metaskill regression tests (distiller lives in preproducts/, not the backend)
cd preproducts\metaskills\community-experience-distiller
$env:PYTHONUTF8 = '1'
python scripts\test_metaskill.py
```

- `backend/pom.xml` targets Java 17 bytecode, but the verified toolchain is JDK 21; never use the default Java 8 or IDEA's JDK 25 project setting. If IDEA shows `TypeTag.UNKNOWN` / `ExceptionInInitializerError`, reload Maven with `ms-21`.
- Maven uses the Aliyun mirror from `C:\Users\17551\.m2\settings.xml`; there is no repo Maven wrapper.
- Frontend has no lint script/ESLint; `npm run build` is the only gate. Backend's only test is `mvn test` (JUnit smoke test).
- Restart loop: find the listener with `Get-NetTCPConnection -LocalPort 8080 -State Listen`, kill the owning PID, then `Start-Process $mvn -ArgumentList 'spring-boot:run' -WindowStyle Hidden` from `backend/`. Wait ~18s before curling.

## PowerShell encoding trap (costs real time if missed)

- Chinese text piped through the shell (here-strings to `python -`, `-replace` args, inline scripts) arrives as literal `??` — this corrupted DB rows and manifests before. Use the `edit`/`write` tools for any Chinese content. For batch replacements, use PowerShell char-code arithmetic (`[char]0x6797` etc.) with `[IO.File]::ReadAllText/WriteAllText` UTF-8.
- Mojibake in console output (e.g. `å±±å¤§`) is display-only; JSON payloads are actually fine.
- SQLite writes from Python while the backend holds the DB open are visible to the next backend query; no restart needed for data edits.

## Structure

- `frontend/src/main.ts` mounts Vue + Pinia + Router; routes in `frontend/src/router/index.ts`. `domain.ts` defines the four directions (学习/科研/竞赛/技能) — names, colors, aliases used for filtering/inference everywhere.
- Backend entrypoint `SkillHubApplication`; controllers expose APIs, `service/ChatOrchestrator` owns chat orchestration + prompt assembly, `repo/sqlite/` is the persistence layer.
- `preproducts/metaskills/community-experience-distiller/` is the Skill-distillation metaskill (own scripts + own test suite); it generates the seven-file bundles under `backend/data/seniors/<id>/`.
- `CLAUDE.md` exists but is stale (describes an older top-3/mock architecture); trust this file and the code over it.

## Chat / Skill runtime facts

- **Immersive top-1 chat**: `ChatOrchestrator` selects 1 senior (`limit(1)`), the frontend types it out into a single left bubble with the senior's name in the card header. There is no three-answer card mode anymore.
- **History storage quirk**: assistant rows are saved with `content=null` and the full answers in `answers_json`. `chatStore.loadHistory` must extract `answers[0].content` for assistant rows (and restore `activeSenior`) or every restored bubble renders empty.
- **LLM output contract**: `ChatOrchestrator.buildSystemPrompt` forbids Markdown in answers and mandates conversational style (short sentences, respond-then-ask, no 首先/其次). `schoolPhrase()` blanks 示例大学/未填写 so the model won't claim a fake school. `LLM_PROVIDER` defaults to `mock`; `LLM_FALLBACK_TO_MOCK` controls fallback.
- `SKILL.md` is read from disk per request (`SeniorReader.loadSkillMd`) — edits are live without restart. `manifest.json`/`meta.json` are scanned into SQLite only at boot (`scanOnBoot`) — **restart the backend after manifest/name changes**.
- A senior directory needs all seven files (`SKILL.md`, `manifest.json`, `meta.json`, `work.md`, `persona.md`, `work_skill.md`, `persona_skill.md`) or it is not ingested.
- `ChatRequest` must stay a mutable Java class (setters); WebFlux record binding → 400.

## Skill naming conventions (enforced in three places — keep them in sync)

- `name` = `{称呼} · {简短擅长领域}`, e.g. `陈学姐 · 保研`; domain must be from the controlled vocab 学习/保研/科研/竞赛/技能/求职/实习/选课; `description` is one human-voice sentence; `triggers` are 5-9 short (2-4 char) spoken words — they match by substring (`contains`) with +6 recall weight, so long phrases never fire.
- Enforcement points: `UserDistillController.normalizeDomain()`, `preproducts/.../references/distillation-schema.md`, and the `write_skill_bundle.py` template. When renaming a senior, update its manifest + meta + all five md titles + `frontend/src/services/seniorService.ts` and `seniorServiceMock.ts` fallback lists.
- Generated SKILL.md must keep the headings `## 触发条件 / 运行契约 / 执行流程 / 决策节点 / 能力边界` and `frag_[a-f0-9]{10}` evidence IDs, or `validate_skill_bundle.py` rejects the bundle.

## API

- `POST /api/chat` `{message, sessionId?, excludeSeniorId?}` → `{sessionId, answers[]}`; answers: `seniorId, name, school, major, year, content`.
- `GET /api/seniors?domain=&school=` → `{items, facets}`; `GET /api/seniors/:id`; `POST /api/seniors/upload` (multipart `file`); `GET /api/seniors/:id/avatar?file=`.
- **Community real API is v1**: `GET /api/v1/posts` (accepts comma-separated `domain` multi-value, e.g. `学习,保研,选课,生活,教育`), `POST /api/v1/posts`, `/api/v1/posts/:id/comments`, `/api/v1/posts/:id/like`. The old `/api/community/posts` reads the empty deprecated `community_posts` table — do not use it.
- DB has two post tables: `posts`/`post_comments` (real data) and `community_posts` (deprecated, empty). `CommunityView.vue` uses `postsApi` from `services/api-v1.ts` directly; `communityService.ts` and `communityStore.ts` are dead code.

## UI gotchas (style.css owns all custom CSS)

- Use the tokens defined in `:root` (`--surface`, `--ink`, `--ink-soft`, `--line`, `--focus`, `--study/--research/--competition/--skills` domain colors, etc.); `--paper`/`--background`/`--primary` do not exist. `tailwind.config.js` maps utilities onto these.
- The home chat is one fixed `.chat-session` card that expands in place on first send (680px hero → 880px dialog). The composer inside it is always `compact`; there is no fixed full-width composer anymore.
- **CSS cascade traps that have caused real bugs**:
  - `.composer-compact` must reset the base `.composer` fixed inset (`right:auto; bottom:auto; left:auto`) — `position:relative` alone still leaves `bottom:66px` shifting it.
  - Legacy un-scoped rules inside media blocks win over your earlier same-specificity overrides by source order: `@media (min-width:1024px) .composer:not(.composer-compact) { left: var(--rail-width) }` and 820px-block `.composer-context` rules. If something is misaligned at only some widths, grep style.css for unscoped `.composer*` selectors inside `@media` blocks.
  - Entry/active card positions hardcode half-heights (`top: calc(50% - 75px)` desktop, `-78px` mobile). If the hero card's content height changes, these must change too.
  - The skill-picker menu must stay outside `.composer-inner` (absolutely positioned against the form) — inside it gets clipped by `overflow:hidden` in the hero state.
- Bottom nav is 66px tall (not 68); the `bottom: 66px` composer rule only matters for non-compact composers.

## Visual verification (required for UI changes)

- Drive the open dev-server browser with `npx --yes --package @playwright/cli playwright-cli` (`resize`, `eval`, `screenshot` → `.playwright-cli/`). The `navigate` subcommand is unreliable — use `eval "location.href='...'"` instead.
- **Never trust screenshot vision alone**: the vision tool (`zhipu-vision_analyze_image`) reports "no problems" on broken layouts. Always assert geometry via `getBoundingClientRect` (card vs composer left/right/bottom, footer width inside inner, center offset) across a width matrix — 390/620/820/1024/1366/1440/1920 — for both entry and active chat states, then review screenshots.
- Backend behavior checks are plain `Invoke-RestMethod` against `http://localhost:8080` after each backend restart.
