select *
from ( WITH current_year_data AS (
SELECT tqlx, SUM(djtqje) AS current_qtr_amount
FROM dws_bus_ft_per_qfq
WHERE SUBSTRING(CAST(tjzq AS VARCHAR), 1, 4) = CAST(#{ssnd} AS VARCHAR) and (
(#{ssjd} = 1 AND substring(tjzq, 5, 1) in (1, 2, 3))
 OR (#{ssjd} = 2 AND substring(tjzq, 5, 1) in (4, 5, 6))
 OR (#{ssjd} = 3 AND substring(tjzq, 5, 1) in (7, 8, 9))
 OR (#{ssjd} = 4 AND substring(tjzq, 5, 2) in (10, 11, 12))
)
GROUP BY tqlx ), prev_year_data AS (
SELECT tqlx, SUM(djtqje) AS prev_qtr_amount
FROM dws_bus_ft_per_qfq
WHERE SUBSTRING(CAST(tjzq AS VARCHAR), 1, 4) = CAST(#{ssnd} - 1 AS VARCHAR) and (
(#{ssjd} = 1 AND substring(tjzq, 5, 1) in (1, 2, 3))
 OR (#{ssjd} = 2 AND substring(tjzq, 5, 1) in (4, 5, 6))
 OR (#{ssjd} = 3 AND substring(tjzq, 5, 1) in (7, 8, 9))
 OR (#{ssjd} = 4 AND substring(tjzq, 5, 2) in (10, 11, 12))
)
GROUP BY tqlx ), total_current AS (
SELECT SUM(current_qtr_amount) AS total_amount
FROM current_year_data ), tqlx_dict AS (
SELECT bmmc, zdbm
FROM ods_cszd_zdtm
WHERE zddm = 'TQLX' ), main_data AS (
SELECT c.current_qtr_amount AS je, d.bmmc as tqsx, ROUND(c.current_qtr_amount / t.total_amount * 100, 2) AS zb, ROUND( CASE WHEN p.prev_qtr_amount = 0 THEN 0 ELSE (c.current_qtr_amount - p.prev_qtr_amount) / p.prev_qtr_amount * 100 END, 2 ) AS tbzzl, 1 as sort_order
FROM current_year_data c CROSS JOIN total_current t
LEFT JOIN prev_year_data p ON c.tqlx = p.tqlx
left JOIN tqlx_dict d ON c.tqlx = d.zdbm )
SELECT tqsx, round(je / 10000, 2) as je, concat(zb, '%') as zb, concat(tbzzl, '%') as tbzzl, sort_order
FROM main_data
UNION ALL
SELECT '汇总' AS tqsx, round(SUM(je) / 10000, 2) AS je, concat(100.00, '%') AS zb, NULL AS tbzzl, 2 as sort_order
FROM main_data
ORDER BY sort_order, je DESC ) as t
order by t.sort_order
