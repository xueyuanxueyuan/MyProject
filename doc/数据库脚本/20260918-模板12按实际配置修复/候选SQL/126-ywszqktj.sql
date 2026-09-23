WITH finance_period AS (
    SELECT FLOOR(CAST(tjzq AS BIGINT) / 100) AS fiscal_year,
           SUM(COALESCE(dyzzsr, 0)) AS zzsyje,
           SUM(COALESCE(dyzfgjjlxsr, 0) + COALESCE(dyzzsylxsr, 0)
               + COALESCE(dywtdklxsr, 0) + COALESCE(dygjzqlxsr, 0)
               + COALESCE(dyqtsr, 0)) AS ywsrje,
           SUM(COALESCE(dyzfgjjlxzc, 0) + COALESCE(dyzfgjjgjsxfzc, 0)
               + COALESCE(dywtdksxfzc, 0) + COALESCE(dyqtzc, 0)) AS ywzcje
    FROM dws_fin_bal_mem
    WHERE FLOOR(CAST(tjzq AS BIGINT) / 100) IN (#{ssnd}, #{ssnd} - 1)
      AND MOD(CAST(tjzq AS BIGINT), 100)
          BETWEEN (#{ssjd} - 1) * 3 + 1 AND #{ssjd} * 3
    GROUP BY FLOOR(CAST(tjzq AS BIGINT) / 100)
), period_values AS (
    SELECT COALESCE(SUM(CASE WHEN fiscal_year = #{ssnd} THEN zzsyje ELSE 0 END), 0) AS zzsyje,
           COALESCE(SUM(CASE WHEN fiscal_year = #{ssnd} - 1 THEN zzsyje ELSE 0 END), 0) AS qn_zzsyje,
           COALESCE(SUM(CASE WHEN fiscal_year = #{ssnd} THEN ywsrje ELSE 0 END), 0) AS ywsrje,
           COALESCE(SUM(CASE WHEN fiscal_year = #{ssnd} - 1 THEN ywsrje ELSE 0 END), 0) AS qn_ywsrje,
           COALESCE(SUM(CASE WHEN fiscal_year = #{ssnd} THEN ywzcje ELSE 0 END), 0) AS ywzcje,
           COALESCE(SUM(CASE WHEN fiscal_year = #{ssnd} - 1 THEN ywzcje ELSE 0 END), 0) AS qn_ywzcje
    FROM finance_period
)
SELECT ROUND(zzsyje / 100000000, 2) AS "zzsyje",
       ROUND(CASE WHEN qn_zzsyje = 0 THEN 0 ELSE (zzsyje - qn_zzsyje) / qn_zzsyje * 100 END, 2) AS "zzsytbzzl",
       ROUND((qn_zzsyje - zzsyje) / 10000, 2) AS "zzsyjsje",
       ROUND(ywsrje / 100000000, 2) AS "ywsrje",
       ROUND(CASE WHEN qn_ywsrje = 0 THEN 0 ELSE (ywsrje - qn_ywsrje) / qn_ywsrje * 100 END, 2) AS "ywsrtbzzl",
       ROUND(ywzcje / 100000000, 2) AS "ywzcje",
       ROUND(CASE WHEN qn_ywzcje = 0 THEN 0 ELSE (ywzcje - qn_ywzcje) / qn_ywzcje * 100 END, 2) AS "ywzctbzzl"
FROM period_values
