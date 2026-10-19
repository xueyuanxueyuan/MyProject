-- 资金风控一期：候选大模型连接配置。适用于目标达梦环境，尚未在目标库执行。
-- 执行前核对业务 schema、表不存在、备份、授权、目标版本及回滚方案。
-- 仅新增本表，不修改 zjfk_model、私有模型训练/预测及正式结果表。
-- 密钥字段只存 AES-GCM 密文；加密主密钥只由服务运行环境提供，不写入数据库。
CREATE TABLE zjfk_llm_connection (
    id VARCHAR(36) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    base_url VARCHAR(1024) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    api_key_cipher VARCHAR(4096) NULL,
    test_enabled INT DEFAULT 1 NOT NULL,
    version INT NOT NULL,
    enabled INT DEFAULT 0 NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_zjfk_llm_connection PRIMARY KEY (id),
    CONSTRAINT ck_zjfk_llm_conn_version CHECK (version >= 1),
    CONSTRAINT ck_zjfk_llm_conn_status CHECK (enabled IN (-1, 0)),
    CONSTRAINT ck_zjfk_llm_conn_test CHECK (test_enabled IN (0, 1))
);

CREATE INDEX idx_zjfk_llm_conn_updated ON zjfk_llm_connection (updated_at);

COMMENT ON TABLE zjfk_llm_connection IS '资金风控候选大模型连接配置，不参与私有模型表和正式预测';
COMMENT ON COLUMN zjfk_llm_connection.id IS 'UUID配置ID，与私有模型ID独立';
COMMENT ON COLUMN zjfk_llm_connection.provider IS '操作员录入的供应商标识';
COMMENT ON COLUMN zjfk_llm_connection.base_url IS '页面录入的OpenAI兼容协议基址，经服务端地址格式及危险目标校验';
COMMENT ON COLUMN zjfk_llm_connection.model_name IS '供应商侧模型名称';
COMMENT ON COLUMN zjfk_llm_connection.api_key_cipher IS '公网密钥AES-GCM密文；内网免密连接为NULL';
COMMENT ON COLUMN zjfk_llm_connection.test_enabled IS '是否允许对该连接发起合成测试：1允许，0禁止';
COMMENT ON COLUMN zjfk_llm_connection.version IS '乐观锁版本，从1开始，变更后递增';
COMMENT ON COLUMN zjfk_llm_connection.enabled IS '候选状态：0待测，-1停用；无正式启用状态';
COMMENT ON COLUMN zjfk_llm_connection.updated_by IS '最后更新的后端操作员用户ID';
COMMENT ON COLUMN zjfk_llm_connection.updated_at IS '最后更新时间，由服务端SQL写入';

-- 执行后核验对象及索引、注释，并用只读查询校验字段：
-- SELECT id, provider, base_url, model_name, version, enabled, updated_by, updated_at
--   FROM zjfk_llm_connection WHERE 1 = 0;
-- 不要使用 SELECT * 展示 api_key_cipher；回滚请单独审查回滚脚本，禁止自动串行执行。
