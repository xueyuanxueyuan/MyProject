WITH monthly_balances AS (
    SELECT CAST(SUBSTR(TRIM(TJZQ), 5, 2) AS INTEGER) AS month_num,
           SUM(QMDKYE) AS loan_balance, 0 AS deposit_balance
    FROM CAP_GJJ_ETL.DWS_BUS_LN_NED
    WHERE SUBSTR(TRIM(TJZQ), 1, 4) = CAST(#{ssnd} AS VARCHAR(4))
    GROUP BY CAST(SUBSTR(TRIM(TJZQ), 5, 2) AS INTEGER)
    UNION ALL
    SELECT CAST(SUBSTR(TRIM(TJZQ), 5, 2) AS INTEGER) AS month_num,
           0 AS loan_balance, SUM(QMZGZHYE) AS deposit_balance
    FROM CAP_GJJ_ETL.DWS_CSR_PER_NED
    WHERE SUBSTR(TRIM(TJZQ), 1, 4) = CAST(#{ssnd} AS VARCHAR(4))
    GROUP BY CAST(SUBSTR(TRIM(TJZQ), 5, 2) AS INTEGER)
)
SELECT CAST(month_num AS VARCHAR(2)) || '月' AS month,
       ROUND(SUM(loan_balance) / NULLIF(SUM(deposit_balance), 0) * 100, 2) AS cdb
FROM monthly_balances
GROUP BY month_num
ORDER BY month_num
