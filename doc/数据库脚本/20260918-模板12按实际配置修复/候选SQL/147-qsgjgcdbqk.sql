WITH benji_loan AS (
SELECT sljg AS zdbm, SUM(qmdkye) AS benji_dkje
FROM dws_bus_ln_ned
WHERE LEFT(tjzq, 4) = #{ssnd} AND (
(#{ssjd} = 1 AND SUBSTRING(tjzq, 5, 2) IN ('01', '02', '03'))
 OR (#{ssjd} = 2 AND SUBSTRING(tjzq, 5, 2) IN ('04', '05', '06'))
 OR (#{ssjd} = 3 AND SUBSTRING(tjzq, 5, 2) IN ('07', '08', '09'))
 OR (#{ssjd} = 4 AND SUBSTRING(tjzq, 5, 2) IN ('10', '11', '12'))
)
GROUP BY sljg ), benji_deposit AS (
SELECT ssjg AS zdbm, SUM(qmzgzhye) AS benji_cunkuan
FROM dws_csr_per_ned
WHERE LEFT(tjzq, 4) = #{ssnd} AND (
(#{ssjd} = 1 AND SUBSTRING(tjzq, 5, 2) IN ('01', '02', '03'))
 OR (#{ssjd} = 2 AND SUBSTRING(tjzq, 5, 2) IN ('04', '05', '06'))
 OR (#{ssjd} = 3 AND SUBSTRING(tjzq, 5, 2) IN ('07', '08', '09'))
 OR (#{ssjd} = 4 AND SUBSTRING(tjzq, 5, 2) IN ('10', '11', '12'))
)
GROUP BY ssjg ), benji_cdb AS (
SELECT bl.zdbm, CASE WHEN bd.benji_cunkuan = 0 THEN 0 ELSE bl.benji_dkje / bd.benji_cunkuan END AS benji_cdb
FROM benji_loan bl JOIN benji_deposit bd ON bl.zdbm = bd.zdbm )
SELECT oz.BMMC AS jgmc, ROUND(bc.benji_cdb * 100, 2) AS cdb
FROM benji_cdb bc JOIN ods_cszd_zdtm oz ON bc.zdbm = oz.zdbm and zddm = 'JGDM'
ORDER BY cdb DESC
