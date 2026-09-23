select     -1.00 AS "ndjxje",
		coalesce(round(sum(case when indicator_type_code = 'jcje' then predicted_value end) / 100000000, 2), 0) AS "gjje",
		coalesce(round(sum(case when indicator_type_code = 'dkff' then predicted_value end) / 100000000, 2), 0) AS "dkff",
		coalesce(round(sum(case when indicator_type_code = 'dkhs' then predicted_value end) / 100000000, 2), 0) AS "dkhs",
		coalesce(round((sum(case when indicator_type_code = 'tqje' then predicted_value end) + sum(case when indicator_type_code = 'dkff' then predicted_value end)) / 100000000, 2), 0) AS "zfxfje",
		coalesce(round((sum(case when indicator_type_code = 'jcje' then predicted_value end) + sum(case when indicator_type_code = 'dkhs' then predicted_value end)) / 100000000, 2), 0) AS "zjlr",
		coalesce(round((sum(case when indicator_type_code = 'tqje' then predicted_value end) + sum(case when indicator_type_code = 'dkff' then predicted_value end)) / 100000000, 2), 0) AS "zjlc",
		coalesce(round(sum(case when indicator_type_code = 'zjjll' then predicted_value end) / 100000000, 2), 0) AS "zjjll",
		coalesce(sum(case when indicator_type_code = 'cdb' then predicted_value end), 0) AS "yccdb",
		coalesce(round(sum(case when indicator_type_code = 'tqje' then predicted_value end) / 100000000, 2), 0) AS "tqje",
		coalesce(round(sum(case when indicator_type_code = 'jcje' then predicted_value end) / 100000000, 2), 0) AS "jcje",
		coalesce(sum(case when indicator_type_code = 'tql' then predicted_value end), 0) AS "tql",
		coalesce(round(sum(case when indicator_type_code = 'dkye' then predicted_value end) / 100000000, 2), 0) AS "dkye",
		coalesce(sum(case when indicator_type_code = 'cdb' then predicted_value end), 0) AS "cdb",
		coalesce(round(sum(case when indicator_type_code = 'jcje' then predicted_value end) / 100000000, 2), 0) + coalesce(round(sum(case when indicator_type_code = 'dkhs' then predicted_value end) / 100000000, 2), 0) AS "ywsr",
		-1.00 AS "ywsrhb",
		coalesce(round(sum(case when indicator_type_code = 'dkff' then predicted_value end) / 100000000, 2), 0) + coalesce(round(sum(case when indicator_type_code = 'tqje' then predicted_value end) / 100000000, 2), 0) AS "ywzc",
		-1.00 AS "ywzchb",
		-1.00 AS "zzsy",
		-1.00 AS "zzsyhb"
FROM zjfk_prediction_result
WHERE prediction_year = #{ssnd} + CASE WHEN #{ssjd} = 4 THEN 1 ELSE 0 END
  AND del_flag = '0'
  AND prediction_month BETWEEN
      CASE WHEN #{ssjd} = 4 THEN 1 ELSE #{ssjd} * 3 + 1 END
      AND CASE WHEN #{ssjd} = 4 THEN 3 ELSE (#{ssjd} + 1) * 3 END
