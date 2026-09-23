SELECT id, template_id, data_item_name, data_item_code,
       data_source_id, data_item_type, data_item_config_type,
       allow_runtime_input, result_type, key_field,
       formula_dependencies, query_sql
FROM zjfk_report_template_data_item
WHERE template_id = 12
  AND del_flag = '0'
  AND id IN (114, 115, 116, 117, 118, 119, 120, 122, 123, 124, 125, 126, 127, 128, 129, 130, 132, 133, 134, 135, 136, 137, 138, 139, 142, 144, 145, 147, 148, 149, 150, 151)
ORDER BY id
