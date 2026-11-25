package com.district360.ods.extract;

import com.district360.common.theme.ThemeExtractor;
import com.district360.common.theme.spatial.UnitSink;
import com.district360.ods.config.OdsProperties;
import com.district360.ods.mapper.OdsSpatialMapper;
import com.district360.ods.mapper.OdsTables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 只同步单元表（source = ods-units）：流式读 ods_makani_bdms_units_height，取数 SQL 与 ods-spatial 的单元部分相同。
 * 手动触发：POST /api/v1/admin/etl/ods-units。
 */
public class OdsUnitExtractor implements ThemeExtractor<UnitSink> {

    public static final String SOURCE = "ods-units";

    private static final Logger log = LoggerFactory.getLogger(OdsUnitExtractor.class);

    private final OdsSpatialMapper mapper;
    private final OdsTables tables;

    public OdsUnitExtractor(OdsSpatialMapper mapper, OdsProperties properties) {
        this.mapper = mapper;
        this.tables = new OdsTables(properties);
    }

    @Override
    public String source() {
        return SOURCE;
    }

    /** 局部同步：只手动触发，不参加每日定时抽取（全量 ods-spatial 已含单元）。 */
    @Override
    public boolean scheduled() {
        return false;
    }

    @Override
    public Class<UnitSink> sinkType() {
        return UnitSink.class;
    }

    @Override
    public LocalDate extract(UnitSink sink) {
        LocalDate asOf = mapper.dataDate(tables);
        long t0 = System.currentTimeMillis();
        AtomicLong n = new AtomicLong();
        mapper.units(tables, ctx -> {
            sink.unit(ctx.getResultObject());
            if (n.incrementAndGet() % 100_000 == 0) {
                log.info("ODS units sync: {} rows read", n.get());
            }
        });
        log.info("ODS units sync ({}): {} rows, {} ms", tables.getUnits(), n.get(), System.currentTimeMillis() - t0);
        return asOf;
    }
}
