WITH CurrentYearData AS (
SELECT ssjg, SUM(DJSJJE) AS current_year_amount
FROM dws_bus_per_paid_qfq
WHERE left(TJZQ, 4) = #{ssnd} and (
(#{ssjd} = 1 AND substring(tjzq, 5, 1) in (1, 2, 3))
 OR (#{ssjd} = 2 AND substring(tjzq, 5, 1) in (4, 5, 6))
 OR (#{ssjd} = 3 AND substring(tjzq, 5, 1) in (7, 8, 9))
 OR (#{ssjd} = 4 AND substring(tjzq, 5, 2) in (10, 11, 12))
)
GROUP BY ssjg ), LastYearData AS (
SELECT ssjg, SUM(DJSJJE) AS last_year_amount
FROM dws_bus_per_paid_qfq
WHERE left(TJZQ, 4) = #{ssnd} - 1 and (
(#{ssjd} = 1 AND substring(tjzq, 5, 1) in (1, 2, 3))
 OR (#{ssjd} = 2 AND substring(tjzq, 5, 1) in (4, 5, 6))
 OR (#{ssjd} = 3 AND substring(tjzq, 5, 1) in (7, 8, 9))
 OR (#{ssjd} = 4 AND substring(tjzq, 5, 2) in (10, 11, 12))
)
GROUP BY ssjg ), OrgName AS (
SELECT zdbm AS ssjg, bmmc AS jgmc
FROM ods_cszd_zdtm
WHERE zddm = 'JGDM' )
SELECT o.jgmc, ROUND(COALESCE(c.current_year_amount, 0) / 10000, 2) AS gjje, CASE WHEN l.last_year_amount = 0 THEN 0 ELSE ROUND(((COALESCE(c.current_year_amount, 0) - COALESCE(l.last_year_amount, 0)) / COALESCE(l.last_year_amount, 1)) * 100, 2) END AS tbzdl, ROUND(COALESCE(c.current_year_amount - COALESCE(l.last_year_amount, 0), 0) / 10000, 2) AS tbzdje
FROM OrgName o
LEFT JOIN CurrentYearData c ON o.ssjg = c.ssjg
LEFT JOIN LastYearData l ON o.ssjg = l.ssjg
UNION ALL
SELECT '合计', SUM(COALESCE(c.current_year_amount, 0)), CASE WHEN SUM(COALESCE(l.last_year_amount, 0)) = 0 THEN 0 ELSE ROUND(((SUM(COALESCE(c.current_year_amount, 0)) - SUM(COALESCE(l.last_year_amount, 0))) / SUM(COALESCE(l.last_year_amount, 1))) * 100, 2) END, SUM(COALESCE(c.current_year_amount, 0)) - SUM(COALESCE(l.last_year_amount, 0))
FROM OrgName o
LEFT JOIN CurrentYearData c ON o.ssjg = c.ssjg
LEFT JOIN LastYearData l ON o.ssjg = l.ssjg
