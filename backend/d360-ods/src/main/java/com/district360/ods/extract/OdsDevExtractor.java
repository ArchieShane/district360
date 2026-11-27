package com.district360.ods.extract;

import com.district360.common.theme.ThemeExtractor;
import com.district360.common.theme.dev.DevSink;
import com.district360.ods.config.OdsProperties;
import com.district360.ods.mapper.OdsDevMapper;
import com.district360.ods.mapper.OdsTables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * ODS 开发建设抽取（source = ods-dev）：总体规划项目、总规地块、在建工地（makani），以及业务视图（ods_pop_view）里的
 * 资本项目（GSEC）、建设许可（EMECS）、竣工 / 维修 / 拆除申请（MEPS）。参加每日定时抽取。
 * 业务视图源表不存在或没有数据时跳过，原因写进表登记。
 * 手动触发：POST /api/v1/admin/etl/ods-dev。空间归属在主题库里关联（ThemeDevLoader），所以要在 ods-spatial 之后跑。
 */
public class OdsDevExtractor implements ThemeExtractor<DevSink> {

    public static final String SOURCE = "ods-dev";

    private static final Logger log = LoggerFactory.getLogger(OdsDevExtractor.class);

    private final OdsDevMapper mapper;
    private final OdsTables tables;

    public OdsDevExtractor(OdsDevMapper mapper, OdsProperties properties) {
        this.mapper = mapper;
        this.tables = new OdsTables(properties);
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public Class<DevSink> sinkType() {
        return DevSink.class;
    }

    @Override
    public LocalDate extract(DevSink sink) {
        LocalDate asOf = mapper.dataDate(tables);
        AtomicLong a = new AtomicLong(), b = new AtomicLong(), c = new AtomicLong();
        long t0 = System.currentTimeMillis();
        mapper.masterPlans(tables, ctx -> { sink.masterPlan(ctx.getResultObject()); a.incrementAndGet(); });
        mapper.masterPlanPlots(tables, ctx -> { sink.masterPlanPlot(ctx.getResultObject()); b.incrementAndGet(); });
        mapper.constructionSites(tables, ctx -> { sink.constructionSite(ctx.getResultObject()); c.incrementAndGet(); });
        log.info("ODS dev extract: masterplans {}, masterplan plots {}, construction sites {}, {} ms",
                a.get(), b.get(), c.get(), System.currentTimeMillis() - t0);

        view(sink, "dev_capital_project", List.of(tables.getGsecProjects(), tables.getGsecPoints(), tables.getGsecPolygons()),
                n -> mapper.capitalProjects(tables, ctx -> { sink.capitalProject(ctx.getResultObject()); n.incrementAndGet(); }));
        view(sink, "dev_permit", List.of(tables.getEmecs()),
                n -> mapper.permits(tables, ctx -> { sink.permit(ctx.getResultObject()); n.incrementAndGet(); }));
        view(sink, "dev_building_application", List.of(tables.getMeps()),
                n -> mapper.buildingApplications(tables, ctx -> { sink.buildingApplication(ctx.getResultObject()); n.incrementAndGet(); }));
        return asOf;
    }

    /** 业务视图：任一源表不存在、或主表（第一张）没有数据就跳过并报告。 */
    private void view(DevSink sink, String themeTable, List<String> sources, Consumer<AtomicLong> body) {
        for (String src : sources) {
            if (!mapper.exists(src)) {
                log.warn("ODS dev extract: source table {} not found, {} skipped", src, themeTable);
                sink.sourceStatus(themeTable, "ODS 无源表：" + src);
                return;
            }
        }
        String main = sources.get(0);
        if (!mapper.hasRows(main)) {
            log.info("ODS dev extract: source table {} is empty, {} skipped", main, themeTable);
            sink.sourceStatus(themeTable, "ODS 源表无数据：" + main);
            return;
        }
        long t0 = System.currentTimeMillis();
        AtomicLong n = new AtomicLong();
        body.accept(n);
        sink.sourceStatus(themeTable, null);
        log.info("ODS dev extract: {} -> {} {} rows, {} ms", main, themeTable, n.get(), System.currentTimeMillis() - t0);
    }
}
