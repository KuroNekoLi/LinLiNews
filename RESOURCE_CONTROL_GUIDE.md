# 資源控制快速參考指南

本文件提供快速參考，說明如何在不同環境中控制定時任務與 Full-Text RSS 服務。

---

## 🎯 控制方式總覽

| 環境            | 方式        | 適用場景           |
|---------------|-----------|----------------|
| **本機開發**      | 便利腳本      | Windows 環境快速切換 |
| **本機開發**      | Docker 命令 | 手動精確控制         |
| **本機開發**      | 環境變數      | 啟動時指定          |
| **Zeabur 雲端** | 環境變數      | 生產環境資源管理       |

---

## 💻 本機開發

### 方式 1：便利腳本（推薦）

```powershell
# 啟動應用程式（無定時任務模式）
.\start-without-jobs.bat

# 停用定時任務與 Full-Text RSS
.\disable-jobs.bat

# 重新啟用定時任務與 Full-Text RSS
.\enable-jobs.bat
```

### 方式 2：Docker 命令

```bash
# 停止 Full-Text RSS
docker stop fulltextrss

# 啟動 Full-Text RSS
docker start fulltextrss

# 停止所有服務
docker-compose stop

# 啟動所有服務
docker-compose start
```

### 方式 3：環境變數

```powershell
# 停用定時任務
$env:JOBRUNR_SCHEDULER_ENABLED="false"
$env:JOBRUNR_BACKGROUND_ENABLED="false"
.\gradlew.bat bootRun

# 停用 Full-Text RSS
$env:FULLTEXTRSS_ENABLED="false"
.\gradlew.bat bootRun
```

---

## ☁️ Zeabur 雲端部署

### 環境變數設定

在 Zeabur 控制台 → 服務 → Variables 中設定：

```bash
# 停用定時任務（節省 CPU）
JOBRUNR_SCHEDULER_ENABLED=false
JOBRUNR_BACKGROUND_ENABLED=false

# 停用 Full-Text RSS（節省記憶體）
FULLTEXTRSS_ENABLED=false
```

### 三種部署模式

#### 🟢 模式 1：全功能（正式運營）

```bash
JOBRUNR_SCHEDULER_ENABLED=true
JOBRUNR_BACKGROUND_ENABLED=true
FULLTEXTRSS_ENABLED=true
FULLTEXTRSS_BASE_URL=http://<ftr-service>.zeabur.internal
```

**資源**：CPU ~25% | 記憶體 ~800MB  
**所需服務**：MySQL + Full-Text RSS + Spring Boot

---

#### 🟡 模式 2：手動更新（開發/測試）

```bash
JOBRUNR_SCHEDULER_ENABLED=false
JOBRUNR_BACKGROUND_ENABLED=false
FULLTEXTRSS_ENABLED=true
FULLTEXTRSS_BASE_URL=http://<ftr-service>.zeabur.internal
```

**資源**：CPU ~15% | 記憶體 ~600MB  
**所需服務**：MySQL + Full-Text RSS + Spring Boot

---

#### 🔴 模式 3：僅查詢（Demo/展示）

```bash
JOBRUNR_SCHEDULER_ENABLED=false
JOBRUNR_BACKGROUND_ENABLED=false
FULLTEXTRSS_ENABLED=false
```

**資源**：CPU ~10% | 記憶體 ~400MB  
**所需服務**：MySQL + Spring Boot（不需要 Full-Text RSS）

---

## 🔄 功能影響對照表

| 功能         | 定時任務啟用 | FTR 啟用 | 說明           |
|------------|--------|--------|--------------|
| ✅ 查詢文章     | 任意     | 任意     | 總是可用         |
| ✅ AI 處理    | 任意     | 任意     | 總是可用         |
| ⚠️ 自動抓取新文章 | ✅      | ✅      | 兩者都需要        |
| ⚠️ 手動匯入文章  | 任意     | ✅      | 需要 FTR       |
| ❌ 完整文章內容   | 任意     | ✅      | FTR 停用時可能不完整 |

---

## 📊 資源使用估計

### CPU 使用率

- 🟢 全功能模式：~25%
- 🟡 手動更新模式：~15%
- 🔴 僅查詢模式：~10%

### 記憶體使用

- 🟢 全功能模式：~800MB
- 🟡 手動更新模式：~600MB
- 🔴 僅查詢模式：~400MB

### 網路流量

- 🟢 全功能模式：持續使用（每 30-60 分鐘抓取）
- 🟡 手動更新模式：按需使用
- 🔴 僅查詢模式：最低使用

---

## 🛠️ 常見使用場景

### 場景 1：開發測試時減少干擾

```powershell
# 本機
.\start-without-jobs.bat
```

### 場景 2：展示 Demo（使用現有資料）

```bash
# Zeabur
JOBRUNR_SCHEDULER_ENABLED=false
JOBRUNR_BACKGROUND_ENABLED=false
FULLTEXTRSS_ENABLED=false
```

### 場景 3：資源有限的雲端環境

```bash
# Zeabur
JOBRUNR_SCHEDULER_ENABLED=false   # 停用自動更新
JOBRUNR_BACKGROUND_ENABLED=false
FULLTEXTRSS_ENABLED=true          # 保留手動匯入功能
```

### 場景 4：正式運營（完整功能）

```bash
# Zeabur
JOBRUNR_SCHEDULER_ENABLED=true
JOBRUNR_BACKGROUND_ENABLED=true
FULLTEXTRSS_ENABLED=true
FULLTEXTRSS_BASE_URL=http://<ftr-service>.zeabur.internal
```

---

## 📖 相關文件

- **[README.md](./README.md)** - 專案總覽與快速開始
- **[ZEABUR_ENV_SETUP.md](./ZEABUR_ENV_SETUP.md)** - Zeabur 環境變數完整指南
- **[LOCAL_TUNNEL_SETUP.md](./LOCAL_TUNNEL_SETUP.md)** - 本機隧道設定

---

## 💡 快速決策流程圖

```
需要自動更新新聞嗎？
├─ 是 → 啟用 JobRunr + Full-Text RSS（全功能模式）
└─ 否 → 
    ├─ 偶爾需要手動更新？
    │   ├─ 是 → 停用 JobRunr，啟用 Full-Text RSS（手動更新模式）
    │   └─ 否 → 停用 JobRunr + Full-Text RSS（僅查詢模式）
```

---

**最後更新**：2025-01-14
