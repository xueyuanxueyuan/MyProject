select t.jgmc as jgmc, t.zzl
from (
select oc.bmmc as jgmc, round( ( sum(case when left(db.tjzq, 4) = #{ssnd} then db.djsjje else 0 end) / sum(case when left(db.tjzq, 4) = #{ssnd} - 1 then db.djsjje else 0 end) - 1 ) * 100, 2 ) as zzl
from dws_bus_per_paid_qfq db join ods_cszd_zdtm oc on db.ssjg = oc.zdbm and oc.zddm = 'JGDM'
where left(db.tjzq, 4) in (#{ssnd}, #{ssnd} - 1) and (
(#{ssjd} = 1 AND substring(db.tjzq, 5, 1) in (1, 2, 3))
 OR (#{ssjd} = 2 AND substring(db.tjzq, 5, 1) in (4, 5, 6))
 OR (#{ssjd} = 3 AND substring(db.tjzq, 5, 1) in (7, 8, 9))
 OR (#{ssjd} = 4 AND substring(db.tjzq, 5, 2) in (10, 11, 12))
)
group by oc.bmmc ) as t
where t.zzl > 4
