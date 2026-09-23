WITH current_data AS (
    SELECT
        b.yhdm,
        b.yhdmmc AS yhmc,
        SUM(a.jzye) AS current_sum
    FROM ods_zjjs_yhzhxx_ye_bf a
    JOIN ods_zjjs_yhzhxx b ON a.ZHXX_ID = b.ID
    WHERE a.BFRQ = (LAST_DAY(
            ADD_MONTHS(TO_DATE(CAST((#{ssnd}) AS VARCHAR(4)) || '0101', 'YYYYMMDD'), (#{ssjd} * 3 - MONTH(TO_DATE(CAST((#{ssnd}) AS VARCHAR(4)) || '0101', 'YYYYMMDD'))))
        ))
    GROUP BY b.yhdm, b.yhdmmc
),
last_data AS (
    SELECT
        b.yhdm,
        b.yhdmmc AS yhmc,
        SUM(a.jzye) AS last_sum
    FROM ods_zjjs_yhzhxx_ye_bf a
    JOIN ods_zjjs_yhzhxx b ON a.ZHXX_ID = b.ID
    WHERE a.BFRQ = LAST_DAY(ADD_MONTHS(
        (LAST_DAY(
                ADD_MONTHS(TO_DATE(CAST((#{ssnd}) AS VARCHAR(4)) || '0101', 'YYYYMMDD'), (#{ssjd} * 3 - MONTH(TO_DATE(CAST((#{ssnd}) AS VARCHAR(4)) || '0101', 'YYYYMMDD'))))
            )),
        -3
    ))
    GROUP BY b.yhdm, b.yhdmmc
),
main_query AS (
    SELECT
        COALESCE(c.yhdm, l.yhdm) AS yhdm,
        COALESCE(c.yhmc, l.yhmc) AS yhmc,
        ROUND(COALESCE(c.current_sum, 0) - COALESCE(l.last_sum, 0), 2) AS ckzjje
    FROM current_data c
    FULL OUTER JOIN last_data l ON c.yhdm = l.yhdm

)

SELECT * FROM main_query
UNION ALL
SELECT '0', '无数据', 0.00
FROM dual
WHERE NOT EXISTS (
    SELECT 1 FROM main_query
    UNION ALL
    SELECT 1 FROM DUAL WHERE 1=0
)
