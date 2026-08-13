# SkillsLab 后端服务部署

让 `skillslab-expert-panel` 在生产地址可用：把当前 Spring Boot 后端构建为 jar，部署到
`http://1.14.192.254:8131`。生产环境目前由反向代理（Nginx 等）在 8131 端口承接外部流量，
代理后端指向本机 Spring Boot 应用端口（默认 8080，可调整）。

## 构建

在 Windows 开发机（或服务器）执行：

```powershell
cd backend
$env:JAVA_HOME = 'C:\Users\17551\.jdks\ms-21.0.9'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:MAVEN_OPTS = '-Dfile.encoding=UTF-8'
$mvn = 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.5\plugins\maven\lib\maven3\bin\mvn.cmd'
& $mvn clean package
```

产物：`backend/target/backend-*.jar`（或 `mvn package -DskipTests` 跳过测试快速打包）。

## 上传与启动（Linux 服务器）

```bash
scp backend/target/backend-*.jar root@1.14.192.254:/opt/skillslab/
```

服务器上首次部署：

```bash
mkdir -p /opt/skillslab/data
cd /opt/skillslab
nohup java -jar backend-*.jar --server.port=8080 > app.log 2>&1 &
```

- `--server.port`：Spring Boot 监听端口。若代理已把 8131 转发到 8080，保持 8080；
  也可以直接 `--server.port=8131` 让应用监听 8131（代理配置需同步调整）。
- 应用依赖 `data/` 目录（相对工作目录 `user.dir`）：`data/skillhub.db` 自动创建，
  `data/seniors/` 需随部署带上（学长七件套）。
- **首次启动会自动迁移社区示例数据**（173 帖 + 7460 评论，来自 jar 内的
  `seed/community-posts.json`）；如需关闭：`SKILLHUB_SEED_COMMUNITY=false`。
- 演示数据（12 帖 + 20 评论）默认开启，可 `SKILLHUB_DEMO_DATA=false` 关闭。

## 环境变量

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `SKILLHUB_SEED_COMMUNITY` | `true` | 首次启动导入社区示例数据 |
| `SKILLHUB_DEMO_DATA` | `true` | 演示帖/评论 |
| `LLM_PROVIDER` | `mock` | `deepseek` 启用真实回答 |
| `DEEPSEEK_API_KEY` | 空 | deepseek 密钥 |
| `LLM_FALLBACK_TO_MOCK` | `false` | 真实 LLM 失败时回退 mock |
| `SERVER_PORT` | `8080` | 应用监听端口 |

生产若要真实专家回答，务必设置 `LLM_PROVIDER=deepseek` 与 `DEEPSEEK_API_KEY`；
否则专家回答是本地示例占位（会明确标注"本地示例回答"）。

## 反向代理示例（Nginx，8131 → 8080）

```nginx
server {
    listen 8131;
    client_max_body_size 30m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## 健康检查

```bash
curl -s http://127.0.0.1:8080/api/v1/posts?limit=1
```

返回帖子 JSON 即正常。外部验证：`curl -s http://1.14.192.254:8131/api/v1/posts?limit=1`。

## 目录布局（服务器）

```
/opt/skillslab/
├── backend-*.jar
├── app.log
└── data/
    ├── skillhub.db        # 首次启动自动创建并导入示例数据
    └── seniors/           # 学长七件套（与仓库 backend/data/seniors 同步）
```

## 注意事项

- 生产目前未部署（返回 502 即代理在但后端未启动）；按本指引部署后恢复。
- 帖子/评论数据在 `skillhub.db`，不随 git 走；示例数据由 jar 内 JSON 自动导入，
  正式运营数据请直接备份 `data/skillhub.db`。
- 升级后端：停进程 → 替换 jar → 重新启动（数据保留，导入短路不重复）。
