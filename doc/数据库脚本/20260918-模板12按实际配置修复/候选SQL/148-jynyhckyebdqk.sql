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
), balances AS (
    SELECT a.BFRQ,
           SUM(CASE WHEN b.ZHXZ = '01' THEN COALESCE(a.JZYE, 0) ELSE 0 END) AS gjj_balance,
           SUM(CASE WHEN b.ZHXZ = '03' THEN COALESCE(a.JZYE, 0) ELSE 0 END) AS zzsy_balance
    FROM ods_zjjs_yhzhxx_ye_bf a
    JOIN ods_zjjs_yhzhxx b ON a.ZHXX_ID = b.ID
    WHERE a.BFRQ IN (SELECT LAST_DAY(month_start) FROM months)
    GROUP BY a.BFRQ
)
SELECT TO_CHAR(months.month_start, 'YYYY') || '年'
       || TO_CHAR(months.month_start, 'MM') || '月' AS "month",
       ROUND(COALESCE(balances.gjj_balance, 0) / 100000000, 2) AS "gjjckye",
       ROUND(COALESCE(balances.zzsy_balance, 0) / 100000000, 2) AS "zzsyckye",
       ROUND((COALESCE(balances.gjj_balance, 0) + COALESCE(balances.zzsy_balance, 0)) / 100000000, 2) AS "ckyehj"
FROM months
LEFT JOIN balances ON balances.BFRQ = LAST_DAY(months.month_start)
ORDER BY months.month_start
