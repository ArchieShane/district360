package com.district360.ods.extract;

import com.district360.common.theme.ThemeExtractor;
import com.district360.common.theme.dev.DevSink;
import com.district360.ods.config.OdsProperties;
import com.district360.ods.mapper.OdsDevMapper;
import com.district360.ods.mapper.OdsTables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ODS 开发建设抽取（source = ods-dev）：总体规划项目、总规地块、在建工地。参加每日定时抽取。
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
        return asOf;
    }
}
