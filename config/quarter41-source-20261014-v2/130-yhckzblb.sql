SELECT b.YHDM, b.YHDMMC as yhmc, COALESCE(SUM(a.jzye), 0) AS jzye_sum, 	COALESCE(SUM(a.NCYE), 0) AS ncje, 
 	SUM(COALESCE(SUM(a.jzye), 0)) OVER () AS total_jzye, 
 	ROUND((CASE WHEN SUM(COALESCE(SUM(a.jzye), 0)) OVER () = 0 THEN 0 ELSE (COALESCE(SUM(a.jzye), 0) / SUM(COALESCE(SUM(a.jzye), 0)) OVER ()) END * 100), 2 ) as ckzb, 
 	ROUND((CASE WHEN SUM(COALESCE(SUM(a.jzye), 0)) OVER () = 0 THEN 0 ELSE (COALESCE(SUM(a.jzye), 0) / SUM(COALESCE(SUM(a.jzye), 0)) OVER ()) END * 100), 2 ) - 
 	ROUND((CASE WHEN SUM(COALESCE(SUM(a.jzye), 0)) OVER () = 0 THEN 0 ELSE (COALESCE(SUM(a.NCYE), 0) / SUM(COALESCE(SUM(a.jzye), 0)) OVER ()) END * 100), 2 ) as jnczj 
 FROM ods_zjjs_yhzhxx_ye_bf a JOIN ods_zjjs_yhzhxx b ON a.zhxx_id = b.ID 
 where a.bfrq = DATE_FORMAT(LAST_DAY(DATE_ADD(STR_TO_DATE(#{ssnd}, '%Y'), INTERVAL (#{ssjd} * 3 - MONTH(STR_TO_DATE(#{ssnd}, '%Y'))) MONTH)),'%Y-%m-%d') 
 GROUP BY b.YHDM, b.YHDMMC
 HAVING ROUND( (CASE  WHEN SUM(COALESCE(SUM(a.jzye), 0)) OVER () = 0 THEN 0  ELSE (COALESCE(SUM(a.jzye), 0) / SUM(COALESCE(SUM(a.jzye), 0)) OVER ())  END * 100),  2  ) > 10
