#!/bin/bash

# ================================================
# 測試同一篇文章的多個難度是否都能正確儲存
# ================================================

BASE_URL="http://localhost:8080"
ARTICLE_ID=""

echo "=========================================="
echo "測試：同一篇文章查詢多個難度"
echo "=========================================="
echo ""

# 步驟 1：隨機生成一篇 VERY_EASY 文章
echo "[步驟 1] 生成 VERY_EASY 難度文章..."
RESPONSE=$(curl -s -X GET "${BASE_URL}/api/ai-articles/random-with-processing?count=1&difficulty=VERY_EASY")
echo "Response: $RESPONSE" | jq '.'

# 提取 article ID
ARTICLE_ID=$(echo $RESPONSE | jq -r '.articles[0].id')

if [ -z "$ARTICLE_ID" ] || [ "$ARTICLE_ID" == "null" ]; then
    echo "❌ 錯誤：無法獲取文章 ID"
    exit 1
fi

echo "✓ 成功生成文章，ID: $ARTICLE_ID"
echo ""
sleep 2

# 步驟 2：查詢該文章的 VERY_EASY 難度（應該直接返回，不重新處理）
echo "[步驟 2] 查詢 VERY_EASY 難度（應該從資料庫讀取）..."
RESPONSE=$(curl -s -X GET "${BASE_URL}/api/ai-articles/${ARTICLE_ID}?difficulty=VERY_EASY")
echo "Available difficulties: $(echo $RESPONSE | jq -r '.availableDifficulties')"
echo "Current difficulty: $(echo $RESPONSE | jq -r '.content.difficulty')"
echo ""
sleep 2

# 步驟 3：查詢該文章的 EASY 難度（應該觸發新的 AI 處理）
echo "[步驟 3] 查詢 EASY 難度（應該觸發 AI 生成新 view）..."
RESPONSE=$(curl -s -X GET "${BASE_URL}/api/ai-articles/${ARTICLE_ID}?difficulty=EASY")
if [ $? -eq 0 ]; then
    echo "✓ EASY 難度查詢成功"
    echo "Available difficulties: $(echo $RESPONSE | jq -r '.availableDifficulties')"
    echo "Current difficulty: $(echo $RESPONSE | jq -r '.content.difficulty')"
else
    echo "❌ EASY 難度查詢失敗"
    exit 1
fi
echo ""
sleep 2

# 步驟 4：查詢該文章的 NORMAL 難度
echo "[步驟 4] 查詢 NORMAL 難度（應該觸發 AI 生成新 view）..."
RESPONSE=$(curl -s -X GET "${BASE_URL}/api/ai-articles/${ARTICLE_ID}?difficulty=NORMAL")
if [ $? -eq 0 ]; then
    echo "✓ NORMAL 難度查詢成功"
    echo "Available difficulties: $(echo $RESPONSE | jq -r '.availableDifficulties')"
    echo "Current difficulty: $(echo $RESPONSE | jq -r '.content.difficulty')"
else
    echo "❌ NORMAL 難度查詢失敗"
    exit 1
fi
echo ""
sleep 2

# 步驟 5：查詢該文章的 HARD 難度
echo "[步驟 5] 查詢 HARD 難度（應該觸發 AI 生成新 view）..."
RESPONSE=$(curl -s -X GET "${BASE_URL}/api/ai-articles/${ARTICLE_ID}?difficulty=HARD")
if [ $? -eq 0 ]; then
    echo "✓ HARD 難度查詢成功"
    echo "Available difficulties: $(echo $RESPONSE | jq -r '.availableDifficulties')"
    echo "Current difficulty: $(echo $RESPONSE | jq -r '.content.difficulty')"
else
    echo "❌ HARD 難度查詢失敗"
    exit 1
fi
echo ""
sleep 2

# 步驟 6：最終驗證 - 查詢所有可用難度
echo "[步驟 6] 最終驗證：檢查所有可用難度..."
RESPONSE=$(curl -s -X GET "${BASE_URL}/api/ai-articles/${ARTICLE_ID}")
AVAILABLE_DIFFICULTIES=$(echo $RESPONSE | jq -r '.availableDifficulties')
AVAILABLE_VIEWS=$(echo $RESPONSE | jq -r '.availableViews')

echo "Available difficulties: $AVAILABLE_DIFFICULTIES"
echo "Available views:"
echo "$AVAILABLE_VIEWS" | jq '.'

# 檢查是否有 4 個難度
DIFFICULTY_COUNT=$(echo $AVAILABLE_DIFFICULTIES | jq 'length')
if [ "$DIFFICULTY_COUNT" -ge "4" ]; then
    echo "✅ 成功！文章有 $DIFFICULTY_COUNT 個不同難度的 views"
else
    echo "⚠️  警告：只有 $DIFFICULTY_COUNT 個難度（預期至少 4 個）"
fi
echo ""

# 步驟 7：檢查資料庫中是否只有一筆 ArticleAi 記錄
echo "[步驟 7] 驗證資料庫一致性..."
echo "請在資料庫中執行以下 SQL 來驗證："
echo ""
echo "SELECT"
echo "    ai.id as article_ai_id,"
echo "    ai.article_id,"
echo "    COUNT(v.id) as view_count,"
echo "    GROUP_CONCAT(DISTINCT v.difficulty) as difficulties"
echo "FROM article_ai ai"
echo "LEFT JOIN article_ai_view v ON ai.id = v.article_ai_id"
echo "WHERE ai.article_id = $ARTICLE_ID"
echo "GROUP BY ai.id, ai.article_id;"
echo ""
echo "預期結果："
echo "- article_ai_id: 只有一個"
echo "- view_count: 4 (或更多)"
echo "- difficulties: VERY_EASY, EASY, NORMAL, HARD"
echo ""

echo "=========================================="
echo "測試完成！"
echo "Article ID: $ARTICLE_ID"
echo "=========================================="
