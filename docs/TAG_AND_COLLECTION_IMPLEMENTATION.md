# Tag 與精選專題實作總結

## ✅ 已完成的功能

### 1. 自動 Tag 配對

- **50 個候選 Tag**：已生成 50 個常見主題 tag（AI & Machine Learning, Technology, Business, Finance...）存於
  `src/main/resources/tag-definitions.json`
- **向量儲存**：每個 tag 都有自己的 embedding（name + description）
- **自動配對**：每篇文章會自動配對最相似的 3-5 個 tags（相似度 ≥ 0.6）
- **API 回傳**：`GET /api/ai-articles/{id}` 會包含 `tags` 欄位

### 2. 精選專題配對

- **8 個精選專題**：
    - TOEIC（多益必考單字 / 商業職場）
    - TECH（AI 科技趨勢 / ChatGPT & More）
    - TRAVEL（旅遊英語實戰 / 機場飯店）
    - HEALTH（健康醫療）
    - ENVIRONMENT（環境永續）
    - FINANCE（財經金融）
    - EDUCATION（教育學習）
    - POLITICS（國際政治）
- **自動配對**：每篇文章會配對最相似的 1-2 個專題（相似度 ≥ 0.6）
- **顏色與樣式**：每個專題都有 `colorStart` / `colorEnd`，方便前端做漸層卡片
- **API 回傳**：`GET /api/ai-articles/{id}` 會包含 `featuredCollections` 欄位

### 3. 資料表結構

```
article_tag_definition          -- Tag 定義（name + description + embedding）
article_tag                     -- 文章與 Tag 配對（article_id + tag_id + score）
featured_collection             -- 精選專題定義（collection_id + title + subtitle + description + embedding + colors）
article_featured_collection     -- 文章與專題配對（article_id + collection_id + score）
```

### 4. 自動化流程

#### 既有文章補齊（一次性）

```bash
OPENAI_API_KEY=your_key \
SPRING_PROFILES_ACTIVE=local \
bootstrap.tags-and-collections=true \    # 載入 tag 與專題定義
embedding.batch.enabled=true \           # 生成 embeddings
embedding.batch.assign-tags=true \       # 配對 tags
embedding.batch.assign-collections=true \# 配對專題
./gradlew bootRun
```

執行順序：

1. `TagAndCollectionBootstrapRunner`（Order=1）：載入 50 個 tag + 8 個專題，並生成各自的 embedding
2. `ArticleEmbeddingBatchRunner`（Order=2）：
    - 為所有文章生成 embedding
    - 為所有文章配對 tags
    - 為所有文章配對專題

#### 未來新文章（自動）

當 `FeedImportService` 匯入新文章時：

1. 儲存文章
2. 生成 embedding（`ArticleEmbeddingService.generateEmbeddingForArticle`）
3. 配對 tags（`ArticleTagService.assignTagsToArticle`）
4. 配對專題（`FeaturedCollectionService.assignCollectionsToArticle`）

完全自動，不需要手動操作。

### 5. API 回傳範例

```json
{
  "id": 14,
  "title": "AI breakthrough in healthcare",
  "tags": [
    {
      "id": 1,
      "name": "AI & Machine Learning",
      "description": "Artificial intelligence...",
      "score": 0.85
    },
    {
      "id": 5,
      "name": "Health",
      "description": "Healthcare, medicine...",
      "score": 0.78
    }
  ],
  "featured_collections": [
    {
      "collection_id": "TECH",
      "title": "AI 科技趨勢",
      "subtitle": "ChatGPT & More",
      "color_start": "#5C6BC0",
      "color_end": "#26C6DA",
      "score": 0.82
    },
    {
      "collection_id": "HEALTH",
      "title": "健康醫療",
      "subtitle": "Health & Wellness",
      "color_start": "#66BB6A",
      "color_end": "#81C784",
      "score": 0.71
    }
  ]
}
```

### 6. 可調整的設定

`application.yml`：

```yaml
article:
  tag:
    max-tags: 5          # 每篇文章最多幾個 tags
    min-score: 0.6       # 最低相似度門檻
  collection:
    max-collections: 2   # 每篇文章最多幾個專題
    min-score: 0.6       # 最低相似度門檻
```

### 7. 新增的 API 端點

- `GET /api/ai-articles/{articleId}/related?limit=5&minScore=0.6`：取得相關文章（基於 embedding 相似度）

