-- 將 article_ai 表中的舊有語言特定欄位移除，改為依 view 儲存
ALTER TABLE article_ai DROP COLUMN summary_en;
ALTER TABLE article_ai DROP COLUMN summary_zh;
ALTER TABLE article_ai DROP COLUMN title_zh;
