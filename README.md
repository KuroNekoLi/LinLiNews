# LinLi News — Spring Boot + Docker

一個自動化抓取 BBC 新聞、提供完整文章內容的後端服務。支援定期排程抓取、全文抽取、分頁查詢、隨機文章抽取，以及自動產生 Swagger
API 文件。

## 目錄

- 快速開始（新手友好）
- API 文件（Swagger）
- 主要功能
- 系統架構與元件
- Docker 容器配置說明（一步一步）
- Zeabur 部署與 CI/CD（一步一步）
- 環境變數（本機與雲端通用）
- 常見問題排查

---

## 快速開始（新手友好）

前置需求：

- 已安裝 Docker Desktop
- 已安裝 JDK 21（macOS 可用 `sdkman` 或 `temurin`）
- 不需要安裝 Gradle（本專案已內建 `./gradlew`）

步驟：

1) 啟動基礎服務（MySQL、Full-Text RSS）

```bash
# 在專案根目錄
docker-compose up -d
# 確認狀態
docker-compose ps
```

2) 啟動 Spring Boot 服務

```bash
./gradlew bootRun
```

3) 開啟 API 文件（Swagger UI）

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

4) 試試看 API

```bash
# 取得分頁文章
curl -s "http://localhost:8080/api/articles?page=0&size=5" | jq .

# 取得單篇（含可直接渲染的 HTML）
curl -s "http://localhost:8080/api/articles/40" | jq .

# 隨機抽取 n 篇
curl -s "http://localhost:8080/api/articles/random?count=3" | jq .
# 依 section 過濾（支援 world 子分類）
curl -s "http://localhost:8080/api/articles?page=0&size=5&section=world_asia" | jq .
curl -s "http://localhost:8080/api/articles?page=0&size=5&section=world_africa" | jq .
curl -s "http://localhost:8080/api/articles?page=0&size=5&section=world_europe" | jq .
curl -s "http://localhost:8080/api/articles?page=0&size=5&section=world_middle_east" | jq .
curl -s "http://localhost:8080/api/articles?page=0&size=5&section=world_us_and_canada" | jq .
curl -s "http://localhost:8080/api/articles?page=0&size=5&section=world_latin_america" | jq .
# [管理] 立即匯入（用於驗證排程來源，避免等待）
curl -s -X POST "http://localhost:8080/api/articles/admin/import-bbc?section=world_asia" | jq .
curl -s -X POST "http://localhost:8080/api/articles/admin/import-bbc?section=world_africa" | jq .
curl -s -X POST "http://localhost:8080/api/articles/admin/import-bbc?section=world_europe" | jq .
curl -s -X POST "http://localhost:8080/api/articles/admin/import-bbc?section=world_middle_east" | jq .
curl -s -X POST "http://localhost:8080/api/articles/admin/import-bbc?section=world_us_and_canada" | jq .
curl -s -X POST "http://localhost:8080/api/articles/admin/import-bbc?section=world_latin_america" | jq .
```

---

## API 文件（Swagger）

本專案使用 `springdoc-openapi` 自動產生文件，啟動後可至：

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

不需要任何額外設定；專案啟動後會自動掃描 `@RestController`。

---

## 主要功能

- 定期抓取 BBC 各欄位 RSS（內建排程）
- 解析 RSS，確保只保留「真正的新聞文章」
- 若 RSS 已含全文則直接保存；否則以 Full-Text RSS 服務補齊
- 提供文章清單、單篇詳情（含 `contentHtml` 可直接渲染；`textContent` 為純文字）、隨機抽取
- 清理無效文章的管理端點（如報紙頭版合集、音頻、純影片頁等）

常用端點（參考 Swagger）

- GET `/api/articles` 分頁查詢
- GET `/api/articles/{id}` 單篇詳情（`contentHtml` 可直接渲染；`textContent` 為純文字）
- GET `/api/articles/random?count=3&source=BBC&section=news` 隨機文章
- POST `/api/articles/cleanup-invalid-articles` 清理無效文章
- POST `/api/articles/admin/import-bbc?section=...` 即時匯入單一 BBC 分類（測試使用）

---

## 系統架構與元件

- Spring Boot 3 + Kotlin（Web, Validation, JPA）
- MySQL 8（資料持久化，Flyway 管理 schema）
- Full-Text RSS（抽取文章全文）
- 排程：Spring `@Scheduled` 任務（BBC 多個欄位定時抓取）
- OpenAPI：`springdoc-openapi-starter-webmvc-ui`

關鍵目錄：

- `src/main/kotlin/com/linli/news/controller`：API 控制器（對外端點）
- `src/main/kotlin/com/linli/news/service`：RSS 匯入與過濾、內容補齊
- `src/main/kotlin/com/linli/news/integration`：Full-Text RSS 客戶端
- `src/main/kotlin/com/linli/news/jobs`：定期抓取任務
- `src/main/resources/db/migration`：Flyway SQL 遷移（自動創建資料表）

---

## Docker 容器配置說明（一步一步）

本專案的 `docker-compose.yml` 啟動兩個容器：

1) MySQL（服務名稱：`mysql`）

```yaml
image: mysql:8.1
ports:
  - "3307:3306"            # 將容器 3306 映射到本機 3307（避免與本機 MySQL 衝突）
environment:
  MYSQL_ROOT_PASSWORD: rootpwd
  MYSQL_DATABASE: linli_news
  MYSQL_USER: linli
  MYSQL_PASSWORD: linlipwd
volumes:
  - mysql-data:/var/lib/mysql  # 資料持久化
healthcheck:                     # 啟動健康檢查，確保可連線
  test: ["CMD", "mysqladmin", "ping", "-h", "127.0.0.1", "-u", "root", "-prootpwd"]
```

