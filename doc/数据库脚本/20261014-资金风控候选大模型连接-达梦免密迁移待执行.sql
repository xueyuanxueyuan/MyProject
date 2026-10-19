-- 仅当旧版 zjfk_llm_connection 已建表且 api_key_cipher 为 NOT NULL 时执行。
-- DBA 先核对 schema、备份及目标版本；不得与建表脚本重复执行。
ALTER TABLE zjfk_llm_connection ALTER COLUMN api_key_cipher SET NULL;
COMMENT ON COLUMN zjfk_llm_connection.api_key_cipher IS '公网密钥AES-GCM密文；内网免密连接为NULL';
-- 迁移后由 DBA 验证列可空性；回滚需先清理免密记录且评估业务影响，不自动回滚。
