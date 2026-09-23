WITH base_data AS (
    SELECT a.*, b.YHMC,b.zhxz
    FROM ods_zjjs_yhzhxx_ye_bf a
    JOIN ods_zjjs_yhzhxx b ON a.zhxx_id = b.ID
    WHERE a.BFRQ = (LAST_DAY(ADD_MONTHS(TO_DATE(CAST((#{ssnd}) AS VARCHAR(4)) || '0101', 'YYYYMMDD'), (#{ssjd} * 3 - MONTH(TO_DATE(CAST((#{ssnd}) AS VARCHAR(4)) || '0101', 'YYYYMMDD'))))))
),
aggregated_data AS (
    SELECT
        round(COALESCE(SUM(jzye), 0),2) AS ckzje,
        round(COALESCE(SUM(CASE WHEN ZHXZ='01' THEN jzye ELSE 0 END), 0),2) AS zfgjjck,
        round(COALESCE(SUM(CASE WHEN ZHXZ='03' THEN jzye ELSE 0 END), 0),2) AS zzsyck,
        round(COALESCE(SUM(jzye - NCYE), 0),2) AS ckzjje,
        round(COALESCE(SUM(CASE WHEN ZHXZ='01' THEN jzye - NCYE ELSE 0 END), 0),2) AS zfgjjckzjje,
        round(COALESCE(SUM(CASE WHEN ZHXZ='03' THEN jzye - NCYE ELSE 0 END), 0),2) AS zzsyckzjje
    FROM base_data
),
user_stats AS (
    SELECT
        round(COALESCE(SUM(CASE WHEN je > 0 THEN 1 ELSE 0 END), 0),2) AS yhckzjyhs,
        round(COALESCE(SUM(CASE WHEN je < 0 THEN 1 ELSE 0 END), 0),2) AS yhckjsyhs
    FROM (
        SELECT b.YHMC,
               COALESCE(SUM(jzye - NCYE), 0) AS je
        FROM ods_zjjs_yhzhxx_ye_bf a
        JOIN ods_zjjs_yhzhxx b ON a.zhxx_id = b.ID
        WHERE a.BFRQ = (LAST_DAY(ADD_MONTHS(TO_DATE(CAST((#{ssnd}) AS VARCHAR(4)) || '0101', 'YYYYMMDD'), (#{ssjd} * 3 - MONTH(TO_DATE(CAST((#{ssnd}) AS VARCHAR(4)) || '0101', 'YYYYMMDD'))))))
        GROUP BY b.YHMC
    ) c
)
SELECT * FROM aggregated_data, user_stats
