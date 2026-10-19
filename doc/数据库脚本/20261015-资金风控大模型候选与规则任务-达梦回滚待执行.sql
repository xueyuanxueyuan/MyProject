-- 危险操作：会删除候选、事件、任务和产物；仅 DBA 在确认无人写入、完成备份和业务批准后执行。
-- 若已有数据，应先导出并制定恢复方案；切勿与建表脚本串行自动执行。
DROP TABLE zjfk_llm_candidate_event;
DROP TABLE zjfk_llm_rule_artifact;
DROP TABLE zjfk_llm_rule_task;
DROP TABLE zjfk_llm_candidate_version;
DROP TABLE zjfk_llm_candidate;
-- 不删除 zjfk_llm_connection 或任何私有模型表。
