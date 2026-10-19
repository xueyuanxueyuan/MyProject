WITH month_ends AS (
    SELECT DATE_ADD(last_day(DATE_FORMAT(LAST_DAY(DATE_ADD(STR_TO_DATE(#{ssnd}, '%Y'), INTERVAL (#{ssjd} * 3 - MONTH(STR_TO_DATE(#{ssnd}, '%Y'))) MONTH)),'%Y-%m-%d')), INTERVAL -idx MONTH)  AS month_end
    FROM (
        SELECT 0 AS idx UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL 
        SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL 
        SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL 
        SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11
    ) t
)
SELECT 
    date_format(m.month_end, '%Y年%m月') AS month,
    round((COALESCE(g.JZYE, 0) / 100000000),2) AS gjjckye,
    round((COALESCE(z.JZYE, 0) / 100000000),2) AS zzsyckye,
    round(((COALESCE(g.JZYE, 0) + COALESCE(z.JZYE, 0)) / 100000000),2) AS ckyehj 
FROM month_ends m
LEFT JOIN ( 
    SELECT a.BFRQ,SUM(COALESCE(a.JZYE, 0)) AS JZYE 
    FROM ods_zjjs_yhzhxx_ye_bf a 
    JOIN ods_zjjs_yhzhxx b ON a.ZHXX_ID = b.ID 
    WHERE b.ZHXZ = '01' 
    GROUP BY a.BFRQ 
) g ON g.BFRQ = m.month_end
LEFT JOIN ( 
    SELECT a.BFRQ,SUM(COALESCE(a.JZYE, 0)) AS JZYE 
    FROM ods_zjjs_yhzhxx_ye_bf a 
    JOIN ods_zjjs_yhzhxx b ON a.ZHXX_ID = b.ID 
    WHERE b.ZHXZ = '03' 
    GROUP BY a.BFRQ 
) z ON z.BFRQ = m.month_end
ORDER BY m.month_end
