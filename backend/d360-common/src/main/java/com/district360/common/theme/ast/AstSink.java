package com.district360.common.theme.ast;

import com.district360.common.theme.ThemeSink;

/** 设施主题的写入端（source = ods-ast）：学校、医疗 / 药房、清真寺、公园逐行写入。 */
public interface AstSink extends ThemeSink {

    void facility(AstRows.Facility row);
}
