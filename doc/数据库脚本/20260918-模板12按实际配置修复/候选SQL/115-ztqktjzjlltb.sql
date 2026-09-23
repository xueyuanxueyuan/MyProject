WITH offsets AS (
    SELECT 0 AS month_offset FROM dual
    UNION ALL SELECT 1 FROM dual
    UNION ALL SELECT 2 FROM dual
    UNION ALL SELECT 3 FROM dual
    UNION ALL SELECT 4 FROM dual
    UNION ALL SELECT 5 FROM dual
    UNION ALL SELECT 6 FROM dual
    UNION ALL SELECT 7 FROM dual
    UNION ALL SELECT 8 FROM dual
    UNION ALL SELECT 9 FROM dual
    UNION ALL SELECT 10 FROM dual
    UNION ALL SELECT 11 FROM dual
), months AS (
    SELECT ADD_MONTHS(TO_DATE(CAST(#{ssnd} AS VARCHAR(4)) || '0101', 'YYYYMMDD'), CAST(#{ssjd} AS INTEGER) * 3 - 1 - month_offset) AS month_start
    FROM offsets
), month_keys AS (
    SELECT TO_CHAR(month_start, 'YYYYMM') AS month_label,
           CAST(TO_CHAR(month_start, 'YYYYMM') AS BIGINT) AS month_key
    FROM months
), gj_agg AS (
    SELECT CAST(TJZQ AS BIGINT) AS month_key, SUM(DYSJZE) AS gj_sum
    FROM dws_bus_per_paid_mfm
    WHERE CAST(TJZQ AS BIGINT) IN (SELECT month_key FROM month_keys)
    GROUP BY CAST(TJZQ AS BIGINT)
), hs_agg AS (
    SELECT CAST(TJZQ AS BIGINT) AS month_key,
           SUM(DYHSBJ + DYHSLX + DYHSFX + DYHSFL) AS hs_sum
    FROM dws_bus_ln_rec_mfm
    WHERE CAST(TJZQ AS BIGINT) IN (SELECT month_key FROM month_keys)
    GROUP BY CAST(TJZQ AS BIGINT)
), tq_agg AS (
    SELECT CAST(TJZQ AS BIGINT) AS month_key, SUM(DYTQJE) AS tq_sum
    FROM dws_bus_ft_per_mfm
    WHERE CAST(TJZQ AS BIGINT) IN (SELECT month_key FROM month_keys)
    GROUP BY CAST(TJZQ AS BIGINT)
), fd_agg AS (
    SELECT CAST(TJZQ AS BIGINT) AS month_key, SUM(DYFDJE) AS fd_sum
    FROM dws_bus_lend_mfm
    WHERE CAST(TJZQ AS BIGINT) IN (SELECT month_key FROM month_keys)
    GROUP BY CAST(TJZQ AS BIGINT)
)
SELECT am.month_label AS "sjyf",
       ROUND((COALESCE(gj.gj_sum, 0) + COALESCE(hs.hs_sum, 0)) / 100000000, 2) AS "zjlr",
       ROUND((COALESCE(tq.tq_sum, 0) + COALESCE(fd.fd_sum, 0)) / 100000000, 2) AS "zjlc",
       ROUND((COALESCE(gj.gj_sum, 0) + COALESCE(hs.hs_sum, 0)
            - COALESCE(tq.tq_sum, 0) - COALESCE(fd.fd_sum, 0)) / 100000000, 2) AS "zjjll"
FROM month_keys am
LEFT JOIN gj_agg gj ON am.month_key = gj.month_key
LEFT JOIN hs_agg hs ON am.month_key = hs.month_key
LEFT JOIN tq_agg tq ON am.month_key = tq.month_key
LEFT JOIN fd_agg fd ON am.month_key = fd.month_key
ORDER BY am.month_key
