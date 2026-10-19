WITH all_months AS (
    SELECT DATE_FORMAT(LAST_DAY(DATE_ADD(STR_TO_DATE(#{ssnd}, '%Y'), INTERVAL (#{ssjd} * 3 - MONTH(STR_TO_DATE(#{ssnd}, '%Y'))) MONTH)),'%Y%m') AS month
    UNION ALL
    SELECT DATE_FORMAT(DATE_ADD(CURRENT_DATE(), INTERVAL - (LEVEL) MONTH), '%Y%m')
    FROM (SELECT 1 AS LEVEL UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 
          UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 
          UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11) AS levels
),
gj_agg AS (
    SELECT TJZQ, SUM(DYSJZE) AS gj_sum 
    FROM dws_bus_per_paid_mfm 
    WHERE TJZQ >= DATE_FORMAT(LAST_DAY(DATE_ADD(STR_TO_DATE(#{ssnd} - 1, '%Y'), INTERVAL (#{ssjd} * 3 - MONTH(STR_TO_DATE(#{ssnd}, '%Y'))) MONTH)),'%Y%m')
    GROUP BY TJZQ
),
hs_agg AS (
    SELECT TJZQ, SUM(DYHSBJ + DYHSLX + DYHSFX + DYHSFL) AS hs_sum 
    FROM dws_bus_ln_rec_mfm 
    WHERE TJZQ >= DATE_FORMAT(LAST_DAY(DATE_ADD(STR_TO_DATE(#{ssnd} - 1, '%Y'), INTERVAL (#{ssjd} * 3 - MONTH(STR_TO_DATE(#{ssnd}, '%Y'))) MONTH)),'%Y%m')
    GROUP BY TJZQ
),
tq_agg AS (
    SELECT TJZQ, SUM(DYTQJE) AS tq_sum 
    FROM dws_bus_ft_per_mfm 
    WHERE TJZQ >= DATE_FORMAT(LAST_DAY(DATE_ADD(STR_TO_DATE(#{ssnd} - 1, '%Y'), INTERVAL (#{ssjd} * 3 - MONTH(STR_TO_DATE(#{ssnd}, '%Y'))) MONTH)),'%Y%m')
    GROUP BY TJZQ
),
fd_agg AS (
    SELECT TJZQ, SUM(DYFDJE) AS fd_sum 
    FROM dws_bus_lend_mfm 
    WHERE TJZQ >= DATE_FORMAT(LAST_DAY(DATE_ADD(STR_TO_DATE(#{ssnd} - 1, '%Y'), INTERVAL (#{ssjd} * 3 - MONTH(STR_TO_DATE(#{ssnd}, '%Y'))) MONTH)),'%Y%m')
    GROUP BY TJZQ
)
SELECT 
    am.month AS sjyf,
    ROUND((COALESCE(gj.gj_sum, 0) + COALESCE(hs.hs_sum, 0)) / 100000000, 2) AS zjlr,
    ROUND((COALESCE(tq.tq_sum, 0) + COALESCE(fd.fd_sum, 0)) / 100000000, 2) AS zjlc,
    ROUND(((COALESCE(gj.gj_sum, 0) + COALESCE(hs.hs_sum, 0)) - (COALESCE(tq.tq_sum, 0) + COALESCE(fd.fd_sum, 0))) / 100000000, 2) AS zjjll
FROM all_months am
LEFT JOIN gj_agg gj ON am.month = gj.TJZQ
LEFT JOIN hs_agg hs ON am.month = hs.TJZQ
LEFT JOIN tq_agg tq ON am.month = tq.TJZQ
LEFT JOIN fd_agg fd ON am.month = fd.TJZQ
ORDER BY am.month
