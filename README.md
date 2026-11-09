# AIDailyReport

将本地 Git 提交转为结构化工作项，并经 OpenAI 兼容 API 生成日报 / 周报 / 月报，支持 Excel 导出。

本分支提供 **Web 桌面化** 体验：Spring Boot 提供 REST API 并内嵌 Vue 3 前端，可打包为 Windows 可执行程序。

## 架构概览

```
Git 项目（DB 配置） → GitCollectService → AiParseService → work_item
                                              ↓
                                    ReportGenerateService
                                              ↓
                         daily_report / period_report + Excel/TXT 导出

Web UI (Vue 3 + Vite)  ←→  REST `/api/*`  ←→  MyBatis → MySQL
```

- **核心表**（不可变更结构）：`git_commit_log`、`work_item`、`daily_report`、`period_report`（见 `src/main/resources/schema.sql`）
- **Web 配置表**（新增）：`app_git_project`、`app_ai_provider`（多提供商）、`app_scheduler_config`、`app_setting`（见 `schema_web.sql`）；旧 `app_ai_config` 仅用于启动时迁移
- **CLI**：仍保留，默认关闭；设置 `app.cli.enabled=true` 可启用交互菜单

## 环境要求

| 组件 | 版本 |
|------|------|
| JDK | 17+ |
| Maven | 3.8+ |
| MySQL | 5.7+ / 8.x |
| Node.js | 18+（仅在前端开发或重构建 UI 时需要） |

## 快速开始（Web 模式）

1. **MySQL**：创建库 `dailyreport`，或通过 JDBC URL 自动创建。
2. **数据库连接**：编辑 `src/main/resources/application.yml` 中的 `spring.datasource.*`，或使用环境变量覆盖密码（勿将真实密码提交到 Git）。
3. **可选 YAML 引导**：复制 `config.example.yaml` 为 `config.yaml`（已在 `.gitignore`），填入示例仓库与 API Key 占位符；首次启动会从 YAML **导入到数据库**（仅当项目表为空时）。
4. **构建并运行**：

```bash
mvn -q -DskipTests package
java -jar target/dailyreport-1.0.0.jar
```

5. 浏览器访问 [http://localhost:18080](http://localhost:18080)（启动时会尝试自动打开）。

列表接口统一支持 `page`（从 1 开始）与 `size`（最大 100），响应体为 `{ items, total, page, size }`。

### 前端开发模式

```bash
# 终端 1：后端
mvn spring-boot:run

# 终端 2：Vite 热更新（代理 /api → 18080）
cd frontend && npm install && npm run dev
```

生产构建将静态资源输出到 `src/main/resources/static`：

```bash
cd frontend && npm run build
```

## Web 功能

| 页面 | API | 说明 |
|------|-----|------|
| 报告总览 | `GET /api/reports/daily?page&size`, `GET /api/reports/period/{type}?page&size` | 分页列表与下载 |
| Git 项目 | `GET /api/projects?page&size` + `POST/PUT/DELETE` | 本地路径或远程 URL + 分支 |
| AI 设置 | `GET /api/ai/providers?page&size` + CRUD + `GET/PUT /api/ai/policy` | 多个 OpenAI 兼容提供商，**priority 越小越优先** |
| 定时采集 | `GET/PUT /api/scheduler`, `POST /api/collect/now` | 默认 cron `0 0 18 * * ?`，时区 `Asia/Shanghai` |
| 生成报告 | `POST /api/reports/{daily,weekly,monthly}/generate` | 状态与 Excel 导出 |

每个提供商的 API Key 在响应中**不会明文返回**；更新时传 `__UNCHANGED__` 表示不修改已有密钥。运行时按启用状态与 priority 依次尝试，失败或冷却时自动切换下一个。

## Windows .exe 打包

在 **Windows** 上安装 JDK 17+ 与 Maven 后：

```powershell
.\scripts\package-windows.ps1
```

或在 Git Bash / Linux 上生成 app-image 骨架（完整 `.exe` 需在 Windows 上执行 `jpackage --type exe`）：

```bash
bash scripts/package-windows.sh
```

产物目录：`dist/win/`。亦可直接分发 fat jar：

```bash
java -jar target/dailyreport-1.0.0.jar
```

打包脚本会为 JVM 传入 `-Dapp.open-browser=true`，启动后打开本地 Web UI。

## 配置项

| 键 | 默认 | 说明 |
|----|------|------|
| `server.port` | `18080` | HTTP 端口 |
| `app.cli.enabled` | `false` | 是否启用旧版 CLI |
| `app.open-browser` | `true` | 启动时打开浏览器 |
| `config.yaml` | 可选 | 首次导入项目 / AI 配置 |

## 安全说明

- 勿提交 `config.yaml` 或真实 API Key / 数据库密码。
- 使用 `config.example.yaml` 与环境变量（如 `MINIMAX_API_KEY`）作为占位符。

## 许可证

见 [LICENSE](LICENSE)。
