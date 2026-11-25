package com.district360.ods.extract;

import com.district360.common.theme.ThemeExtractor;
import com.district360.common.theme.spatial.SpatialSink;
import com.district360.ods.config.OdsProperties;
import com.district360.ods.mapper.OdsSpatialMapper;
import com.district360.ods.mapper.OdsTables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * ODS 空间下钻抽取（source = ods-spatial）：市政、区域、社区、地块、建筑、楼层、单元逐级流式读出，逐行交给主题库写入端。
 * <p>
 * 只负责读 ODS；清表、写入、上下级关联、汇总、事务都由框架（d360-stat 的 ThemeSpatialLoader）完成。
 * 不捕获异常：抛出即本次失败，主题库保持原样。手动触发：POST /api/v1/admin/etl/ods-spatial。
 */
public class OdsSpatialExtractor implements ThemeExtractor<SpatialSink> {

    public static final String SOURCE = "ods-spatial";

    private static final Logger log = LoggerFactory.getLogger(OdsSpatialExtractor.class);

    private final OdsSpatialMapper mapper;
    private final OdsProperties properties;
    private final OdsTables tables;

    public OdsSpatialExtractor(OdsSpatialMapper mapper, OdsProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
        this.tables = new OdsTables(properties);
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public Class<SpatialSink> sinkType() {
        return SpatialSink.class;
    }

    @Override
    public LocalDate extract(SpatialSink sink) {
        // 先取数据日期：失败时立即失败，不用等全部写完
        LocalDate asOf = mapper.dataDate(tables);
        step("市政", tables.getMunicipality(), c -> mapper.municipalities(tables, properties.getMunicipalityCodes(),
                ctx -> { sink.municipality(ctx.getResultObject()); c.accept(null); }));
        step("区域", tables.getDistrict(), c -> mapper.districts(tables, ctx -> { sink.district(ctx.getResultObject()); c.accept(null); }));
        step("社区", tables.getCommunity(), c -> mapper.communities(tables, ctx -> { sink.community(ctx.getResultObject()); c.accept(null); }));
        step("地块", tables.getPlot(), c -> mapper.plots(tables, ctx -> { sink.plot(ctx.getResultObject()); c.accept(null); }));
        step("建筑", tables.getBuilding(), c -> mapper.buildings(tables, ctx -> { sink.building(ctx.getResultObject()); c.accept(null); }));
        step("楼层", tables.getFloors(), c -> mapper.floors(tables, ctx -> { sink.floor(ctx.getResultObject()); c.accept(null); }));
        step("单元", tables.getUnits(), c -> mapper.units(tables, ctx -> { sink.unit(ctx.getResultObject()); c.accept(null); }));
        return asOf;
    }

    /** 跑一步并记录行数与耗时；counter 每读一行调用一次。 */
    private void step(String name, String table, Consumer<Consumer<Void>> body) {
        long t0 = System.currentTimeMillis();
        AtomicLong n = new AtomicLong();
        body.accept(x -> {
            long v = n.incrementAndGet();
            if (v % 100_000 == 0) {
                log.info("ODS 空间抽取 · {}：已读 {} 行", name, v);
            }
        });
        log.info("ODS 空间抽取 · {}（{}）：{} 行，{} ms", name, table, n.get(), System.currentTimeMillis() - t0);
    }
}
