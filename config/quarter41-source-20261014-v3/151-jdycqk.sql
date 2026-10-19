select     -1.00 as ndjxje,     
		coalesce(round(sum(case when indicator_type_code = 'jcje' then predicted_value end) / 100000000, 2), 0) as gjje,
		coalesce(round(sum(case when indicator_type_code = 'dkff' then predicted_value end) / 100000000, 2), 0) as dkff,
		coalesce(round(sum(case when indicator_type_code = 'dkhs' then predicted_value end) / 100000000, 2), 0) as dkhs,
		coalesce(round((sum(case when indicator_type_code = 'tqje' then predicted_value end) + sum(case when indicator_type_code = 'dkff' then predicted_value end)) / 100000000, 2), 0) as zfxfje,
		coalesce(round((sum(case when indicator_type_code = 'jcje' then predicted_value end) + sum(case when indicator_type_code = 'dkhs' then predicted_value end)) / 100000000, 2), 0) as zjlr,     
		coalesce(round((sum(case when indicator_type_code = 'tqje' then predicted_value end) + sum(case when indicator_type_code = 'dkff' then predicted_value end)) / 100000000, 2), 0) as zjlc,     
		coalesce(round(sum(case when indicator_type_code = 'zjjll' then predicted_value end) / 100000000, 2), 0) as zjjll,
		coalesce(sum(case when indicator_type_code = 'cdb' then predicted_value end), 0) as yccdb,
		coalesce(round(sum(case when indicator_type_code = 'tqje' then predicted_value end) / 100000000, 2), 0) as tqje,
		coalesce(round(sum(case when indicator_type_code = 'jcje' then predicted_value end) / 100000000, 2), 0) as jcje,
		coalesce(sum(case when indicator_type_code = 'tql' then predicted_value end), 0) as tql,
		coalesce(round(sum(case when indicator_type_code = 'dkye' then predicted_value end) / 100000000, 2), 0) as dkye,
		coalesce(sum(case when indicator_type_code = 'cdb' then predicted_value end), 0) as cdb,
		coalesce(round(sum(case when indicator_type_code = 'jcje' then predicted_value end) / 100000000, 2), 0) + coalesce(round(sum(case when indicator_type_code = 'dkhs' then predicted_value end) / 100000000, 2), 0) as ywsr,
		-1.00 as ywsrhb,
		coalesce(round(sum(case when indicator_type_code = 'dkff' then predicted_value end) / 100000000, 2), 0) + coalesce(round(sum(case when indicator_type_code = 'tqje' then predicted_value end) / 100000000, 2), 0) as ywzc,
		-1.00 as ywzchb,
		-1.00 as zzsy,
		-1.00 as zzsyhb  
from     zjfk_prediction_result 
where     prediction_year = #{ssnd}     
and del_flag = '0'     
and case #{ssjd} + 1  when 1 then prediction_month in (1, 2, 3)  
						when 2 then prediction_month in (4, 5, 6)             
						when 3 then prediction_month in (7, 8, 9)             
						when 4 then prediction_month in (10, 11, 12)         
			end 
