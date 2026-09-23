SELECT COUNT(DISTINCT t1.sljg) AS cdbcg95jgs FROM (     SELECT         sljg,         SUM(qmdkye) AS total_loan     FROM dws_bus_ln_ned     WHERE         LEFT(tjzq, 4) = #{ssnd}         AND (
(#{ssjd} = 1 AND SUBSTRING(tjzq, 5, 2) IN ('01', '02', '03'))
 OR (#{ssjd} = 2 AND SUBSTRING(tjzq, 5, 2) IN ('04', '05', '06'))
 OR (#{ssjd} = 3 AND SUBSTRING(tjzq, 5, 2) IN ('07', '08', '09'))
 OR (#{ssjd} = 4 AND SUBSTRING(tjzq, 5, 2) IN ('10', '11', '12'))
)     GROUP BY sljg ) t1 LEFT JOIN (     SELECT         ssjg,         SUM(qmzgzhye) AS total_deposit     FROM dws_csr_per_ned     WHERE         LEFT(tjzq, 4) = #{ssnd}         AND (
(#{ssjd} = 1 AND SUBSTRING(tjzq, 5, 2) IN ('01', '02', '03'))
 OR (#{ssjd} = 2 AND SUBSTRING(tjzq, 5, 2) IN ('04', '05', '06'))
 OR (#{ssjd} = 3 AND SUBSTRING(tjzq, 5, 2) IN ('07', '08', '09'))
 OR (#{ssjd} = 4 AND SUBSTRING(tjzq, 5, 2) IN ('10', '11', '12'))
)     GROUP BY ssjg ) t2 ON t1.sljg = t2.ssjg WHERE CASE     WHEN t2.total_deposit IS NULL OR t2.total_deposit = 0     THEN NULL     ELSE t1.total_loan / t2.total_deposit END > 0.95
