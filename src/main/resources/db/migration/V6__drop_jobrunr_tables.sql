-- V6__drop_jobrunr_tables.sql
-- 刪除 JobRunr 相關資料表

-- 刪除 JobRunr 使用的資料表
DROP TABLE IF EXISTS jobrunr_recurring_jobs;
DROP TABLE IF EXISTS jobrunr_jobs;
DROP TABLE IF EXISTS jobrunr_metadata;
DROP TABLE IF EXISTS jobrunr_backgroundjobservers;