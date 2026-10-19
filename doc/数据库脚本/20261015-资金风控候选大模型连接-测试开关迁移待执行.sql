-- 仅供已建 zjfk_llm_connection 且尚无 test_enabled 字段的环境使用；未执行。
-- DBA 应先核对 schema、备份和现有字段，禁止与新版建表脚本重复执行。
ALTER TABLE zjfk_llm_connection ADD test_enabled INT DEFAULT 1 NOT NULL;
ALTER TABLE zjfk_llm_connection ADD CONSTRAINT ck_zjfk_llm_conn_test CHECK (test_enabled IN (0, 1));
COMMENT ON COLUMN zjfk_llm_connection.test_enabled IS '是否允许对该连接发起合成测试：1允许，0禁止';
-- 执行后检查字段缺省值，并确认老记录测试权限是否应逐条关闭。
