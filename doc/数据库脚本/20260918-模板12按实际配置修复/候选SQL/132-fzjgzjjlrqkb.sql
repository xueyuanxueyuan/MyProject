SELECT z.bmmc AS jgmc, COALESCE(gj_sum, 0) + COALESCE(hs_sum, 0) AS lrje, COALESCE(tq_sum, 0) + COALESCE(fd_sum, 0) AS zjlc, (COALESCE(gj_sum, 0) + COALESCE(hs_sum, 0)) - (COALESCE(tq_sum, 0) + COALESCE(fd_sum, 0)) AS zjjll
FROM ods_cszd_zdtm z
LEFT JOIN (
SELECT ssjg, SUM(DJSJJE) AS gj_sum
FROM dws_bus_per_paid_qfq
WHERE left(tjzq, 4) = #{ssnd} and (
(#{ssjd} = 1 AND substring(tjzq, 5, 1) in (1, 2, 3))
 OR (#{ssjd} = 2 AND substring(tjzq, 5, 1) in (4, 5, 6))
 OR (#{ssjd} = 3 AND substring(tjzq, 5, 1) in (7, 8, 9))
 OR (#{ssjd} = 4 AND substring(tjzq, 5, 2) in (10, 11, 12))
)
GROUP BY ssjg ) gj ON z.zdbm = gj.ssjg AND z.zddm = 'JGDM'
LEFT JOIN (
SELECT ssjg, SUM(DYHSBJ) AS hs_sum
FROM dws_bus_ln_rec_mfm
WHERE left(tjzq, 4) = #{ssnd} and (
(#{ssjd} = 1 AND substring(tjzq, 5, 2) in ('01', '02', '03'))
 OR (#{ssjd} = 2 AND substring(tjzq, 5, 2) in ('04', '05', '06'))
 OR (#{ssjd} = 3 AND substring(tjzq, 5, 2) in ('07', '08', '09'))
 OR (#{ssjd} = 4 AND substring(tjzq, 5, 2) in ('10', '11', '12'))
)
GROUP BY ssjg ) hs ON z.zdbm = hs.ssjg
LEFT JOIN (
SELECT ssjg, SUM(DJTQJE) AS tq_sum
FROM dws_bus_ft_per_qfq
WHERE left(tjzq, 4) = #{ssnd} and (
(#{ssjd} = 1 AND substring(tjzq, 5, 1) in (1, 2, 3))
 OR (#{ssjd} = 2 AND substring(tjzq, 5, 1) in (4, 5, 6))
 OR (#{ssjd} = 3 AND substring(tjzq, 5, 1) in (7, 8, 9))
 OR (#{ssjd} = 4 AND substring(tjzq, 5, 2) in (10, 11, 12))
)
GROUP BY ssjg ) tq ON z.zdbm = tq.ssjg
LEFT JOIN (
SELECT ssjg, SUM(DJFDJE) AS fd_sum
FROM dws_bus_lend_qfq
WHERE left(tjzq, 4) = #{ssnd} and (
(#{ssjd} = 1 AND substring(tjzq, 5, 2) in ('01', '02', '03'))
 OR (#{ssjd} = 2 AND substring(tjzq, 5, 2) in ('04', '05', '06'))
 OR (#{ssjd} = 3 AND substring(tjzq, 5, 2) in ('07', '08', '09'))
 OR (#{ssjd} = 4 AND substring(tjzq, 5, 2) in ('10', '11', '12'))
)
GROUP BY ssjg ) fd ON z.zdbm = fd.ssjg
WHERE z.zddm = 'JGDM'
GROUP BY z.bmmc, z.zdbm, gj_sum, hs_sum, tq_sum, fd_sum
ORDER BY zjjll desc
