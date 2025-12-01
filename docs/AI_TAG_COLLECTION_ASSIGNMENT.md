# AI 自動分配 Tag 與精選專題

## 概述

系統已從「向量相似度判斷」改為「AI 直接判斷」的方式來分配文章的 tags 和精選專題。

## 變更內容

### 1. AI Prompt 更新

在 `AiArticlePrompts.teachingStage2SystemPrompt()` 和 `teachingStage2UserPrompt()` 中新增：

- **Tags 選擇**：AI 從 50 個候選 tags 中選擇 3-5 個最相關的
- **精選專題選擇**：AI 從 8 個精選專題中選擇 0-2 個最適合的（可以不選）

### 2. AI 回應 JSON Schema 更新

Stage 2 的回應現在包含：

```json
{
  "title_from": "...",
  "title_to": "...",
  "summary_from": "...",
  "summary_to": "...",
  "explanation": { ... },
  "tags": ["Technology", "Cybersecurity", "Social Media"],
  "featured_collections": ["TECH"]
}
```

### 3. 自動配對流程

當 AI 生成文章內容後，`AiArticleService.saveAiResult()` 會：

1. 保存文章的 AI 內容（標題、摘要、解析等）
2. 從 AI 回應的 `tags` 陣列中提取 tag 名稱
3. 從 AI 回應的 `featured_collections` 陣列中提取專題 ID
4. 呼叫 `ArticleTagService.assignTagsByName()` 配對 tags
5. 呼叫 `FeaturedCollectionService.assignCollectionsById()` 配對專題

### 4. 配對方法

#### ArticleTagService.assignTagsByName()

- 根據 AI 回傳的 tag 名稱列表，查找對應的 `ArticleTagDefinition`
- 為文章建立 `ArticleTagAssignment` 記錄
- Score 設為 1.0（AI 判斷的給予滿分）

#### FeaturedCollectionService.assignCollectionsById()

- 根據 AI 回傳的專題 ID，查找對應的 `FeaturedCollection`
- 為文章建立 `ArticleFeaturedCollectionAssignment` 記錄
- Score 設為 1.0（AI 判斷的給予滿分）

## 候選清單

### Tags (50 個)

從 `src/main/resources/tag-definitions.json` 載入，包含：

- AI & Machine Learning
- Technology
- Business
- Finance
- Politics
- Health
- Science
- Environment
- Education
- Travel
- ... (共 50 個)

### 精選專題 (8 個)

從 `src/main/resources/featured-collections.json` 載入：

- **TOEIC** - 多益必考單字（商業/職場）
- **TECH** - AI 科技趨勢（ChatGPT & More）
- **TRAVEL** - 旅遊英語實戰（機場/飯店）
- **HEALTH** - 健康醫療
- **ENVIRONMENT** - 環境永續
- **FINANCE** - 財經金融
- **EDUCATION** - 教育學習
- **POLITICS** - 國際政治

## 優勢

1. **準確性更高**：AI 能理解文章的語義和上下文，比單純的向量相似度更準確
2. **無需 Embedding**：不再需要為 tag 和專題生成 embedding，節省成本
3. **即時配對**：文章生成時就立即配對完成，無需批次處理
4. **靈活性**：AI 可以判斷文章是否不適合任何專題（返回空陣列）

## 向量相關功能保留

以下功能仍然使用向量 embedding：

- **相關文章推薦** (`/api/ai-articles/{id}/related`)：計算文章之間的語義相似度
- **文章 embedding**：每篇文章仍會生成 embedding 用於相關文章推薦

## 向量相關功能已移除

以下功能不再使用向量判斷：

- ~~Tag 的 embedding 生成~~
- ~~精選專題的 embedding 生成~~
- ~~基於 cosine similarity 的 tag 配對~~
- ~~基於 cosine similarity 的專題配對~~
- ~~批次配對任務中的向量計算~~

相關的 repository 方法與 service 方法仍保留，但主要流程不會再調用。

## API 回傳

`GET /api/ai-articles/{id}` 回傳的 JSON 包含：

```json
{
  "id": 62,
  "title": "...",
  "tags": [
    {
      "id": 1,
      "name": "Technology",
      "description": "...",
      "score": 1.0
    },
    ...
  ],
  "featuredCollections": [
    {
      "id": 1,
      "collectionId": "TECH",
      "title": "AI 科技趨勢",
      "subtitle": "ChatGPT & More",
      "colorStart": "#5C6BC0",
      "colorEnd": "#26C6DA",
      "score": 1.0
    }
  ]
}
```

## 測試

### 測試流程

1. **匯入新文章**：
   ```bash
   curl "http://localhost:8080/api/feed-import/import?feedUrl=..."
   ```

2. **生成 AI 內容**：
   ```bash
   curl "http://localhost:8080/api/ai-articles/random-with-processing?count=1&difficulty=NORMAL"
   ```

3. **查詢文章**：
   ```bash
   curl "http://localhost:8080/api/ai-articles/{id}"
   ```

   應該能看到 `tags` 和 `featuredCollections` 欄位有內容。

### 預期結果

- 科技相關文章應該配對到 "Technology", "AI & Machine Learning" 等 tags
- 符合「AI 科技趨勢」主題的文章應該配對到 "TECH" 專題
- 商業英語文章應該配對到 "TOEIC" 專題
- 旅遊相關文章應該配對到 "TRAVEL" 專題

## 未來擴展

如需新增 tag 或精選專題：

1. 編輯 `src/main/resources/tag-definitions.json` 或 `featured-collections.json`
2. 重啟應用時 `TagAndCollectionBootstrapRunner` 會自動載入新定義
3. 新文章生成時 AI 就能選擇新的 tags/專題

不需要重新生成 embedding 或執行批次配對。
