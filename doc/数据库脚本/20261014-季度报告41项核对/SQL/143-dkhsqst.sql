WITH monthly_recovery AS (
    SELECT TRIM(TJZQ) AS month_key, SUM(DYHSBJ) AS recovery_amount
    FROM CAP_GJJ_ETL.DWS_BUS_LN_REC_MFM
    WHERE SUBSTR(TRIM(TJZQ), 1, 4) IN (
        CAST(#{ssnd} AS VARCHAR(4)),
        CAST(CAST(#{ssnd} AS INTEGER) - 1 AS VARCHAR(4))
    )
    GROUP BY TRIM(TJZQ)
)
SELECT CAST(CAST(SUBSTR(current_year.month_key, 5, 2) AS INTEGER) AS VARCHAR(2)) || '月' AS month,
       ROUND(current_year.recovery_amount / 100000000, 2) AS bnhsje,
       ROUND(COALESCE(previous_year.recovery_amount, 0) / 100000000, 2) AS snhsje
FROM monthly_recovery current_year
LEFT JOIN monthly_recovery previous_year
    ON previous_year.month_key = CAST(CAST(#{ssnd} AS INTEGER) - 1 AS VARCHAR(4))
                                 || SUBSTR(current_year.month_key, 5, 2)
WHERE SUBSTR(current_year.month_key, 1, 4) = CAST(#{ssnd} AS VARCHAR(4))
ORDER BY CAST(SUBSTR(current_year.month_key, 5, 2) AS INTEGER)
