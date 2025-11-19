-- 新增各難度的內容欄位，並移除不再使用的 word_count
ALTER TABLE article_ai ADD COLUMN content_easy JSON;
ALTER TABLE article_ai ADD COLUMN content_normal JSON;
ALTER TABLE article_ai ADD COLUMN content_hard JSON;
ALTER TABLE article_ai ADD COLUMN content_very_hard JSON;
ALTER TABLE article_ai DROP COLUMN word_count;
