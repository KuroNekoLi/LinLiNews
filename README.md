# LinLi News — Spring Boot + Docker

一個自動化抓取 BBC 新聞、提供完整文章內容的後端服務。支援定期排程抓取、全文抽取、分頁查詢、隨機文章抽取，以及 AI 教學解說功能，並自動產生
Swagger API 文件。

## 目錄

- [快速開始](#快速開始新手友好)
- [API 文件](#api-文件swagger)
- [主要功能](#主要功能)
- [系統架構](#系統架構與元件)
- [Docker 容器配置](#docker-容器配置說明)
- [Zeabur 部署](#zeabur-部署與-cicd)
- [環境變數](#環境變數本機與雲端通用)
- [常見問題](#常見問題排查)

---

## 快速開始（新手友好）

### 前置需求

- 已安裝 Docker Desktop
- 已安裝 JDK 21（macOS 可用 `sdkman` 或 `temurin`）
- 不需要安裝 Gradle（本專案已內建 `./gradlew`）

### 步驟

**1) 啟動基礎服務（MySQL、Full-Text RSS）**

```bash
# 在專案根目錄
docker-compose up -d
# 確認狀態
docker-compose ps
```

**2) 啟動 Spring Boot 服務**

```bash
./gradlew bootRun
```

**3) 開啟 API 文件（Swagger UI）**

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

**4) 試試看 API**

```bash
# 取得分頁文章
curl -s "http://localhost:8080/api/articles?page=0&size=5" | jq .

# 取得單篇（含可直接渲染的 HTML）
curl -s "http://localhost:8080/api/articles/1" | jq .

# 隨機抽取 3 篇文章
curl -s "http://localhost:8080/api/articles/random?count=3" | jq .

# 依 section 過濾（支援 world 子分類）
curl -s "http://localhost:8080/api/articles?page=0&size=5&section=world_asia" | jq .

# 取得 AI 處理過的文章（含繁中翻譯與教學解說）
curl -s "http://localhost:8080/api/ai-articles/1" | jq .

# 隨機取得 3 篇文章並自動進行 AI 處理（若尚未處理）
curl -s "http://localhost:8080/api/ai-articles/random-with-processing?count=3" | jq .
```

---

## API 文件（Swagger）

本專案使用 `springdoc-openapi` 自動產生文件，啟動後可至：

- **Swagger UI**: http://localhost:8080/swagger-ui/index.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

所有端點都提供完整的繁體中文說明。

**注意**：測試端點（如 `/api/ops/*`、`/api/background-jobs/*`）在生產環境的 Swagger 文件中會自動隱藏，且無法被調用。

### AI 文章 API (`/api/ai-articles`)

- `GET /api/ai-articles` - 列出已處理文章（分頁）
- `GET /api/ai-articles/{articleId}` - 取得或處理單篇 AI 文章
- `GET /api/ai-articles/random` - 隨機取得 n 篇 AI 文章（僅從已處理的文章中抽取）
- `GET /api/ai-articles/random-with-processing` - 隨機取得 n 篇文章並自動進行 AI 處理（若尚未處理則即時處理）

---

## 主要功能

### 文章匯入與管理

- 定期自動抓取 BBC 各欄位 RSS（內建排程）
- 解析 RSS，自動過濾非新聞內容（音頻、視頻、報紙頭版等）
- 若 RSS 已含全文則直接保存；否則使用 Full-Text RSS 服務補齊
- 文章去重（依 URL）

### API 端點

#### 文章 API (`/api/articles`)

- `GET /api/articles` - 分頁查詢文章列表
- `GET /api/articles/{id}` - 單篇文章詳情
- `GET /api/articles/random` - 隨機取得 n 篇文章
- `POST /api/articles/{id}/ai/process` - 使用 GPT-4o 處理文章

### AI 教學功能

- 使用 GPT-4o 處理文章內容
- 自動生成繁體中文翻譯（良好分段）
- 提供英文教學解說：
    - 單字講解（雙語：詞性、釋義、例句）
    - 文法講解（雙語解說與例句）
    - 句型分析（雙語解說與例句）
    - 片語/成語講解（雙語解說與例句）
    - 閱讀理解測驗（4 選 1 選擇題，附中文詳解）

---

## 系統架構與元件

### 技術棧

- **Spring Boot 3** + **Kotlin**（Web, Validation, JPA）
- **MySQL 8**（資料持久化，Flyway 管理 schema）
- **Full-Text RSS**（抽取文章全文）
- **JobRunr**（背景任務排程）
- **OpenAI GPT-4o**（AI 文章處理與教學生成）
- **Swagger/OpenAPI**（自動產生 API 文件）

### 關鍵目錄

```
src/main/kotlin/com/linli/news/
├── controller/         # API 控制器（對外端點）
├── service/           # 業務邏輯服務
├── domain/            # JPA 實體類別
├── repository/        # 資料庫存取層
├── integration/       # 外部服務整合（Full-Text RSS）
├── jobs/              # 定期抓取任務
├── config/            # 配置類別
└── model/             # DTO 資料模型

src/main/resources/
├── application.yml    # 應用程式配置
└── db/migration/      # Flyway SQL 遷移
```

---

## Docker 容器配置說明

本專案的 `docker-compose.yml` 啟動兩個容器：

### 1. MySQL（服務名稱：`mysql`）

```yaml
image: mysql:8.1
ports:
  - "3307:3306"            # 將容器 3306 映射到本機 3307
environment:
  MYSQL_ROOT_PASSWORD: rootpwd
  MYSQL_DATABASE: linli_news
  MYSQL_USER: linli
  MYSQL_PASSWORD: linlipwd
```

Spring Boot 連線字串（`application.yml`）：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3307/linli_news?...
    username: root
    password: rootpwd
```

### 2. Full-Text RSS（服務名稱：`fulltextrss`）

```yaml
image: heussd/fivefilters-full-text-rss:latest
ports:
  - "8085:80"               # 將容器 80 映射到本機 8085
```

Spring Boot 配置：
```yaml
fulltextrss:
  base-url: http://localhost:8085
```

### 容器管理指令

```bash
# 啟動所有容器
docker-compose up -d

# 檢查狀態
docker-compose ps

# 查看日誌
docker-compose logs -f

# 停止容器
docker-compose down

# 停止並清除資料（小心！）
docker-compose down -v
```

---

## Zeabur 部署與 CI/CD

這個專案提供 `Dockerfile`，Zeabur 會自動偵測並建置。

### 部署步驟

**1) 連結 Repo 與自動部署**

- 在 Zeabur 新建 Project → 連 GitHub → 選此 repo 的 main 分支
- 打開 Auto Deploy

**2) 部署 App 服務（Spring Boot）**
- 類型：GitHub（使用本專案 Dockerfile 建置）
- 綁定子網域：例如 `https://linlinews.zeabur.app`
- 設定環境變數（見下方）：
  ```bash
  # 必要環境變數
  SPRING_PROFILES_ACTIVE=prod
  
  # 資料庫連線
  SPRING_DATASOURCE_URL=jdbc:mysql://<MYSQL_HOST>:<MYSQL_PORT>/linli_news?...
  SPRING_DATASOURCE_USERNAME=<MYSQL_USER>
  SPRING_DATASOURCE_PASSWORD=<MYSQL_PASSWORD>
  
  # Full-Text RSS
  FULLTEXTRSS_BASE_URL=http://<FTR_INTERNAL_HOST>
  
  # OpenAI API
  OPENAI_API_KEY=<YOUR_OPENAI_API_KEY>
  ```

  **SERVER_URL** 是可選的，通常不需要設定，因為 springdoc-openapi 會自動從 HTTP 請求偵測伺服器 URL。

**3) 部署 Full-Text RSS 服務**

- 類型：Docker 容器映像
- 映像：`heussd/fivefilters-full-text-rss:latest`
- Port：80 / HTTP
- **僅開內網存取**，取得內網主機名（`.zeabur.internal`）

**4) 部署 MySQL**

- 使用 Zeabur 的 MySQL Add-on
- 取得連線資訊後填入 App 的環境變數

**5) 驗證部署**

```bash
# Swagger 文件
open https://<你的子網域>/swagger-ui/index.html

# 查詢文章列表
curl -s "https://<你的子網域>/api/articles?page=0&size=5" | jq .
```

---

## 環境變數（本機與雲端通用）

### 必填／常用

```bash
# 資料庫連線
SPRING_DATASOURCE_URL=jdbc:mysql://<MYSQL_HOST>:<MYSQL_PORT>/linli_news?...
SPRING_DATASOURCE_USERNAME=<MYSQL_USER>
SPRING_DATASOURCE_PASSWORD=<MYSQL_PASSWORD>

# Full-Text RSS 服務位址
FULLTEXTRSS_BASE_URL=http://localhost:8085  # 本機
# 或 http://<FTR_INTERNAL_HOST>           # Zeabur 內網

# OpenAI API（AI 功能必要）
OPENAI_API_KEY=<YOUR_OPENAI_API_KEY>
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_MODEL=gpt-4o
OPENAI_MAX_TOKENS=16000
```

### 可選

```bash
# 伺服器埠號（Zeabur 會自動注入）
PORT=8080

# Flyway 資料庫遷移
SPRING_FLYWAY_ENABLED=true

# JobRunr 排程設定
JOBRUNR_SCHEDULER_ENABLED=true
JOBRUNR_BACKGROUND_ENABLED=true
JOBRUNR_DASHBOARD_ENABLED=false  # 雲端建議關閉
JOBRUNR_DASHBOARD_PORT=8001

# Swagger 自訂伺服器 URL（選填，通常不需要設定）
# 注意：springdoc-openapi 會自動從 HTTP 請求偵測伺服器 URL
# 大多數情況下不需要設定此變數
# SERVER_URL=https://your-domain.zeabur.app
```

**說明**：`application.yml` 已內建預設值，未提供環境變數時使用本機開發的合理預設。

---

## 常見問題排查

### 本機開發

**Q: 8080 埠號已被占用**

- A: 關閉其他服務或修改 `server.port` 環境變數

**Q: MySQL 連不上**

- A: 確認 `docker-compose ps` 顯示 `healthy`，應用程式使用 `localhost:3307`

**Q: Full-Text RSS 無回應**

- A: 確認 `http://localhost:8085` 可開啟（容器運行中）

**Q: 排程任務沒有執行**

- A: 檢查 `JOBRUNR_SCHEDULER_ENABLED` 和 `JOBRUNR_BACKGROUND_ENABLED` 是否為 `true`

### 雲端部署（Zeabur）

**Q: FTR 內網 URL 怎麼取得？**

- A: FTR 服務 → 網路 → 內網存取，複製主機名（`.zeabur.internal`）

**Q: AI 回傳非 JSON 或被截斷**

- A: 確認 `OPENAI_API_KEY` 有效，可增加 `OPENAI_MAX_TOKENS` 值

**Q: 測試/管理端點無法存取**

- A: 測試端點（如 `/api/ops/*`、`/api/background-jobs/*`）預設在生產環境（`prod` profile）被停用

**Q: Swagger 文件中的伺服器 URL 顯示為 localhost:8080**

- A: 這通常不會發生，因為 springdoc-openapi 會自動從 HTTP 請求偵測伺服器 URL（透過 `X-Forwarded-*` 標頭）。
    - 在 Zeabur 等平台部署時，反向代理會自動設定這些標頭
    - 本專案已配置 `server.forward-headers-strategy=framework` 來支援此功能
    - 如果仍顯示錯誤 URL，可選擇性設定 `SERVER_URL` 環境變數，但通常不需要

### AI 功能

**Q: AI quota exceeded 錯誤**

- A: OpenAI API 額度不足，請檢查帳號計費狀態

**Q: explanation 欄位為 null**

- A: 可能因 `max_tokens` 不足導致回應被截斷，增加 `OPENAI_MAX_TOKENS` 設定值

---

## 支援的 BBC 區塊

### 主要區塊（每 30 分鐘更新）

- `news` - 主要新聞
- `world` - 國際新聞
- `uk` - 英國新聞
- `technology` - 科技新聞

### 次要區塊（每 60 分鐘更新）

- `business` - 商業新聞
- `health` - 健康新聞
- `science_and_environment` - 科學與環境
- `entertainment_and_arts` - 娛樂與藝術
- `politics` - 政治新聞
- `education` - 教育新聞

### 國際子區塊（每 60 分鐘更新）

- `world_africa` - 非洲
- `world_asia` - 亞洲
- `world_europe` - 歐洲
- `world_middle_east` - 中東
- `world_us_and_canada` - 美加
- `world_latin_america` - 拉丁美洲

---

## 開發環境設定

### 非生產環境啟用測試端點

測試/管理端點預設在生產環境停用，若要在本機或測試環境使用：

```bash
# 方式 1：不設定 profile（預設非 prod）
./gradlew bootRun

# 方式 2：明確指定 dev profile
./gradlew bootRun --args='--spring.profiles.active=dev'

# 方式 3：使用環境變數
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

測試端點包含：

- `/api/ops/*` - 運維驗證端點
- `/api/background-jobs/*` - 背景任務測試端點
- `/api/articles/admin/*` - 管理端點
- `/api/articles/*/fetch-content` - 內容補齊端點
- `/api/ai-articles/*/debug-*` - AI 調試端點

---

## 授權

內部專案示例，可自由調整使用。
