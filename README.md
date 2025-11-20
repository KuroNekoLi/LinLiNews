# LinLi News — Spring Boot + Docker

一個自動化抓取 BBC 新聞、提供完整文章內容的後端服務。支援定期排程抓取、全文抽取、分頁查詢、隨機文章抽取，以及 AI 教學解說功能，並自動產生
Swagger API 文件。

## 目錄

- [快速開始](#快速開始新手友好)
- [資源管理](#資源管理控制定時任務與服務)
- [本地隧道部署](#本地隧道部署cloudflared)
- [API 文件](#api-文件swagger)
- [主要功能](#主要功能)
- [系統架構](#系統架構與元件)
- [Docker 容器配置](#docker-容器配置說明)
- [Zeabur 部署](#zeabur-部署與-cicd)
- [環境變數](#環境變數本機與雲端通用)
- [常見問題排查](#常見問題排查)
- [測試](#測試)
- [資源控制快速參考](#資源控制快速參考指南)

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

**3) 如果希望在本地開發時禁用定時任務（scheduler）**

可以使用本地配置文件啟動應用程序：

```bash
# 使用本地配置啟動，將禁用定時任務
./gradlew bootRun --args="--spring.profiles.active=local"
```

或者設置環境變數：

```bash
# 在 Linux/macOS
export SPRING_PROFILES_ACTIVE=local
./gradlew bootRun

# 在 Windows PowerShell
$env:SPRING_PROFILES_ACTIVE="local"
.\gradlew.bat bootRun
```

**4) 開啟 API 文件（Swagger UI）**

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

**5) 試試看 API**

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

## 資源管理：控制定時任務與服務

如果您覺得主機負擔太大，可以選擇性地停用定時任務或 Full-Text RSS 服務。

### 方式一：使用便利腳本（推薦）

專案已提供批次檔案簡化操作：

```powershell
# 啟動應用程式（不執行定時任務，節省資源）
.\start-without-jobs.bat

# 停用定時任務與 Full-Text RSS
.\disable-jobs.bat

# 重新啟用定時任務與 Full-Text RSS
.\enable-jobs.bat
```

### 方式二：手動控制 Docker 容器

```bash
# 只停止 Full-Text RSS（保留 MySQL）
docker stop fulltextrss

# 重新啟動
docker start fulltextrss

# 停止所有 Docker 服務
docker-compose stop

# 啟動所有 Docker 服務
docker-compose start
```

### 方式三：環境變數控制

```powershell
# 停用 JobRunr 定時任務
$env:JOBRUNR_SCHEDULER_ENABLED="false"
$env:JOBRUNR_BACKGROUND_ENABLED="false"
.\gradlew.bat bootRun

# 啟用 JobRunr 定時任務
$env:JOBRUNR_SCHEDULER_ENABLED="true"
$env:JOBRUNR_BACKGROUND_ENABLED="true"
.\gradlew.bat bootRun
```

### 效果說明

停用後的影響：

- ✅ **API 服務正常運作**：查詢文章、AI 處理等功能完全可用
- ✅ **資料庫現有資料**：可正常查詢已匯入的文章
- ❌ **不會自動抓取新文章**：需要手動觸發或重新啟用
- ❌ **無法使用 Full-Text RSS**：若 RSS 內容不完整，無法補齊全文

### 手動觸發匯入（可選）

如果停用定時任務但偶爾需要更新文章，可以使用 API 手動觸發：

```bash
# 僅在開發環境可用（生產環境已停用）
curl -X POST "http://localhost:8080/api/ops/trigger-bbc-news"
```

**注意**：此端點在生產環境（`prod` profile）會被停用以確保安全。

### 📖 完整資源控制指南

更多詳細的資源控制選項、部署模式對比、使用場景建議，請參考：

- **[資源控制快速參考指南](./RESOURCE_CONTROL_GUIDE.md)** - 本機與雲端資源控制總覽
- **[Zeabur 環境變數設定指南](./ZEABUR_ENV_SETUP.md)** - Zeabur 雲端部署完整指南

---

## 本地隧道部署（Cloudflared）

### 使用場景

當您想讓其他人訪問您本地運行的服務時，可以使用 Cloudflared 隧道創建公開網址。

### 快速設置

**1) 啟動本地服務**

```powershell
# 啟動 Docker 服務
docker-compose up -d

# 啟動應用（另一個終端）
.\gradlew.bat bootRun
```

**2) 創建公開隧道**

```powershell
# 新開一個 PowerShell 視窗
"C:\Program Files (x86)\cloudflared\cloudflared.exe" tunnel --url http://localhost:8080
```

**3) 取得公開網址**

您會看到類似這樣的輸出：

```
Your quick Tunnel has been created! Visit it at:
https://reasoning-compression-marijuana-katrina.trycloudflare.com
```

### 實際範例

**目前運行的公開網址**: https://reasoning-compression-marijuana-katrina.trycloudflare.com

其他人可以通過以下網址訪問：

- **API 文件**: https://reasoning-compression-marijuana-katrina.trycloudflare.com/swagger-ui/index.html
- **文章列表**: https://reasoning-compression-marijuana-katrina.trycloudflare.com/api/articles
- **隨機文章**: https://reasoning-compression-marijuana-katrina.trycloudflare.com/api/articles/random?count=3
- **AI 文章**: https://reasoning-compression-marijuana-katrina.trycloudflare.com/api/ai-articles

### 便利工具

專案已提供批次檔案簡化操作：

- `start-with-tunnel.bat` - 啟動本地服務
- `start-tunnel.bat` - 創建 Cloudflared 隧道
- `LOCAL_TUNNEL_SETUP.md` - 完整設置指南

### 注意事項

⚠️ **安全提醒**：

- 隧道會將您的本地服務暴露到公共網路
- 網址會在重啟隧道後改變
- 僅建議開發測試使用，勿用於生產環境

---

## API 文件（Swagger）

本專案使用 `springdoc-openapi` 自動產生文件，啟動後可至：

- **本地**: http://localhost:8080/swagger-ui/index.html
- **公開隧道**: https://reasoning-compression-marijuana-katrina.trycloudflare.com/swagger-ui/index.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

所有端點都提供完整的繁體中文說明。

**注意**：測試端點（如 `/api/ops/*`、`/api/background-jobs/*`）在生產環境的 Swagger 文件中會自動隱藏，且無法被調用。

### AI 文章 API (`/api/ai-articles`)

- `GET /api/ai-articles` - 列出已處理文章（分頁）
- `GET /api/ai-articles/{articleId}` - 取得或處理單篇 AI 文章
- `GET /api/ai-articles/random` - 隨機取得 n 篇 AI 文章（僅從已處理的文章中抽取）
- `GET /api/ai-articles/random-with-processing` - 隨機取得 n 篇文章並自動進行 AI 處理（若尚未處理則即時處理）

> 所有 AI 文章端點都支援 `difficulty`、`fromLanguage`、`toLanguage` 查詢參數。若未指定，預設為 `en → zh-TW`。

### AI 視圖與多語資料模型

- `article_ai_view`：每一筆代表「某篇文章 + 語言對 + 難度」。欄位包含 `from_lang`、`to_lang`、`difficulty`、雙語標題／摘要、`optimized_html_from/to`、`cleaned_text_from/to` 以及備援的 `legacy_explanation_json`。
- 解說資料拆為多張表並以 `article_ai_view_id` 外鍵串接：
  - `ai_vocabulary_item`（雙語單字與例句）
  - `ai_grammar_item`
  - `ai_sentence_pattern_item`
  - `ai_phrase_idiom_item`
  - `ai_comprehension_mcq` + `ai_comprehension_option`
- 每張子表都保留 `sort_order` 以維持 JSON 順序，也可依語言 pair 快速查詢。
- Flyway V14/V15 將舊的 `article_ai_content` 與 JSON 欄位遷移至上述結構，並把舊表重新命名為 `article_ai_content_legacy` 作為回滾備援。

#### 新增語言對（例如 fr → en）的建議流程

1. 確保文章已存在並具備全文 HTML。
2. 呼叫 `POST /api/articles/{id}/ai/process?fromLanguage=fr&toLanguage=en&difficulty=NORMAL` 讓 AI 產出新的視圖，或使用 `/api/ai-articles/random-with-processing` 搭配相同語言參數批次處理。
3. 回傳結果會在 `availableViews` 中列出 `fr → en` 視圖，前端可再用 `difficulty` 過濾。
4. 若需回滾，可將 `article_ai_content_legacy` 內容重新塞回 `article_ai_view`（或重新跑一次處理流程）。

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
  base-url: http://localhost:8085  # 本機
# 或 http://<FTR_INTERNAL_HOST>           # Zeabur 內網
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
  
  # Full-Text RSS 啟用控制（可選）
  FULLTEXTRSS_ENABLED=true   # 啟用全文抓取
  
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

### 資源控制與環境變數

在 Zeabur 上，您可以透過環境變數靈活控制應用程式的功能與資源使用：

#### 🎛️ 控制選項

1. **定時任務控制（JobRunr）**
   ```bash
   JOBRUNR_SCHEDULER_ENABLED=false   # 停用自動抓取，節省 CPU
   JOBRUNR_BACKGROUND_ENABLED=false
   ```

2. **Full-Text RSS 控制**
   ```bash
   FULLTEXTRSS_ENABLED=false         # 停用全文抓取，節省記憶體
   # 停用後可不部署 Full-Text RSS 服務
   ```

3. **Scheduler 控制**
   ```bash
   SCHEDULER_ENABLED=false  # 停用 Spring Scheduler，節省 CPU
   ```

#### 📊 三種部署模式

| 模式          | 定時任務 | Full-Text RSS | CPU | 記憶體    | 適用場景    |
|-------------|------|---------------|-----|--------|---------|
| 🟢 **全功能**  | ✅    | ✅             | 高   | ~800MB | 正式運營    |
| 🟡 **手動更新** | ❌    | ✅             | 中   | ~600MB | 開發/測試   |
| 🔴 **僅查詢**  | ❌    | ❌             | 低   | ~400MB | Demo/展示 |

#### 📖 詳細說明

完整的環境變數設定、部署場景範例、資源使用對比與故障排查，請參考：

**👉 [Zeabur 環境變數設定指南](./ZEABUR_ENV_SETUP.md)**

---

## 環境變數（本機與雲端通用）

### 多環境配置

系統使用多份環境配置（application.yml）以適應不同平台：

- `application.yml`：預設配置
- `application-local.yml`：本機開發環境（路徑：`./linli_news.db`，關閉定時任務）
- `application-prod.yml`：Zeabur 生產環境（路徑：`/data/linli_news.db`）
- `application-tunnel.yml`：電腦通道環境
- `application-test.yml`：單元測試環境

如何指定使用的環境檔案：

```bash
# 使用本地環境配置啟動
./gradlew bootRun --args="--spring.profiles.active=local"

# 或設定環境變數
# Linux/macOS
export SPRING_PROFILES_ACTIVE=local
./gradlew bootRun

# Windows PowerShell
$env:SPRING_PROFILES_ACTIVE="local"
.\gradlew.bat bootRun
```

### 必填／常用

```bash
# 資料庫連線
SPRING_DATASOURCE_URL=jdbc:mysql://<MYSQL_HOST>:<MYSQL_PORT>/linli_news?...
SPRING_DATASOURCE_USERNAME=<MYSQL_USER>
SPRING_DATASOURCE_PASSWORD=<MYSQL_PASSWORD>

# 注意：Zeabur 上建議使用 SPRING_PROFILES_ACTIVE=prod
# 會自動使用 /data/linli_news.db 作為數據庫路徑

# Full-Text RSS 服務位址
FULLTEXTRSS_BASE_URL=http://localhost:8085  # 本機
# 或 http://<FTR_INTERNAL_HOST>           # Zeabur 內網

# Full-Text RSS 啟用控制（可選，預設為 true）
FULLTEXTRSS_ENABLED=true   # 啟用全文抓取
# FULLTEXTRSS_ENABLED=false  # 停用，節省資源

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

# Scheduler 控制
SCHEDULER_ENABLED=true  # 啟用 Spring Scheduler

# Swagger 自訂伺服器 URL（選填，通常不需要設定）
# 注意：springdoc-openapi 會自動從 HTTP 請求偵測伺服器 URL
# 大多數情況下不需要設定此變數
# SERVER_URL=https://your-domain.zeabur.app
```

**說明**：`application.yml` 已內建預設值，未提供環境變數時使用本機開發的合理預設。

### JobRunr 排程設定

- `JOBRUNR_SCHEDULER_ENABLED`: 啟用或停用 JobRunr 排程功能，預設為 `true`。
- `JOBRUNR_BACKGROUND_ENABLED`: 啟用或停用 JobRunr 背景任務執行，預設為 `true`。

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

## 測試

本專案使用 Kotlin 友好的測試框架進行測試。

### 測試框架

- **MockK** - Kotlin 原生 mocking 框架
- **SpringMockK** - Spring + MockK 整合
- **JUnit 5** - 測試斷言與執行
- **Spring Boot Test** - Spring 測試工具（`@WebMvcTest`, `@DataJpaTest`）
- **H2 Database** - 記憶體資料庫（測試環境）

### 測試範例

```kotlin
// Service 測試（MockK）
@Test
fun `randomWithProcessing 應該自動處理未處理的文章`() {
    val article = Article(...)
    every { articleRepository.findAll() } returns listOf(article)
    every { aiProcessor.process(...) } returns aiResponse
    
    val (result, total, processedCount) = service.randomWithProcessing(
        count = 1,
        source = null,
        section = null,
        difficulty = DifficultyLevel.NORMAL,
        fromLanguage = "en",
        toLanguage = "zh-TW"
    )

    assertEquals(1, processedCount)
    verify(exactly = 1) { aiProcessor.process(any(), any(), any(), any()) }
}

### 測試覆蓋範圍

✅ **Service 層**
- `AiArticleServiceTest` - 13 個測試
  - random() 方法的來源/區塊過濾
  - randomWithProcessing() 自動處理文章
  - listProcessed() 分頁查詢
  - getOrProcess() 取得或處理單篇
  
- `FeedImportServiceTest` - 1 個測試
  - RSS 匯入流程與無效文章過濾

✅ **Controller 層**
- `AiArticleControllerTest` - 6 個測試
  - GET /api/ai-articles（列表、隨機、帶處理）
  - 來源與區塊過濾
  
- `ArticleQueryControllerTest` - 8 個測試
  - GET /api/articles（列表、單篇、過濾）
  - 404 錯誤處理

✅ **Repository 層**
- `ArticleRepositoryTest` - 6 個測試
  - CRUD 操作
  - 自訂查詢方法（findByOriginalUrl、findBySource_Name、findBySource_Section）
  - 分頁查詢

### 執行測試

```bash
# 執行所有測試
./gradlew test

# 執行特定測試
./gradlew test --tests AiArticleServiceTest

# 查看測試報告
open build/reports/tests/test/index.html
```

### 測試環境配置

測試環境使用 `src/test/resources/application-test.yml`：

- H2 記憶體資料庫（MySQL 模式）
- 停用 Flyway 遷移（使用 Hibernate 自動建表）
- 停用 JobRunr 排程
- Mock 外部服務端點

---