Spring Boot 連線字串（`application.yml`）對應：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3307/linli_news?...  # 指向本機 3307
    username: root
    password: rootpwd
```

2) Full-Text RSS（服務名稱：`fulltextrss`）

```yaml
image: heussd/fivefilters-full-text-rss:latest
ports:
  - "8085:80"               # 將容器 80 映射到本機 8085
volumes:
  - rss-cache:/var/www/html/cache/rss  # 快取持久化
```

Spring Boot 的 Full-Text RSS 基底位址：

```yaml
fulltextrss:
  base-url: http://localhost:8085
```

啟動／停止：

```bash
docker-compose up -d
docker-compose ps
docker-compose logs -f
# 停止
docker-compose down
# 停止並清除資料（小心！）
docker-compose down -v
```

---

## Zeabur 部署與 CI/CD

這個專案提供 `Dockerfile`，Zeabur 會自動偵測並建置。建議建立 3 個服務：App、Full-Text RSS、MySQL。

1) 連結 Repo 與自動部署

- 在 Zeabur 新建 Project → 連 GitHub → 選此 repo 的 main 分支 → 打開 Auto Deploy。

2) 部署 App 服務（Spring Boot）

- 類型：GitHub（使用本專案 Dockerfile 建置）
- 綁定子網域：例如 `https://linlinews.zeabur.app`
- 設定環境變數：

```bash
# 依你的 MySQL 與 FTR 調整
SPRING_DATASOURCE_URL=jdbc:mysql://<MYSQL_HOST>:<MYSQL_PORT>/linli_news?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=UTC&createDatabaseIfNotExist=true
SPRING_DATASOURCE_USERNAME=<MYSQL_USER>
SPRING_DATASOURCE_PASSWORD=<MYSQL_PASSWORD>
FULLTEXTRSS_BASE_URL=http://<FTR_INTERNAL_HOST>        # 例如 http://heussdfivefilters-full-text-rsslatest.zeabur.internal
JOBRUNR_SCHEDULER_ENABLED=true
JOBRUNR_BACKGROUND_ENABLED=true
JOBRUNR_DASHBOARD_ENABLED=false
```

3) 部署 Full-Text RSS 服務（FTR）

- 類型：Docker 容器映像
- 映像：`heussd/fivefilters-full-text-rss:latest`
- Port：80 / HTTP
- 僅開「內網存取」，取得內網主機名（結尾 `.zeabur.internal`），填入 App 的 `FULLTEXTRSS_BASE_URL`

4) 部署 MySQL（Add-on 或容器）

- 從面板取得 Host、Port、Database、Username、Password
- 回填至 App 的 `SPRING_DATASOURCE_*` 環境變數

5) 驗證部署

```bash
# Swagger 文件
open https://<你的子網域>/swagger-ui/index.html

# 免等排程，先手動匯入一個分類驗證（管理端點）
curl -s -X POST "https://<你的子網域>/api/articles/admin/import-bbc?section=uk" | jq .

# 查詢文章列表（含可直接渲染的 contentHtml）
curl -s "https://<你的子網域>/api/articles?page=0&size=5&section=uk" | jq '.content[] | {id, title, hasHtml: (.contentHtml|length>0), htmlLen: (.contentHtml|length)}'
```

小貼士：

- FTR 建議僅內網存取，不綁定公開網域。
- App 服務副本數建議 1，避免多副本重複執行排程。若要水平擴展，關閉非主副本的 `JOBRUNR_*`.

---

## 環境變數（本機與雲端通用）

必填／常用：

- `PORT`：平台會注入（本機預設 8080）
- `SPRING_DATASOURCE_URL`：MySQL JDBC URL
- `SPRING_DATASOURCE_USERNAME`：MySQL 使用者
- `SPRING_DATASOURCE_PASSWORD`：MySQL 密碼
- `FULLTEXTRSS_BASE_URL`：Full-Text RSS 服務位址（本機預設 `http://localhost:8085`）

可選：

- `SPRING_FLYWAY_ENABLED`（預設 true）
- `JOBRUNR_SCHEDULER_ENABLED`（預設 true）
- `JOBRUNR_BACKGROUND_ENABLED`（預設 true）
- `JOBRUNR_DASHBOARD_ENABLED`（預設 false，雲端建議關閉）
- `JOBRUNR_DASHBOARD_PORT`（預設 8001）

說明：`application.yml` 已內建預設值，若未提供則使用本機開發合理預設（如 `http://localhost:8085`）。

---

## 常見問題排查

- 8080 已被占用 → 關閉其他服務或修改 `server.port`
- 8001 JobRunr Dashboard 衝突 → 在 `application.yml` 調整 port 或停用 dashboard
- MySQL 連不上 → 確認 `docker-compose ps` 顯示 `healthy`，且應用程式使用 `localhost:3307`
- Full-Text RSS 無回應 → 確認 `http://localhost:8085` 可開啟（容器運行中）
- Zeabur 上 FTR 內網 URL 怎麼取得？→ FTR 服務 → 網路 → 內網存取，複製主機名（`.zeabur.internal`）。
- 如何切換本機/雲端的 FTR？→ 設定／不設定 `FULLTEXTRSS_BASE_URL` 即可（未設定時預設 `http://localhost:8085`）。

---

## 授權

內部專案示例，可自由調整使用。

