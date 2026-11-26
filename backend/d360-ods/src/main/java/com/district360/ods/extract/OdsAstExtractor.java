package com.district360.ods.extract;

import com.district360.common.theme.ThemeExtractor;
import com.district360.common.theme.ast.AstSink;
import com.district360.ods.config.OdsProperties;
import com.district360.ods.mapper.OdsAstMapper;
import com.district360.ods.mapper.OdsTables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * ODS 设施抽取（source = ods-ast）：学校、医疗 / 药房、清真寺、公园（含 DMT 公园明细）。参加每日定时抽取（在 ods-spatial 之后）。
 * 与 v1 区情概览统计同一批 makani 表、同一过滤规则。手动触发：POST /api/v1/admin/etl/ods-ast。
 */
public class OdsAstExtractor implements ThemeExtractor<AstSink> {

    public static final String SOURCE = "ods-ast";

    private static final Logger log = LoggerFactory.getLogger(OdsAstExtractor.class);

    private final OdsAstMapper mapper;
    private final OdsTables tables;

    public OdsAstExtractor(OdsAstMapper mapper, OdsProperties properties) {
        this.mapper = mapper;
        this.tables = new OdsTables(properties);
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public Class<AstSink> sinkType() {
        return AstSink.class;
    }

    @Override
    public LocalDate extract(AstSink sink) {
        LocalDate asOf = mapper.exists(tables.getSchools()) ? mapper.dataDate(tables) : null;
        step("schools", tables.getSchools(), n -> mapper.schools(tables, ctx -> { sink.facility(ctx.getResultObject()); n.incrementAndGet(); }));
        step("medical / pharmacy", tables.getDoh(), n -> mapper.health(tables, ctx -> { sink.facility(ctx.getResultObject()); n.incrementAndGet(); }));
        step("mosques", tables.getMosques(), n -> mapper.mosques(tables, ctx -> { sink.facility(ctx.getResultObject()); n.incrementAndGet(); }));
        step("parks", tables.getParks(), n -> mapper.parks(tables, ctx -> { sink.facility(ctx.getResultObject()); n.incrementAndGet(); }));
        step("park details", tables.getParkDetails(), n -> mapper.parkDetails(tables, ctx -> { sink.facility(ctx.getResultObject()); n.incrementAndGet(); }));
        return asOf;
    }

    private void step(String name, String table, Consumer<AtomicLong> body) {
        if (!mapper.exists(table)) {
            log.warn("ODS facility extract: source table {} not found, {} skipped", table, name);
            return;
        }
        long t0 = System.currentTimeMillis();
        AtomicLong n = new AtomicLong();
        body.accept(n);
        log.info("ODS facility extract: {} ({}) {} rows, {} ms", name, table, n.get(), System.currentTimeMillis() - t0);
    }
}
