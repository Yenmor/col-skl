# AGENTS.md

## Commands

Run each app from its own directory:

```powershell
# frontend
cd frontend
npm install
npm run dev                 # Vite: http://localhost:5173; /api proxies to :8080
npm run build               # vue-tsc -b && vite build
npm run preview

# backend (JDK 21; Maven is bundled with IntelliJ on this machine)
cd backend
$env:JAVA_HOME = 'C:\Users\17551\.jdks\ms-21.0.9'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:MAVEN_OPTS = '-Dfile.encoding=UTF-8'
$mvn = 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.5\plugins\maven\lib\maven3\bin\mvn.cmd'
& $mvn test
& $mvn spring-boot:run
```

- `backend/pom.xml` targets Java 17 bytecode, but the verified local toolchain and `CLAUDE.md` use JDK 21; do not use the default Java 8 or IDEA's JDK 25 project setting.
- Maven uses the Aliyun mirror from `C:\Users\17551\.m2\settings.xml`. There is no repo Maven wrapper.
- For Chinese output in PowerShell, run `chcp 65001` and set `$OutputEncoding`/`[Console]::OutputEncoding` to UTF-8 before Maven or Java commands.
- Frontend has no lint script or ESLint config; `npm run build` is the focused typecheck/build verification. Backend's only test is `mvn test` (JUnit smoke test).

## Structure

- `frontend/src/main.ts` mounts Vue + Pinia + Router; routes live in `frontend/src/router/index.ts`; views, stores, services, and shared interfaces are under `frontend/src/`.
- `backend/src/main/java/com/skillhub/SkillHubApplication.java` is the Spring Boot entrypoint; controllers expose the API, services own orchestration/skill ingestion, and `repo/sqlite/` is the persistence implementation.
- `frontend/src/style.css` contains all custom CSS and responsive overrides; `tailwind.config.js` maps utility colors to its CSS variables.

## Runtime constraints

- Backend runs on port 8080 and creates `backend/data/skillhub.db` at runtime. `InitRunner.@PostConstruct` must initialize the SQLite schema before `SeniorReader.scanOnBoot()` scans `backend/data/seniors/`.
- A valid senior Skill directory must contain all seven files: `SKILL.md`, `manifest.json`, `meta.json`, `work.md`, `persona.md`, `work_skill.md`, and `persona_skill.md`. `manifest.json` supplies display fields; `meta.json.identity` supplies school/college/major/year data.
- `ChatRequest` must remain a mutable Java class with setters; WebFlux request-body binding to a record causes 400 responses.
- LLM provider defaults to `mock`; `LLM_PROVIDER=deepseek` and the `DEEPSEEK_*` variables in `backend/.env.example` enable DeepSeek. `LLM_FALLBACK_TO_MOCK` controls failure fallback.
- Frontend `seniorService` and `communityService` fall back to local mock data when the API is unavailable; `chatService` does not.
- If IDEA reports `TypeTag.UNKNOWN` or `ExceptionInInitializerError`, its project/Maven JRE is likely JDK 25; use the configured `ms-21` JDK, reload Maven, and rebuild.

## API

- `POST /api/chat` body `{message, sessionId?}` → `{sessionId, answers[]}`; answers contain `seniorId, name, school, major, year, content`.
- `GET /api/seniors?domain=&school=` → `{items, facets}`; `GET /api/seniors/:id` → detail; `POST /api/seniors/upload` accepts multipart field `file`; `GET /api/seniors/:id/avatar?file=` serves SVG.
- `GET /api/community/posts?limit=20` → `{items, count}`; `POST /api/community/posts` accepts `title, body, authorName, authorAvatar`.

## UI gotchas

- Use the CSS tokens defined in `frontend/src/style.css` (`--paper`, `--surface`, `--ink`, `--pink`, etc.); do not introduce the old `--background`/`--primary` names.
- The mobile bottom navigation is 68px high, so `.composer` must use `bottom: 68px` below 720px; desktop navigation becomes a bottom-right pill.
