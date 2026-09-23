WITH curr_q AS (
SELECT SUM(dyzfgjjlxsr) AS dyzfgjjlxsr, SUM(dyzzsylxsr) AS dyzzsylxsr, SUM(dywtdklxsr) AS dywtdklxsr, SUM(dygjzqlxsr) AS dygjzqlxsr, SUM(dyqtsr) AS dyqtsr, SUM(dyzfgjjlxzc) AS dyzfgjjlxzc, SUM(dywtdksxfzc) AS dywtdksxfzc, SUM(dyqtzc) AS dyqtzc, SUM(dyzzsr) AS dyzzsr
FROM dws_fin_bal_mem
WHERE LEFT(tjzq, 4) = #{ssnd} AND (
(#{ssjd} = 1 AND SUBSTRING(tjzq, 5, 2) IN ('01', '02', '03'))
 OR (#{ssjd} = 2 AND SUBSTRING(tjzq, 5, 2) IN ('04', '05', '06'))
 OR (#{ssjd} = 3 AND SUBSTRING(tjzq, 5, 2) IN ('07', '08', '09'))
 OR (#{ssjd} = 4 AND SUBSTRING(tjzq, 5, 2) IN ('10', '11', '12'))
) ), last_q AS (
SELECT SUM(dyzfgjjlxsr) AS dyzfgjjlxsr, SUM(dyzzsylxsr) AS dyzzsylxsr, SUM(dywtdklxsr) AS dywtdklxsr, SUM(dygjzqlxsr) AS dygjzqlxsr, SUM(dyqtsr) AS dyqtsr, SUM(dyzfgjjlxzc) AS dyzfgjjlxzc, SUM(dywtdksxfzc) AS dywtdksxfzc, SUM(dyqtzc) AS dyqtzc, SUM(dyzzsr) AS dyzzsr
FROM dws_fin_bal_mem
WHERE LEFT(tjzq, 4) = #{ssnd} - 1 AND (
(#{ssjd} = 1 AND SUBSTRING(tjzq, 5, 2) IN ('01', '02', '03'))
 OR (#{ssjd} = 2 AND SUBSTRING(tjzq, 5, 2) IN ('04', '05', '06'))
 OR (#{ssjd} = 3 AND SUBSTRING(tjzq, 5, 2) IN ('07', '08', '09'))
 OR (#{ssjd} = 4 AND SUBSTRING(tjzq, 5, 2) IN ('10', '11', '12'))
) )
SELECT xm, round(bjd / 10000, 2) as bjd, round(tbzde / 10000, 2) as tbzde, concat(CASE WHEN last_bjd = 0 AND bjd = 0 THEN 0 WHEN last_bjd = 0 THEN 100 ELSE ROUND((tbzde / last_bjd) * 100, 2) END, '%') AS tbzdl
FROM (
SELECT '一、业务收入' AS xm, c.dyzfgjjlxsr + c.dyzzsylxsr + c.dywtdklxsr + c.dygjzqlxsr + c.dyqtsr AS bjd, (c.dyzfgjjlxsr + c.dyzzsylxsr + c.dywtdklxsr + c.dygjzqlxsr + c.dyqtsr) - (l.dyzfgjjlxsr + l.dyzzsylxsr + l.dywtdklxsr + l.dygjzqlxsr + l.dyqtsr) AS tbzde, l.dyzfgjjlxsr + l.dyzzsylxsr + l.dywtdklxsr + l.dygjzqlxsr + l.dyqtsr AS last_bjd, 1 as sort_order
FROM curr_q c, last_q l
UNION ALL
SELECT '1.住房公积金利息收入' AS xm, c.dyzfgjjlxsr AS bjd, c.dyzfgjjlxsr - l.dyzfgjjlxsr AS tbzde, l.dyzfgjjlxsr AS last_bjd, 2 as sort_order
FROM curr_q c, last_q l
UNION ALL
SELECT '2.增值收益利息收入' AS xm, c.dyzzsylxsr AS bjd, c.dyzzsylxsr - l.dyzzsylxsr AS tbzde, l.dyzzsylxsr AS last_bjd, 3 as sort_order
FROM curr_q c, last_q l
UNION ALL
SELECT '3.委托贷款利息收入' AS xm, c.dywtdklxsr AS bjd, c.dywtdklxsr - l.dywtdklxsr AS tbzde, l.dywtdklxsr AS last_bjd, 4 as sort_order
FROM curr_q c, last_q l
UNION ALL
SELECT '4.国家债券利息收入' AS xm, c.dygjzqlxsr AS bjd, c.dygjzqlxsr - l.dygjzqlxsr AS tbzde, l.dygjzqlxsr AS last_bjd, 5 as sort_order
FROM curr_q c, last_q l
UNION ALL
SELECT '5.其他收入' AS xm, c.dyqtsr AS bjd, c.dyqtsr - l.dyqtsr AS tbzde, l.dyqtsr AS last_bjd, 6 as sort_order
FROM curr_q c, last_q l
UNION ALL
SELECT '二、业务支出' AS xm, c.dyzfgjjlxzc + c.dywtdksxfzc + c.dyqtzc AS bjd, (c.dyzfgjjlxzc + c.dywtdksxfzc + c.dyqtzc) - (l.dyzfgjjlxzc + l.dywtdksxfzc + l.dyqtzc) AS tbzde, l.dyzfgjjlxzc + l.dywtdksxfzc + l.dyqtzc AS last_bjd, 7 as sort_order
FROM curr_q c, last_q l
UNION ALL
SELECT '1.住房公积金归集手续费支出' AS xm, c.dyzfgjjlxzc AS bjd, c.dyzfgjjlxzc - l.dyzfgjjlxzc AS tbzde, l.dyzfgjjlxzc AS last_bjd, 8 as sort_order
FROM curr_q c, last_q l
UNION ALL
SELECT '2.委托贷款手续费支出' AS xm, c.dywtdksxfzc AS bjd, c.dywtdksxfzc - l.dywtdksxfzc AS tbzde, l.dywtdksxfzc AS last_bjd, 9 as sort_order
FROM curr_q c, last_q l
UNION ALL
SELECT '3.其他支出' AS xm, c.dyqtzc AS bjd, c.dyqtzc - l.dyqtzc AS tbzde, l.dyqtzc AS last_bjd, 10 as sort_order
FROM curr_q c, last_q l
UNION ALL
SELECT '三、增值收益' AS xm, c.dyzzsr AS bjd, c.dyzzsr - l.dyzzsr AS tbzde, l.dyzzsr AS last_bjd, 11 as sort_order
FROM curr_q c, last_q l ) AS temp_result
ORDER BY sort_order
