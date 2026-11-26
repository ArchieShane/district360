package com.district360.stat.theme;

import com.district360.common.theme.ThemeExtractor;
import com.district360.common.theme.ThemeLoadResult;
import com.district360.common.theme.ThemeLoader;
import com.district360.common.theme.dev.DevRows;
import com.district360.common.theme.dev.DevSink;
import com.district360.stat.mapper.ThemeDevWriteMapper;
import com.district360.stat.mapper.ThemeSpatialWriteMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.district360.stat.theme.ThemeSpatialLoader.step;

/**
 * 主题库 · 开发建设写入（ods-dev）：一个事务内 清空 dev_masterplan / dev_masterplan_plot / dev_construction_site
 * → 逐行写入（每 500 行一批）→ 按 theme.sp_* 关联地块 / 社区 / 区域 → 登记行数。失败整体回滚。
 */
@Component
public class ThemeDevLoader implements ThemeLoader<DevSink> {

    private static final Logger log = LoggerFactory.getLogger(ThemeDevLoader.class);
    private static final int CHUNK = 500;
    static final List<String> TABLES = List.of("dev_masterplan", "dev_masterplan_plot", "dev_construction_site");

    private final ThemeDevWriteMapper mapper;
    private final ThemeSpatialWriteMapper common;
    private final TransactionTemplate tx;

    public ThemeDevLoader(ThemeDevWriteMapper mapper, ThemeSpatialWriteMapper common,
                          @Qualifier("statTransactionTemplate") TransactionTemplate tx) {
        this.mapper = mapper;
        this.common = common;
        this.tx = tx;
    }

    @Override
    public Class<DevSink> sinkType() {
        return DevSink.class;
    }

    @Override
    public ThemeLoadResult load(ThemeExtractor<DevSink> extractor) {
        return tx.execute(status -> {
            TABLES.forEach(common::clear);
            Batch<DevRows.MasterPlan> plans = new Batch<>(mapper::insertMasterPlans);
            Batch<DevRows.MasterPlanPlot> plots = new Batch<>(mapper::insertMasterPlanPlots);
            Batch<DevRows.ConstructionSite> sites = new Batch<>(mapper::insertConstructionSites);
            LocalDate asOf = extractor.extract(new DevSink() {
                @Override public void masterPlan(DevRows.MasterPlan r) { plans.add(r); }
                @Override public void masterPlanPlot(DevRows.MasterPlanPlot r) { plots.add(r); }
                @Override public void constructionSite(DevRows.ConstructionSite r) { sites.add(r); }
            });
            plans.flush();
            plots.flush();
            sites.flush();
            long total = plans.total + plots.total + sites.total;
            log.info("主题库 · 开发建设：写入 {} 行，开始关联", total);
            TABLES.forEach(common::analyze);
            step("总规项目 → 社区 / 区域", mapper::linkMasterPlans);
            step("总规地块 → 地块（gisid）", mapper::linkMasterPlanPlotsByGisid);
            step("总规地块 → 地块（位置）", mapper::linkMasterPlanPlotsByLocation);
            step("总规地块 ← 地块的社区 / 区域", mapper::propagateMasterPlanPlots);
            step("在建工地 → 地块 / 社区", mapper::linkConstructionSites);
            step("在建工地的在建建筑数", mapper::countSiteBuildings);

            Map<String, Long> counts = new LinkedHashMap<>();
            for (String t : TABLES) {
                long n = common.count(t);
                counts.put(t, n);
                common.updateMeta(t, n, asOf);
            }
            log.info("主题库 · 开发建设完成 {}", counts);
            return new ThemeLoadResult(total, asOf, counts);
        });
    }

    private static final class Batch<T> {
        private final Consumer<List<T>> insert;
        private final List<T> rows = new ArrayList<>(CHUNK);
        long total;

        Batch(Consumer<List<T>> insert) {
            this.insert = insert;
        }

        void add(T row) {
            rows.add(row);
            total++;
            if (rows.size() >= CHUNK) {
                flush();
            }
        }

        void flush() {
            if (!rows.isEmpty()) {
                insert.accept(rows);
                rows.clear();
            }
        }
    }
}
