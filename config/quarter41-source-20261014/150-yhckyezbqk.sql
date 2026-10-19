
UNION ALL

SELECT 
    '0' AS YHDM,
    '无数据' AS yhmc,
    0 AS jzye_sum,
    0 AS ncje,
    0 AS total_jzye,
    0 AS ckzb,
    0 AS jnczj
SELECT 
    b.YHDM,
    b.YHDMMC AS yhmc,
    COALESCE(SUM(a.jzye), 0) AS jzye_sum,
    COALESCE(SUM(a.NCYE), 0) AS ncje,
    SUM(COALESCE(SUM(a.jzye), 0)) OVER () AS total_jzye,
    ROUND(
        CASE
            WHEN SUM(COALESCE(SUM(a.jzye), 0)) OVER () = 0 THEN 0
            ELSE COALESCE(SUM(a.jzye), 0) / SUM(COALESCE(SUM(a.jzye), 0)) OVER ()
        END * 100,
        2
    ) AS ckzb,
    ROUND(
        CASE
            WHEN SUM(COALESCE(SUM(a.jzye), 0)) OVER () = 0 THEN 0
            ELSE COALESCE(SUM(a.jzye), 0) / SUM(COALESCE(SUM(a.jzye), 0)) OVER ()
        END * 100,
        2
    ) - ROUND(
        CASE
            WHEN SUM(COALESCE(SUM(a.jzye), 0)) OVER () = 0 THEN 0
            ELSE COALESCE(SUM(a.NCYE), 0) / SUM(COALESCE(SUM(a.jzye), 0)) OVER ()
        END * 100,
        2
    ) AS jnczj
FROM 
    ods_zjjs_yhzhxx_ye_bf a
JOIN 
    ods_zjjs_yhzhxx b ON a.zhxx_id = b.ID
WHERE 
    a.bfrq = DATE_FORMAT(
        LAST_DAY(
            DATE_ADD(STR_TO_DATE(#{ssnd}, '%Y'), INTERVAL (#{ssjd} * 3 - MONTH(STR_TO_DATE(#{ssnd}, '%Y'))) MONTH)
        ),
        '%Y-%m-%d'
    )
GROUP BY 
    b.YHDM, b.YHDMMC
