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
), lending AS (
    SELECT CAST(tjzq AS BIGINT) AS month_key,
           COALESCE(SUM(dyfdbs), 0) AS total_count,
           COALESCE(SUM(dyfdje), 0) AS total_amount,
           COALESCE(SUM(CASE WHEN dklx = '04' THEN dyfdbs ELSE 0 END), 0) AS excluded_count,
           COALESCE(SUM(CASE WHEN dklx = '04' THEN dyfdje ELSE 0 END), 0) AS excluded_amount
    FROM dws_bus_lend_mfm
    WHERE CAST(tjzq AS BIGINT) IN (
        SELECT CAST(TO_CHAR(month_start, 'YYYYMM') AS BIGINT) FROM months
    )
    GROUP BY CAST(tjzq AS BIGINT)
)
SELECT TO_CHAR(months.month_start, 'YYYYMM') AS "month",
       COALESCE(lending.total_count, 0) - COALESCE(lending.excluded_count, 0) AS "dkffbs",
       COALESCE(lending.total_amount, 0) - COALESCE(lending.excluded_amount, 0) AS "dkffje"
FROM months
LEFT JOIN lending ON lending.month_key = CAST(TO_CHAR(months.month_start, 'YYYYMM') AS BIGINT)
ORDER BY months.month_start