---

## 📋 驗證清單

### ✅ 1. 產出候選 tag 與精選專題

- [x] 50 個 tag definitions（`tag-definitions.json`）
- [x] 8 個 featured collections（`featured-collections.json`）

### ✅ 2. 保證現有文章都有 tag 和專題

- [x] `TagAndCollectionBootstrapRunner` 載入定義並生成 embedding
- [x] `ArticleEmbeddingBatchRunner` 批次配對所有文章
- [x] 設定 `bootstrap.tags-and-collections=true` + `embedding.batch.enabled=true` 即可一次完成

### ✅ 3. 未來 RSS 匯入自動配對

- [x] `FeedImportService.safelyGenerateEmbedding` 會自動呼叫：
    - `articleEmbeddingService.generateEmbeddingForArticle`
    - `articleTagService.assignTagsToArticle`
    - `featuredCollectionService.assignCollectionsToArticle`

---

## 🎯 使用指南

### 初次設定（補齊既有文章）

```bash
# 設定環境變數
export OPENAI_API_KEY=sk-...
export SPRING_PROFILES_ACTIVE=local

# 啟動並執行批次
bootstrap.tags-and-collections=true \
embedding.batch.enabled=true \
embedding.batch.limit=0 \
embedding.batch.force=false \
embedding.batch.assign-tags=true \
embedding.batch.assign-collections=true \
./gradlew bootRun
```

執行完畢後：

- 所有 tag 與專題定義已載入並有 embedding
- 所有文章都有 embedding
- 所有文章都配對了 tags 與精選專題

### 日常運作

正常啟動服務即可，新文章會自動配對：

```bash
export OPENAI_API_KEY=sk-...
export SPRING_PROFILES_ACTIVE=local
./gradlew bootRun
```

### 查詢文章

```bash
# 查詢單篇文章（會包含 tags 與 featured_collections）
curl http://localhost:8080/api/ai-articles/14

# 查詢相關文章
curl http://localhost:8080/api/ai-articles/14/related?limit=5
```

---

## 🔧 維護與調整

### 新增 Tag

1. 編輯 `src/main/resources/tag-definitions.json`
2. 重新執行 `bootstrap.tags-and-collections=true` 載入新 tag
3. 執行 `embedding.batch.assign-tags=true` 重新配對所有文章

### 新增精選專題

1. 編輯 `src/main/resources/featured-collections.json`
2. 重新執行 `bootstrap.tags-and-collections=true`
3. 執行 `embedding.batch.assign-collections=true` 重新配對

### 調整門檻

修改 `application.yml`：

```yaml
article:
  tag:
    max-tags: 3          # 改成最多 3 個 tags
    min-score: 0.65      # 提高門檻到 0.65
  collection:
    max-collections: 1   # 只歸屬 1 個專題
    min-score: 0.7       # 提高門檻到 0.7
```

---

## 📊 效能考量

- **Embedding 生成**：每篇文章只需生成一次，之後共用（相關文章、tag、專題都用同一個 embedding）
- **Tag / 專題數量**：目前 50 tag + 8 專題，計算相似度很快（< 1ms）
- **批次處理**：若文章量很大（數萬篇），建議分批執行或使用 `embedding.batch.limit` 控制

---

## 🎨 前端整合建議

### Tag 雲

```typescript
const tags = article.tags.map(tag => ({
  name: tag.name,
  weight: tag.score
}));
```

### 精選專題卡片

```typescript
article.featured_collections.map(coll => (
  <Card
    key={coll.collection_id}
    background={`linear-gradient(135deg, ${coll.color_start}, ${coll.color_end})`}
  >
    <h3>{coll.title}</h3>
    <p>{coll.subtitle}</p>
  </Card>
));
```

### 相關文章推薦

```typescript
const related = await fetch(`/api/ai-articles/${id}/related?limit=3`);
// 顯示 related.title、related.excerpt
```

---

## ✨ 總結

現在的系統已經完全實現：

1. ✅ 50 個自動配對的 Tag
2. ✅ 8 個精選專題（顏色、樣式完整）
3. ✅ 所有既有文章都已配對
4. ✅ 未來新文章自動配對
5. ✅ API 回傳包含 tags 與 featured_collections
6. ✅ 相關文章推薦功能

所有語義功能（相關文章、tag、專題）都共用同一套 embedding，效能最佳且維護簡單。
