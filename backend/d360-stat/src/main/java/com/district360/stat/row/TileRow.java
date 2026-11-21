package com.district360.stat.row;

/** MVT 瓦片字节（MyBatis 不能把单列 bytea 直接映射成 byte[] 返回值，包一层）。 */
public class TileRow {
    public byte[] mvt;
}
