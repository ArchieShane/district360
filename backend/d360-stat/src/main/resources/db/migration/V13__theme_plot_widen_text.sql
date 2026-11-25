-- makani_1119 全量地块（41 万）里 devcode_far 最长 150 字符（多个容积率的说明文字），原 varchar(32) 放不下，导致 ods-spatial 失败。
-- 同时放宽长度接近上限的管控类文本列。
ALTER TABLE theme.sp_plot ALTER COLUMN devcode_far TYPE varchar(512);
ALTER TABLE theme.sp_plot ALTER COLUMN max_heights TYPE varchar(128);
ALTER TABLE theme.sp_plot ALTER COLUMN devcode TYPE varchar(128);
