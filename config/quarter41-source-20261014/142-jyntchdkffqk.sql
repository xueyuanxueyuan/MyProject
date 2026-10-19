WITH month_list AS (
    SELECT DATE_ADD(STR_TO_DATE(#{ssnd}, '%Y'), INTERVAL (#{ssjd} * 3 - MONTH(STR_TO_DATE(#{ssjd}, '%Y'))) MONTH) AS month_start
    UNION ALL SELECT DATE_SUB(DATE_ADD(STR_TO_DATE(#{ssnd}, '%Y'), INTERVAL (#{ssjd} * 3 - MONTH(STR_TO_DATE(#{ssjd}, '%Y'))) MONTH), INTERVAL 1 MONTH)
    UNION ALL SELECT DATE_SUB(DATE_ADD(STR_TO_DATE(#{ssnd}, '%Y'), INTERVAL (#{ssjd} * 3 - MONTH(STR_TO_DATE(#{ssjd}, '%Y'))) MONTH), INTERVAL 2 MONTH)
    UNION ALL SELECT DATE_SUB(DATE_ADD(STR_TO_DATE(#{ssnd}, '%Y'), INTERVAL (#{ssjd} * 3 - MONTH(STR_TO_DATE(#{ssjd}, '%Y'))) MONTH), INTERVAL 3 MONTH)
    UNION ALL SELECT DATE_SUB(DATE_ADD(STR_TO_DATE(#{ssnd}, '%Y'), INTERVAL (#{ssjd} * 3 - MONTH(STR_TO_DATE(#{ssjd}, '%Y'))) MONTH), INTERVAL 4 MONTH)
    UNION ALL SELECT DATE_SUB(DATE_ADD(STR_TO_DATE(#{ssnd}, '%Y'), INTERVAL (#{ssjd} * 3 - MONTH(STR_TO_DATE(#{ssjd}, '%Y'))) MONTH), INTERVAL 5 MONTH)
    UNION ALL SELECT DATE_SUB(DATE_ADD(STR_TO_DATE(#{ssnd}, '%Y'), INTERVAL (#{ssjd} * 3 - MONTH(STR_TO_DATE(#{ssjd}, '%Y'))) MONTH), INTERVAL 6 MONTH)
    UNION ALL SELECT DATE_SUB(DATE_ADD(STR_TO_DATE(#{ssnd}, '%Y'), INTERVAL (#{ssjd} * 3 - MONTH(STR_TO_DATE(#{ssjd}, '%Y'))) MONTH), INTERVAL 7 MONTH)
    UNION ALL SELECT DATE_SUB(DATE_ADD(STR_TO_DATE(#{ssnd}, '%Y'), INTERVAL (#{ssjd} * 3 - MONTH(STR_TO_DATE(#{ssjd}, '%Y'))) MONTH), INTERVAL 8 MONTH)
    UNION ALL SELECT DATE_SUB(DATE_ADD(STR_TO_DATE(#{ssnd}, '%Y'), INTERVAL (#{ssjd} * 3 - MONTH(STR_TO_DATE(#{ssjd}, '%Y'))) MONTH), INTERVAL 9 MONTH)
    UNION ALL SELECT DATE_SUB(DATE_ADD(STR_TO_DATE(#{ssnd}, '%Y'), INTERVAL (#{ssjd} * 3 - MONTH(STR_TO_DATE(#{ssjd}, '%Y'))) MONTH), INTERVAL 10 MONTH)
    UNION ALL SELECT DATE_SUB(DATE_ADD(STR_TO_DATE(#{ssnd}, '%Y'), INTERVAL (#{ssjd} * 3 - MONTH(STR_TO_DATE(#{ssjd}, '%Y'))) MONTH), INTERVAL 11 MONTH)
)
SELECT DATE_FORMAT(ml.month_start, '%Y%m') AS month, COALESCE(COALESCE(SUM(dkffbs_sum.dyfdbs), 0) - COALESCE(SUM(szg_dkbs.dyfdbs), 0), 0) AS dkffbs,
		COALESCE(COALESCE(SUM(dkffbs_sum.dyfdje), 0) - COALESCE(SUM(szg_dkbs.dyfdje), 0), 0) AS dkffje
FROM month_list ml
LEFT JOIN (
    SELECT STR_TO_DATE(CONCAT(SUBSTRING(tjzq, 1, 4), '-', SUBSTRING(tjzq, 5, 2), '-01'), '%Y-%m-%d') AS month_start,COALESCE(SUM(dyfdbs), 0) AS dyfdbs ,COALESCE(SUM(dyfdje), 0) AS dyfdje 
    FROM dws_bus_lend_mfm
    GROUP BY STR_TO_DATE(CONCAT(SUBSTRING(tjzq, 1, 4), '-', SUBSTRING(tjzq, 5, 2), '-01'), '%Y-%m-%d')
) dkffbs_sum ON ml.month_start = dkffbs_sum.month_start
LEFT JOIN (
    SELECT STR_TO_DATE(CONCAT(SUBSTRING(tjzq, 1, 4), '-', SUBSTRING(tjzq, 5, 2), '-01'), '%Y-%m-%d') AS month_start,COALESCE(SUM(dyfdbs), 0) AS dyfdbs ,COALESCE(SUM(dyfdje), 0) AS dyfdje 
    FROM dws_bus_lend_mfm 
    WHERE dklx = '04'
    GROUP BY STR_TO_DATE(CONCAT(SUBSTRING(tjzq, 1, 4), '-', SUBSTRING(tjzq, 5, 2), '-01'), '%Y-%m-%d')
) szg_dkbs ON ml.month_start = szg_dkbs.month_start
GROUP BY ml.month_start
ORDER BY ml.month_start
