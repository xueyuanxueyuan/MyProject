SELECT b.YHDM AS YHDM, b.YHDMMC AS yhmc, COALESCE(SUM(a.jzye), 0) - COALESCE(SUM(prev.jzye), 0) AS ckbh
FROM ods_zjjs_yhzhxx_ye_bf a
JOIN ods_zjjs_yhzhxx b ON a.zhxx_id = b.ID
LEFT JOIN ods_zjjs_yhzhxx_ye_bf prev
    ON a.zhxx_id = prev.zhxx_id
    AND prev.BFRQ = LAST_DAY(ADD_MONTHS((LAST_DAY(ADD_MONTHS(TO_DATE(CAST((#{ssnd}) AS VARCHAR(4)) || '0101', 'YYYYMMDD'), (#{ssjd} * 3 - MONTH(TO_DATE(CAST((#{ssnd}) AS VARCHAR(4)) || '0101', 'YYYYMMDD')))))), -3))
WHERE a.BFRQ = (LAST_DAY(ADD_MONTHS(TO_DATE(CAST((#{ssnd}) AS VARCHAR(4)) || '0101', 'YYYYMMDD'), (#{ssjd} * 3 - MONTH(TO_DATE(CAST((#{ssnd}) AS VARCHAR(4)) || '0101', 'YYYYMMDD'))))))
GROUP BY GROUPING SETS ((b.YHDM, b.YHDMMC))
UNION ALL
SELECT '0', '无数据0', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1
    FROM ods_zjjs_yhzhxx_ye_bf a
    JOIN ods_zjjs_yhzhxx b ON a.zhxx_id = b.ID
    WHERE a.BFRQ = (LAST_DAY(ADD_MONTHS(TO_DATE(CAST((#{ssnd}) AS VARCHAR(4)) || '0101', 'YYYYMMDD'), (#{ssjd} * 3 - MONTH(TO_DATE(CAST((#{ssnd}) AS VARCHAR(4)) || '0101', 'YYYYMMDD'))))))
)
