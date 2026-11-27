package com.district360.ods.extract;

import com.district360.common.theme.ThemeExtractor;
import com.district360.common.theme.ops.OpsSink;
import com.district360.ods.config.OdsProperties;
import com.district360.ods.mapper.OdsOpsMapper;
import com.district360.ods.mapper.OdsTables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * ODS 城市运行抽取（source = ods-ops）：路灯、公交站台（makani），以及业务视图（ods_pop_view）里的
 * 居民诉求、道路事故、视觉违规、违规处罚、住宅占用。参加每日定时抽取（在 ods-spatial 之后）。
 * 业务视图源表不存在或没有数据时跳过，原因通过 {@link OpsSink#sourceStatus} 写进表登记。
 * 手动触发：POST /api/v1/admin/etl/ods-ops。
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
        load(sink, "ops_streetlight", List.of(tables.getStreetlight()), false,
                n -> mapper.streetlights(tables, ctx -> { sink.streetlight(ctx.getResultObject()); n.incrementAndGet(); }));
        load(sink, "ops_bus_stop", List.of(tables.getBusStop()), false,
                n -> mapper.busStops(tables, ctx -> { sink.busStop(ctx.getResultObject()); n.incrementAndGet(); }));

        load(sink, "ops_case", List.of(tables.getAddaCases()), true,
                n -> mapper.cases(tables, ctx -> { sink.communityCase(ctx.getResultObject()); n.incrementAndGet(); }));
        load(sink, "ops_accident", List.of(tables.getAccidents()), true,
                n -> mapper.accidents(tables, ctx -> { sink.accident(ctx.getResultObject()); n.incrementAndGet(); }));
        load(sink, "ops_distortion", List.of(tables.getDistortions(), tables.getViolationResults()), true,
                n -> mapper.distortions(tables, ctx -> { sink.distortion(ctx.getResultObject()); n.incrementAndGet(); }));
        load(sink, "ops_violation", List.of(tables.getViolations(), tables.getViolationResults()), true,
                n -> mapper.violations(tables, ctx -> { sink.violation(ctx.getResultObject()); n.incrementAndGet(); }));
        if (mapper.exists(tables.getViolationsUis()) && mapper.exists(tables.getViolations())
                && mapper.exists(tables.getViolationResults())) {
            AtomicLong n = new AtomicLong();
            mapper.violationsUis(tables, ctx -> { sink.violation(ctx.getResultObject()); n.incrementAndGet(); });
            log.info("ODS ops extract: {} -> ops_violation {} rows", tables.getViolationsUis(), n.get());
        }
        load(sink, "ops_occupancy_case", List.of(tables.getOccupancySurvey()), true,
                n -> mapper.occupancyCases(tables, ctx -> { sink.occupancyCase(ctx.getResultObject()); n.incrementAndGet(); }));
        return asOf;
    }

    /**
     * @param sources  需要的源表，第一张是主表；任一不存在就跳过
     * @param checkRows 主表没有数据时也跳过并报告（业务视图用）
     */
    private void load(OpsSink sink, String themeTable, List<String> sources, boolean checkRows, Consumer<AtomicLong> body) {
        for (String src : sources) {
            if (!mapper.exists(src)) {
                log.warn("ODS ops extract: source table {} not found, {} skipped", src, themeTable);
                sink.sourceStatus(themeTable, "ODS 无源表：" + src);
                return;
            }
        }
        String main = sources.get(0);
        if (checkRows && !mapper.hasRows(main)) {
            log.info("ODS ops extract: source table {} is empty, {} skipped", main, themeTable);
            sink.sourceStatus(themeTable, "ODS 源表无数据：" + main);
            return;
        }
        long t0 = System.currentTimeMillis();
        AtomicLong n = new AtomicLong();
        body.accept(n);
        sink.sourceStatus(themeTable, null);
        log.info("ODS ops extract: {} -> {} {} rows, {} ms", main, themeTable, n.get(), System.currentTimeMillis() - t0);
    }
}
