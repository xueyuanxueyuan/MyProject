-- 仅在确认尚未被任何业务使用、并已备份及获批后由DBA人工执行。
-- 执行将永久删除候选连接及全部加密密钥；不可与建表脚本自动串行运行。
-- 执行前记录预期行数并比对：SELECT COUNT(*) FROM zjfk_llm_connection;
DROP TABLE zjfk_llm_connection;
