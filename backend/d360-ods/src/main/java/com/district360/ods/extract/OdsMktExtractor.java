package com.district360.ods.extract;

import com.district360.common.theme.ThemeExtractor;
import com.district360.common.theme.mkt.MktSink;
import com.district360.ods.config.OdsProperties;
import com.district360.ods.mapper.OdsMktMapper;
import com.district360.ods.mapper.OdsTables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * ODS 市场抽取（source = ods-mkt）：Tawtheeq 租约、ADREC 买卖 / 抵押 / 租赁、ELMS 地块交易、Tawtheeq 单元状态（业务视图 ods_pop_view）。参加每日定时抽取（在 ods-spatial 之后）。
 * 每张源表先检查是否存在：不存在就跳过并告诉写入端（表登记里记「源表未导入」），其余照常抽取。
 * 手动触发：POST /api/v1/admin/etl/ods-mkt。
 */
public class OdsMktExtractor implements ThemeExtractor<MktSink> {

    public static final String SOURCE = "ods-mkt";

    private static final Logger log = LoggerFactory.getLogger(OdsMktExtractor.class);

    private final OdsMktMapper mapper;
    private final OdsTables tables;

    public OdsMktExtractor(OdsMktMapper mapper, OdsProperties properties) {
        this.mapper = mapper;
        this.tables = new OdsTables(properties);
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public Class<MktSink> sinkType() {
        return MktSink.class;
    }

    @Override
    public LocalDate extract(MktSink sink) {
        LocalDate asOf = mapper.exists(tables.getTawtheeq()) ? mapper.dataDate(tables) : null;
        step(sink, "mkt_tenancy", tables.getTawtheeq(), n -> mapper.tenancies(tables, ctx -> { sink.tenancy(ctx.getResultObject()); n.incrementAndGet(); }));
        step(sink, "mkt_sale", tables.getReSale(), n -> mapper.sales(tables, ctx -> { sink.sale(ctx.getResultObject()); n.incrementAndGet(); }));
        step(sink, "mkt_mortgage", tables.getReMortgage(), n -> mapper.mortgages(tables, ctx -> { sink.mortgage(ctx.getResultObject()); n.incrementAndGet(); }));
        step(sink, "mkt_rent", tables.getReRent(), n -> mapper.rents(tables, ctx -> { sink.rent(ctx.getResultObject()); n.incrementAndGet(); }));
        step(sink, "mkt_plot_transaction", tables.getElmsBuySell(), n -> mapper.plotSales(tables, ctx -> { sink.plotTransaction(ctx.getResultObject()); n.incrementAndGet(); }));
        step(sink, "mkt_plot_transaction", tables.getElmsMortgage(), n -> mapper.plotMortgages(tables, ctx -> { sink.plotTransaction(ctx.getResultObject()); n.incrementAndGet(); }));
        if (!mapper.exists(tables.getTawtheeqUnitStatus())) {
            sink.sourceMissing("mkt_unit_status", tables.getTawtheeqUnitStatus());
        } else if (!mapper.hasRows(tables.getTawtheeqUnitStatus())) {
            log.info("ODS market extract: source table {} is empty, mkt_unit_status skipped", tables.getTawtheeqUnitStatus());
            sink.sourceEmpty("mkt_unit_status", tables.getTawtheeqUnitStatus());
        } else {
            step(sink, "mkt_unit_status", tables.getTawtheeqUnitStatus(),
                    n -> mapper.unitStatuses(tables, ctx -> { sink.unitStatus(ctx.getResultObject()); n.incrementAndGet(); }));
        }
        return asOf;
    }

    private void step(MktSink sink, String themeTable, String sourceTable, Consumer<AtomicLong> body) {
        if (!mapper.exists(sourceTable)) {
            log.warn("ODS market extract: source table {} not found, {} skipped", sourceTable, themeTable);
            sink.sourceMissing(themeTable, sourceTable);
            return;
        }
        long t0 = System.currentTimeMillis();
        AtomicLong n = new AtomicLong();
        body.accept(n);
        log.info("ODS market extract: {} -> {} {} rows, {} ms", sourceTable, themeTable, n.get(), System.currentTimeMillis() - t0);
    }
}
