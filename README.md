# LinLi News — Spring Boot + Docker

一個自動化抓取 BBC 新聞、提供完整文章內容的後端服務。支援定期排程抓取、全文抽取、分頁查詢、隨機文章抽取，以及自動產生 Swagger
API 文件。

## 目錄

- 快速開始（新手友好）
- API 文件（Swagger）
- 主要功能
- 系統架構與元件
- Docker 容器配置說明（一步一步）
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

## 常見問題排查

- 8080 已被占用 → 關閉其他服務或修改 `server.port`
- 8001 JobRunr Dashboard 衝突 → 在 `application.yml` 調整 port 或停用 dashboard
- MySQL 連不上 → 確認 `docker-compose ps` 顯示 `healthy`，且應用程式使用 `localhost:3307`
- Full-Text RSS 無回應 → 確認 `http://localhost:8085` 可開啟（容器運行中）

---

## 授權

內部專案示例，可自由調整使用。

