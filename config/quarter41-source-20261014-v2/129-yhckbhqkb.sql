SELECT 
    b.YHDM AS YHDM, 
    b.YHDMMC AS wtyh, 
    COALESCE(SUM(a.jzye), 0) AS ymckze, 
    COALESCE(SUM(a.jzye - a.NCYE), 0) AS jnczdje, 
    ROUND(
        (CASE 
            WHEN COALESCE(SUM(a.NCYE), 0) = 0 THEN 0 
            ELSE (COALESCE(SUM(a.jzye - a.NCYE), 0) / COALESCE(SUM(a.NCYE), 0)) 
        END * 100), 
        2 
    ) AS zdpfb, 
    COALESCE(SUM(a.jzye), 0) - COALESCE(SUM(prev.jzye), 0) AS hbzdje 
FROM ods_zjjs_yhzhxx_ye_bf a 
JOIN ods_zjjs_yhzhxx b ON a.zhxx_id = b.ID 
LEFT JOIN ods_zjjs_yhzhxx_ye_bf prev 
    ON a.zhxx_id = prev.zhxx_id 
    AND prev.BFRQ = LAST_DAY(ADD_MONTHS(DATE_FORMAT(LAST_DAY(DATE_ADD(STR_TO_DATE(#{ssnd}, '%Y'), INTERVAL (#{ssjd} * 3 - MONTH(STR_TO_DATE(#{ssnd}, '%Y'))) MONTH)),'%Y-%m-%d'), -3)) 
WHERE a.BFRQ = DATE_FORMAT(LAST_DAY(DATE_ADD(STR_TO_DATE(#{ssnd}, '%Y'), INTERVAL (#{ssjd} * 3 - MONTH(STR_TO_DATE(#{ssnd}, '%Y'))) MONTH)),'%Y-%m-%d') 
GROUP BY GROUPING SETS ((b.YHDM, b.YHDMMC)) 
ORDER BY b.YHDM
