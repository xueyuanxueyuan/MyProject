-- 仅供 DBA 核验目标 schema、备份、对象名与达梦版本后执行；本文件未执行。
-- 独立于旧私有模型表；先确保 zjfk_llm_connection 已按接入版完成迁移。
CREATE TABLE zjfk_llm_candidate (
    id VARCHAR(36) NOT NULL,
    connection_id VARCHAR(36) NOT NULL,
    alias_name VARCHAR(128) NOT NULL,
    model_version VARCHAR(128),
    capability VARCHAR(32) DEFAULT 'UNAVAILABLE' NOT NULL,
    indicator_code VARCHAR(64),
    current_version INT DEFAULT 1 NOT NULL,
    status VARCHAR(24) DEFAULT 'DRAFT' NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_zjfk_llm_candidate PRIMARY KEY (id),
    CONSTRAINT fk_zjfk_llm_candidate_conn FOREIGN KEY (connection_id) REFERENCES zjfk_llm_connection(id),
    CONSTRAINT ck_zjfk_llm_candidate_cap CHECK (capability IN ('TRAIN','ADAPT','ZERO_SHOT','INFERENCE_ONLY','UNAVAILABLE')),
    CONSTRAINT ck_zjfk_llm_candidate_status CHECK (status IN ('DRAFT','TEST_PENDING','TESTED','REVIEWED','CANDIDATE','DISABLED')),
    CONSTRAINT ck_zjfk_llm_candidate_version CHECK (current_version >= 1)
);
CREATE INDEX idx_zjfk_llm_candidate_conn ON zjfk_llm_candidate(connection_id);

CREATE TABLE zjfk_llm_candidate_version (
    candidate_id VARCHAR(36) NOT NULL,
    version INT NOT NULL,
    parameter_json CLOB,
    prompt_template CLOB,
    capability VARCHAR(32) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_zjfk_llm_candidate_ver PRIMARY KEY (candidate_id, version),
    CONSTRAINT fk_zjfk_llm_candidate_ver FOREIGN KEY (candidate_id) REFERENCES zjfk_llm_candidate(id),
    CONSTRAINT ck_zjfk_llm_candidate_ver_num CHECK (version >= 1)
);

CREATE TABLE zjfk_llm_rule_task (
    id VARCHAR(36) NOT NULL,
    candidate_id VARCHAR(36) NOT NULL,
    candidate_version INT NOT NULL,
    dataset_id BIGINT NOT NULL,
    snapshot_hash VARCHAR(64),
    cutoff_time TIMESTAMP NOT NULL,
    parameter_json CLOB,
    task_kind VARCHAR(24) NOT NULL,
    task_status VARCHAR(24) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    error_summary VARCHAR(512),
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_zjfk_llm_rule_task PRIMARY KEY (id),
    CONSTRAINT uk_zjfk_llm_rule_task_key UNIQUE (idempotency_key),
    CONSTRAINT fk_zjfk_llm_rule_task_ver FOREIGN KEY (candidate_id, candidate_version)
        REFERENCES zjfk_llm_candidate_version(candidate_id, version),
    CONSTRAINT ck_zjfk_llm_rule_kind CHECK (task_kind IN ('RULE_PROMPT','ADAPT')),
    CONSTRAINT ck_zjfk_llm_rule_status CHECK (task_status IN ('DRAFT','WAITING_NETWORK','RUNNING','UNKNOWN','FAILED','SUCCEEDED','CANCELLED'))
);
CREATE INDEX idx_zjfk_llm_rule_task_candidate ON zjfk_llm_rule_task(candidate_id, candidate_version);

CREATE TABLE zjfk_llm_rule_artifact (
    task_id VARCHAR(36) NOT NULL,
    rule_json CLOB NOT NULL,
    prompt_template CLOB NOT NULL,
    metric_json CLOB,
    artifact_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_zjfk_llm_rule_artifact PRIMARY KEY (task_id),
    CONSTRAINT fk_zjfk_llm_rule_artifact_task FOREIGN KEY (task_id) REFERENCES zjfk_llm_rule_task(id)
);

CREATE TABLE zjfk_llm_candidate_event (
    id VARCHAR(36) NOT NULL,
    candidate_id VARCHAR(36) NOT NULL,
    version INT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    event_note VARCHAR(512),
    actor VARCHAR(64) NOT NULL,
    event_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT pk_zjfk_llm_candidate_event PRIMARY KEY (id),
    CONSTRAINT fk_zjfk_llm_candidate_event FOREIGN KEY (candidate_id, version)
        REFERENCES zjfk_llm_candidate_version(candidate_id, version)
);
CREATE INDEX idx_zjfk_llm_candidate_event ON zjfk_llm_candidate_event(candidate_id, event_at);
