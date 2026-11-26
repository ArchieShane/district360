-- makani_masterplan_ud_plot.registered_land_use 最长 163 字符（多个用途拼接），V14 的 varchar(128) 放不下，ods-dev 写入失败。
ALTER TABLE theme.dev_masterplan_plot ALTER COLUMN registered_land_use TYPE varchar(512);
ALTER TABLE theme.dev_masterplan_plot ALTER COLUMN land_use_l2 TYPE varchar(512);
