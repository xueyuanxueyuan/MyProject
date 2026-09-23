WITH params AS (
    SELECT
        CAST(#{ssnd} AS INTEGER) AS report_year,
        CAST(#{ssnd} AS INTEGER) - 1 AS prev_year,
        CAST(#{ssjd} AS INTEGER) AS report_quarter,
        CAST(#{ssnd} AS INTEGER) * 10 + CAST(#{ssjd} AS INTEGER) AS cur_quarter_key,
        (CAST(#{ssnd} AS INTEGER) - 1) * 10 + CAST(#{ssjd} AS INTEGER) AS prev_quarter_key,
        CAST(#{ssnd} AS INTEGER) * 100 + CAST(#{ssjd} AS INTEGER) * 3 AS end_month_key,
        (CAST(#{ssnd} AS INTEGER) - 1) * 100 + CAST(#{ssjd} AS INTEGER) * 3 AS prev_end_month_key,
        (CAST(#{ssnd} AS INTEGER) - 1) * 100 + 12 AS year_start_month_key,
        1 AS start_month_num,
        CAST(#{ssjd} AS INTEGER) * 3 AS end_month_num
    FROM dual
)
, july_int AS (
    SELECT FLOOR(CAST(stl.TJZQ AS BIGINT) / 100) AS report_year, SUM(stl.DRJXJE) AS jxje
    FROM dws_fin_per_stl_int_yfm stl
    CROSS JOIN params p
    WHERE MOD(CAST(stl.TJZQ AS BIGINT), 100) = 7
      AND FLOOR(CAST(stl.TJZQ AS BIGINT) / 100) BETWEEN p.report_year - 3 AND p.report_year
    GROUP BY FLOOR(CAST(stl.TJZQ AS BIGINT) / 100)
)
, gj_cur AS (
    SELECT
        SUM(q.djsjje) AS amount,
        CASE
            WHEN MAX(CASE WHEN MOD(CAST(q.tjzq AS BIGINT), 10) >= 3 THEN 1 ELSE 0 END) = 1 THEN COALESCE(MAX(j.jxje), 0)
            ELSE 0
        END AS jxje
    FROM dws_bus_per_paid_qfq q
    CROSS JOIN params p
    LEFT JOIN july_int j ON j.report_year = p.report_year
    WHERE FLOOR(CAST(q.tjzq AS BIGINT) / 10) = p.report_year
      AND CAST(q.tjzq AS BIGINT) <= p.cur_quarter_key
)
, gj_prev AS (
    SELECT SUM(djsjje) AS amount
    FROM dws_bus_per_paid_qfq q, params p
    WHERE FLOOR(CAST(q.tjzq AS BIGINT) / 10) = p.prev_year
      AND CAST(q.tjzq AS BIGINT) <= p.prev_quarter_key
)
, gj_year_amount AS (
    SELECT
        FLOOR(CAST(q.tjzq AS BIGINT) / 10) AS report_year,
        SUM(q.djsjje) AS amount
    FROM dws_bus_per_paid_qfq q
    CROSS JOIN params p
    WHERE FLOOR(CAST(q.tjzq AS BIGINT) / 10) BETWEEN p.report_year - 4 AND p.report_year
      AND MOD(CAST(q.tjzq AS BIGINT), 10) <= 4
    GROUP BY FLOOR(CAST(q.tjzq AS BIGINT) / 10)
)
, gj_year_avg_growth AS (
    SELECT AVG(growth) AS avg_growth
    FROM (
        SELECT
            cur.report_year,
            CASE
                WHEN prev.amount IS NULL OR prev.amount = 0 THEN NULL
                ELSE (cur.amount - prev.amount) / prev.amount
            END AS growth
        FROM gj_year_amount cur
        LEFT JOIN gj_year_amount prev ON prev.report_year = cur.report_year - 1
        CROSS JOIN params p
        WHERE cur.report_year BETWEEN p.report_year - 3 AND p.report_year - 1
    ) t
)
, tq_cur AS (
    SELECT SUM(dytqje) AS amount
    FROM dws_bus_ft_per_mfm q, params p
    WHERE FLOOR(CAST(q.tjzq AS BIGINT) / 100) = p.report_year
      AND CAST(q.tjzq AS BIGINT) <= p.end_month_key
)
, tq_prev AS (
    SELECT SUM(dytqje) AS amount
    FROM dws_bus_ft_per_mfm q, params p
    WHERE FLOOR(CAST(q.tjzq AS BIGINT) / 100) = p.prev_year
      AND CAST(q.tjzq AS BIGINT) <= p.prev_end_month_key
)
, tq_year_amount AS (
    SELECT
        FLOOR(CAST(q.tjzq AS BIGINT) / 10) AS report_year,
        SUM(q.djtqje) AS amount
    FROM dws_bus_ft_per_qfq q
    CROSS JOIN params p
    WHERE FLOOR(CAST(q.tjzq AS BIGINT) / 10) BETWEEN p.report_year - 4 AND p.report_year
      AND MOD(CAST(q.tjzq AS BIGINT), 10) <= 4
    GROUP BY FLOOR(CAST(q.tjzq AS BIGINT) / 10)
)
, tq_year_avg_growth AS (
    SELECT AVG(growth) AS avg_growth
    FROM (
        SELECT
            cur.report_year,
            CASE
                WHEN prev.amount IS NULL OR prev.amount = 0 THEN NULL
                ELSE (cur.amount - prev.amount) / prev.amount
            END AS growth
        FROM tq_year_amount cur
        LEFT JOIN tq_year_amount prev ON prev.report_year = cur.report_year - 1
        CROSS JOIN params p
        WHERE cur.report_year BETWEEN p.report_year - 3 AND p.report_year - 1
    ) t
)
, loan_cur AS (
    SELECT
        SUM(dyfdje) AS amount,
        SUM(dyfdbs) AS loan_count,
        SUM(CASE WHEN dklx = '04' THEN dyfdje ELSE 0 END) AS amount_szg,
        SUM(CASE WHEN dklx = '04' THEN dyfdbs ELSE 0 END) AS count_szg
    FROM dws_bus_lend_mfm q, params p
    WHERE FLOOR(CAST(q.tjzq AS BIGINT) / 100) = p.report_year
      AND CAST(q.tjzq AS BIGINT) <= p.end_month_key
)
, loan_prev AS (
    SELECT
        SUM(dyfdje) AS amount,
        SUM(dyfdbs) AS loan_count,
        SUM(CASE WHEN dklx = '04' THEN dyfdje ELSE 0 END) AS amount_szg,
        SUM(CASE WHEN dklx = '04' THEN dyfdbs ELSE 0 END) AS count_szg
    FROM dws_bus_lend_mfm q, params p
    WHERE FLOOR(CAST(q.tjzq AS BIGINT) / 100) = p.prev_year
      AND CAST(q.tjzq AS BIGINT) <= p.prev_end_month_key
)
, loan_amount_year AS (
    SELECT
        FLOOR(CAST(q.tjzq AS BIGINT) / 10) AS report_year,
        SUM(q.djfdje) AS amount
    FROM dws_bus_lend_qfq q
    CROSS JOIN params p
    WHERE FLOOR(CAST(q.tjzq AS BIGINT) / 10) BETWEEN p.report_year - 4 AND p.report_year
      AND MOD(CAST(q.tjzq AS BIGINT), 10) <= 4
    GROUP BY FLOOR(CAST(q.tjzq AS BIGINT) / 10)
)
, loan_amount_avg_growth AS (
    SELECT AVG(growth) AS avg_growth
    FROM (
        SELECT
            cur.report_year,
            CASE
                WHEN prev.amount IS NULL OR prev.amount = 0 THEN NULL
                ELSE (cur.amount - prev.amount) / prev.amount
            END AS growth
        FROM loan_amount_year cur
        LEFT JOIN loan_amount_year prev ON prev.report_year = cur.report_year - 1
        CROSS JOIN params p
        WHERE cur.report_year BETWEEN p.report_year - 3 AND p.report_year - 1
    ) t
)
, housing_year_amount AS (
    SELECT report_year, SUM(amount) AS amount
    FROM (
        SELECT
            FLOOR(CAST(q.tjzq AS BIGINT) / 10) AS report_year,
            SUM(q.djtqje) AS amount
        FROM dws_bus_ft_per_qfq q, params p
        WHERE FLOOR(CAST(q.tjzq AS BIGINT) / 10) BETWEEN p.report_year - 4 AND p.report_year
          AND MOD(CAST(q.tjzq AS BIGINT), 10) <= 4
        GROUP BY FLOOR(CAST(q.tjzq AS BIGINT) / 10)
        UNION ALL
        SELECT
            FLOOR(CAST(q.tjzq AS BIGINT) / 10) AS report_year,
            SUM(q.djfdje) AS amount
        FROM dws_bus_lend_qfq q, params p
        WHERE FLOOR(CAST(q.tjzq AS BIGINT) / 10) BETWEEN p.report_year - 4 AND p.report_year
          AND MOD(CAST(q.tjzq AS BIGINT), 10) <= 4
        GROUP BY FLOOR(CAST(q.tjzq AS BIGINT) / 10)
    ) housing
    GROUP BY report_year
)
, housing_avg_growth AS (
    SELECT AVG(growth) AS avg_growth
    FROM (
        SELECT
            cur.report_year,
            CASE
                WHEN prev.amount IS NULL OR prev.amount = 0 THEN NULL
                ELSE (cur.amount - prev.amount) / prev.amount
            END AS growth
        FROM housing_year_amount cur
        LEFT JOIN housing_year_amount prev ON prev.report_year = cur.report_year - 1
        CROSS JOIN params p
        WHERE cur.report_year BETWEEN p.report_year - 3 AND p.report_year - 1
    ) t
)
, loan_repay_cur AS (
    SELECT
        SUM(dyhsbj) AS principal,
        SUM(CASE WHEN hkywlx IN ('02', '03') THEN dyhsbj ELSE 0 END) AS tqhd
    FROM dws_bus_ln_rec_mfm r, params p
    WHERE FLOOR(CAST(r.tjzq AS BIGINT) / 100) = p.report_year
      AND (MOD(CAST(r.tjzq AS BIGINT), 100)) BETWEEN p.start_month_num AND p.end_month_num
)
, loan_repay_prev AS (
    SELECT
        SUM(dyhsbj) AS principal,
        SUM(CASE WHEN hkywlx IN ('02', '03') THEN dyhsbj ELSE 0 END) AS tqhd
    FROM dws_bus_ln_rec_mfm r, params p
    WHERE FLOOR(CAST(r.tjzq AS BIGINT) / 100) = p.prev_year
      AND (MOD(CAST(r.tjzq AS BIGINT), 100)) BETWEEN p.start_month_num AND p.end_month_num
)
, loan_repay_year AS (
    SELECT
        FLOOR(CAST(r.tjzq AS BIGINT) / 100) AS report_year,
        SUM(r.dyhsbj) AS amount
    FROM dws_bus_ln_rec_mfm r
    CROSS JOIN params p
    WHERE FLOOR(CAST(r.tjzq AS BIGINT) / 100) BETWEEN p.report_year - 4 AND p.report_year
      AND MOD(CAST(r.tjzq AS BIGINT), 100) <= 12
    GROUP BY FLOOR(CAST(r.tjzq AS BIGINT) / 100)
)
, loan_repay_avg_growth AS (
    SELECT AVG(growth) AS avg_growth
    FROM (
        SELECT
            cur.report_year,
            CASE
                WHEN prev.amount IS NULL OR prev.amount = 0 THEN NULL
                ELSE (cur.amount - prev.amount) / prev.amount
            END AS growth
        FROM loan_repay_year cur
        LEFT JOIN loan_repay_year prev ON prev.report_year = cur.report_year - 1
        CROSS JOIN params p
        WHERE cur.report_year BETWEEN p.report_year - 3 AND p.report_year - 1
    ) t
)
, deposit_balance_cur AS (
    SELECT SUM(qmzjze) AS balance FROM dws_fin_bal_em, params p
    WHERE kmbh = '201' AND CAST(tjzq AS BIGINT) = p.end_month_key
)
, deposit_balance_start AS (
    SELECT SUM(qmzjze) AS balance FROM dws_fin_bal_em, params p
    WHERE kmbh = '201' AND CAST(tjzq AS BIGINT) = p.year_start_month_key
)
, loan_balance_cur AS (
    SELECT SUM(qmzjze) AS balance FROM dws_fin_bal_em, params p
    WHERE kmbh = '121' AND CAST(tjzq AS BIGINT) = p.end_month_key
)
, loan_balance_start AS (
    SELECT SUM(qmzjze) AS balance FROM dws_fin_bal_em, params p
    WHERE kmbh = '121' AND CAST(tjzq AS BIGINT) = p.year_start_month_key
)
, finance_cur AS (
    SELECT
        SUM(dyzfgjjlxsr) AS zfgjjlxsr,
        SUM(dyzzsylxsr) AS zzsylxsr,
        SUM(dywtdklxsr) AS wtdklxsr,
        SUM(dygjzqlxsr) AS gjzqlxsr,
        SUM(dyqtsr) AS qtsr,
        SUM(dyzfgjjlxzc) AS zfgjjlxzc,
        SUM(dyzfgjjgjsxfzc) AS gjsxfzc,
        SUM(dywtdksxfzc) AS wtdksxfzc,
        SUM(dyqtzc) AS qtzc,
        SUM(dyzzsr) AS zzsy
    FROM dws_fin_bal_mem f, params p
    WHERE FLOOR(CAST(f.tjzq AS BIGINT) / 100) = p.report_year
      AND (MOD(CAST(f.tjzq AS BIGINT), 100)) BETWEEN p.start_month_num AND p.end_month_num
)
SELECT
    ROUND(COALESCE(gj_cur.amount, 0) / 100000000, 2) AS "gjje",
    ROUND(COALESCE(gj_cur.jxje, 0) / 100000000, 2) AS "jxje",
    ROUND(
        CASE WHEN COALESCE(gj_prev.amount, 0) = 0 THEN 0
             ELSE (COALESCE(gj_cur.amount, 0) - gj_prev.amount) / gj_prev.amount * 100
        END,
        2
    ) AS "gjtbzzl",
    ROUND(COALESCE(gj_year_avg_growth.avg_growth, 0) * 100, 2) AS "gjjsnpjzzl",
    ROUND(COALESCE(tq_cur.amount, 0) / 100000000, 2) AS "tqje",
    ROUND(
        CASE WHEN COALESCE(tq_prev.amount, 0) = 0 THEN 0
             ELSE (COALESCE(tq_cur.amount, 0) - tq_prev.amount) / tq_prev.amount * 100
        END,
        2
    ) AS "tqtbzzl",
    ROUND(COALESCE(tq_year_avg_growth.avg_growth, 0) * 100, 2) AS "tqjsnpjzzl",
    ROUND(
        COALESCE(
            COALESCE(tq_cur.amount, 0) / NULLIF(COALESCE(gj_cur.amount, 0), 0) * 100,
            0
        ),
        2
    ) AS "tql",
    ROUND(
        COALESCE(
            (COALESCE(tq_cur.amount, 0) / NULLIF(COALESCE(gj_cur.amount, 0), 0) -
             COALESCE(tq_prev.amount, 0) / NULLIF(COALESCE(gj_prev.amount, 0), 0)) * 100,
            0
        ),
        2
    ) AS "tqtqzb",
    ROUND(COALESCE(deposit_balance_cur.balance, 0) / 100000000, 2) AS "gjye",
    ROUND(COALESCE(loan_cur.amount, 0) / 100000000, 2) AS "dkff",
    ROUND(COALESCE(loan_amount_avg_growth.avg_growth, 0) * 100, 2) AS "dkffjsnpjzzl",
    ROUND(
        CASE WHEN COALESCE(loan_prev.amount, 0) = 0 THEN 0
             ELSE (COALESCE(loan_cur.amount, 0) - loan_prev.amount) / loan_prev.amount * 100
        END,
        2
    ) AS "dkjetbzzl",
    ROUND(COALESCE(loan_repay_cur.principal, 0) / 100000000, 2) AS "dkhs",
    ROUND(
        CASE WHEN COALESCE(loan_repay_prev.principal, 0) = 0 THEN 0
             ELSE (COALESCE(loan_repay_cur.principal, 0) - loan_repay_prev.principal) / loan_repay_prev.principal * 100
        END,
        2
    ) AS "dkhstbzzl",
    ROUND(
        COALESCE(
            COALESCE(loan_repay_cur.tqhd, 0) / NULLIF(COALESCE(loan_repay_cur.principal, 0), 0) * 100,
            0
        ),
        2
    ) AS "tqhdzb",
    ROUND(
        CASE WHEN COALESCE(loan_repay_prev.tqhd, 0) = 0 THEN 0
             ELSE (COALESCE(loan_repay_cur.tqhd, 0) - loan_repay_prev.tqhd) / loan_repay_prev.tqhd * 100
        END,
        2
    ) AS "tqhdzbtb",
    ROUND(COALESCE(loan_repay_avg_growth.avg_growth, 0) * 100, 2) AS "dkhsjsnpjzzl",
    ROUND(COALESCE(loan_balance_cur.balance, 0) / 100000000, 2) AS "dkye",
    ROUND(COALESCE(loan_cur.loan_count, 0), 0) AS "dkffbs",
    ROUND(
        CASE WHEN COALESCE(loan_prev.loan_count, 0) = 0 THEN 0
             ELSE (COALESCE(loan_cur.loan_count, 0) - loan_prev.loan_count) / loan_prev.loan_count * 100
        END,
        2
    ) AS "dkbstbzzl",
    ROUND(
        CASE
            WHEN COALESCE(loan_prev.loan_count, 0) - COALESCE(loan_prev.count_szg, 0) = 0 THEN 0
            ELSE (
                (COALESCE(loan_cur.loan_count, 0) - COALESCE(loan_cur.count_szg, 0)) -
                (COALESCE(loan_prev.loan_count, 0) - COALESCE(loan_prev.count_szg, 0))
            ) / (COALESCE(loan_prev.loan_count, 0) - COALESCE(loan_prev.count_szg, 0)) * 100
        END,
        2
    ) AS "tczhhdkbszzl",
    ROUND(
        CASE
            WHEN COALESCE(loan_prev.amount, 0) - COALESCE(loan_prev.amount_szg, 0) = 0 THEN 0
            ELSE (
                (COALESCE(loan_cur.amount, 0) - COALESCE(loan_cur.amount_szg, 0)) -
                (COALESCE(loan_prev.amount, 0) - COALESCE(loan_prev.amount_szg, 0))
            ) / (COALESCE(loan_prev.amount, 0) - COALESCE(loan_prev.amount_szg, 0)) * 100
        END,
        2
    ) AS "tczhhdkjezzl",
    ROUND(
        (COALESCE(gj_cur.amount, 0) - COALESCE(tq_cur.amount, 0)) / 100000000,
        2
    ) AS "jyje",
    ROUND(
        COALESCE(
            COALESCE(loan_balance_cur.balance, 0) / NULLIF(COALESCE(deposit_balance_cur.balance, 0), 0) * 100,
            0
        ),
        2
    ) AS "cdb",
    ROUND(
        COALESCE(
            COALESCE(loan_balance_cur.balance, 0) / NULLIF(COALESCE(deposit_balance_cur.balance, 0), 0) * 100 -
            COALESCE(loan_balance_start.balance, 0) / NULLIF(COALESCE(deposit_balance_start.balance, 0), 0) * 100,
            0
        ),
        2
    ) AS "cdbbhl",
    ROUND(
        (COALESCE(loan_balance_cur.balance, 0) + COALESCE(deposit_balance_cur.balance, 0)) / 100000000,
        2
    ) AS "cdkgm",
    ROUND(
        (COALESCE(gj_cur.amount, 0) + COALESCE(loan_repay_cur.principal, 0) - COALESCE(gj_cur.jxje, 0)) / 100000000,
        2
    ) AS "zjlr",
    ROUND(
        (COALESCE(tq_cur.amount, 0) + COALESCE(loan_cur.amount, 0)) / 100000000,
        2
    ) AS "zjlc",
    ROUND(
        (COALESCE(gj_cur.amount, 0) + COALESCE(loan_repay_cur.principal, 0) - COALESCE(gj_cur.jxje, 0) -
         COALESCE(tq_cur.amount, 0) - COALESCE(loan_cur.amount, 0)) / 100000000,
        2
    ) AS "zjjll",
    ROUND(COALESCE(loan_cur.amount_szg, 0) / 100000000, 2) AS "szgdkje",
    ROUND(
        (
            COALESCE(tq_cur.amount, 0) +
            COALESCE(loan_cur.amount, 0)
        ) / 100000000,
        2
    ) AS "zfxfe",
    ROUND(
        (
            COALESCE(tq_cur.amount, 0) + COALESCE(loan_cur.amount, 0) -
            COALESCE(tq_prev.amount, 0) - COALESCE(loan_prev.amount, 0)
        ) / 100000000,
        2
    ) AS "zfxftb",
    ROUND(COALESCE(housing_avg_growth.avg_growth, 0) * 100, 2) AS "zfxfjsnpjzzl",
    ROUND(
        (
            COALESCE(finance_cur.zfgjjlxsr, 0) +
            COALESCE(finance_cur.zzsylxsr, 0) +
            COALESCE(finance_cur.wtdklxsr, 0) +
            COALESCE(finance_cur.gjzqlxsr, 0) +
            COALESCE(finance_cur.qtsr, 0)
        ) / 100000000,
        2
    ) AS "ywsr",
    ROUND(
        (
            COALESCE(finance_cur.zfgjjlxzc, 0) +
            COALESCE(finance_cur.gjsxfzc, 0) +
            COALESCE(finance_cur.wtdksxfzc, 0) +
            COALESCE(finance_cur.qtzc, 0)
        ) / 100000000,
        2
    ) AS "ywzc",
    ROUND(COALESCE(finance_cur.zzsy, 0) / 100000000, 2) AS "zzsy"
FROM params
LEFT JOIN gj_cur ON 1 = 1
LEFT JOIN gj_prev ON 1 = 1
LEFT JOIN gj_year_avg_growth ON 1 = 1
LEFT JOIN tq_cur ON 1 = 1
LEFT JOIN tq_prev ON 1 = 1
LEFT JOIN tq_year_avg_growth ON 1 = 1
LEFT JOIN loan_cur ON 1 = 1
LEFT JOIN loan_prev ON 1 = 1
LEFT JOIN loan_amount_avg_growth ON 1 = 1
LEFT JOIN housing_avg_growth ON 1 = 1
LEFT JOIN loan_repay_cur ON 1 = 1
LEFT JOIN loan_repay_prev ON 1 = 1
LEFT JOIN loan_repay_avg_growth ON 1 = 1
LEFT JOIN deposit_balance_cur ON 1 = 1
LEFT JOIN deposit_balance_start ON 1 = 1
LEFT JOIN loan_balance_cur ON 1 = 1
LEFT JOIN loan_balance_start ON 1 = 1
LEFT JOIN finance_cur ON 1 = 1
