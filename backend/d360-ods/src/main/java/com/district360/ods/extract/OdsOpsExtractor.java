package com.district360.ods.extract;

import com.district360.common.theme.ThemeExtractor;
import com.district360.common.theme.ops.OpsSink;
import com.district360.ods.config.OdsProperties;
import com.district360.ods.mapper.OdsOpsMapper;
import com.district360.ods.mapper.OdsTables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ODS 城市运行抽取（source = ods-ops）：路灯、公交站台。参加每日定时抽取（在 ods-spatial 之后）。
 * 居民诉求、道路事故、视觉违规、违规处罚、住宅占用只检查源表：不存在 / 没有数据 / 已有数据但抽取未实现，
 * 结果写进表登记，源表有数据后再实现对应抽取。手动触发：POST /api/v1/admin/etl/ods-ops。
 */
public class OdsOpsExtractor implements ThemeExtractor<OpsSink> {

    public static final String SOURCE = "ods-ops";

    private static final Logger log = LoggerFactory.getLogger(OdsOpsExtractor.class);

    private final OdsOpsMapper mapper;
    private final OdsTables tables;

    public OdsOpsExtractor(OdsOpsMapper mapper, OdsProperties properties) {
        this.mapper = mapper;
        this.tables = new OdsTables(properties);
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public Class<OpsSink> sinkType() {
        return OpsSink.class;
    }

    @Override
    public LocalDate extract(OpsSink sink) {
        LocalDate asOf = mapper.exists(tables.getStreetlight()) ? mapper.dataDate(tables) : null;
        load(sink, "ops_streetlight", tables.getStreetlight(), n -> mapper.streetlights(tables, ctx -> { sink.streetlight(ctx.getResultObject()); n.incrementAndGet(); }));
        load(sink, "ops_bus_stop", tables.getBusStop(), n -> mapper.busStops(tables, ctx -> { sink.busStop(ctx.getResultObject()); n.incrementAndGet(); }));

        Map<String, String> pending = new LinkedHashMap<>();
        pending.put("ops_case", tables.getAddaCases());
        pending.put("ops_accident", tables.getAccidents());
        pending.put("ops_distortion", tables.getDistortions());
        pending.put("ops_violation", tables.getViolations());
        pending.put("ops_occupancy_case", tables.getOccupancySurvey());
        pending.forEach((theme, src) -> {
            String note = !mapper.exists(src) ? "ODS 无源表：" + src
                    : !mapper.hasRows(src) ? "ODS 源表无数据：" + src
                    : "ODS 源表已有数据，抽取尚未实现：" + src;
            log.info("ODS ops extract: {} -> {}", theme, note);
            sink.sourceStatus(theme, note);
        });
        return asOf;
    }

    private void load(OpsSink sink, String themeTable, String sourceTable, java.util.function.Consumer<AtomicLong> body) {
        if (!mapper.exists(sourceTable)) {
            log.warn("ODS ops extract: source table {} not found, {} skipped", sourceTable, themeTable);
            sink.sourceStatus(themeTable, "ODS 无源表：" + sourceTable);
            return;
        }
        long t0 = System.currentTimeMillis();
        AtomicLong n = new AtomicLong();
        body.accept(n);
        log.info("ODS ops extract: {} -> {} {} rows, {} ms", sourceTable, themeTable, n.get(), System.currentTimeMillis() - t0);
    }
}
