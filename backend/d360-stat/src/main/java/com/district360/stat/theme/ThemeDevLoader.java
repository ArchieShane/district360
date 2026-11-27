package com.district360.stat.theme;

import com.district360.common.theme.ThemeExtractor;
import com.district360.common.theme.ThemeLoadResult;
import com.district360.common.theme.ThemeLoader;
import com.district360.common.theme.dev.DevRows;
import com.district360.common.theme.dev.DevSink;
import com.district360.stat.mapper.ThemeBizWriteMapper;
import com.district360.stat.mapper.ThemeDevWriteMapper;
import com.district360.stat.mapper.ThemeMktWriteMapper;
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
 * 主题库 · 开发建设写入（ods-dev）：一个事务内 清空 dev_masterplan / dev_masterplan_plot / dev_construction_site，
 * 以及业务视图来的 dev_capital_project / dev_permit / dev_building_application
 * → 逐行写入（每 500 行一批）→ 按 theme.sp_* 关联地块 / 社区 / 区域 → 登记行数与备注。失败整体回滚。
 */
@Component
public class ThemeDevLoader implements ThemeLoader<DevSink> {

    private static final Logger log = LoggerFactory.getLogger(ThemeDevLoader.class);
    private static final int CHUNK = 500;
    static final List<String> TABLES = List.of("dev_masterplan", "dev_masterplan_plot", "dev_construction_site",
            "dev_capital_project", "dev_permit", "dev_building_application");

    private final ThemeDevWriteMapper mapper;
    private final ThemeSpatialWriteMapper common;
    private final ThemeBizWriteMapper biz;
    private final ThemeMktWriteMapper notes;
    private final TransactionTemplate tx;

    public ThemeDevLoader(ThemeDevWriteMapper mapper, ThemeSpatialWriteMapper common, ThemeBizWriteMapper biz,
                          ThemeMktWriteMapper notes, @Qualifier("statTransactionTemplate") TransactionTemplate tx) {
        this.mapper = mapper;
        this.common = common;
        this.biz = biz;
        this.notes = notes;
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
            Batch<DevRows.CapitalProject> projects = new Batch<>(biz::insertCapitalProjects);
            Batch<DevRows.Permit> permits = new Batch<>(biz::insertPermits);
            Batch<DevRows.BuildingApplication> apps = new Batch<>(biz::insertBuildingApplications);
            Map<String, String> sourceNotes = new LinkedHashMap<>();
            LocalDate asOf = extractor.extract(new DevSink() {
                @Override public void masterPlan(DevRows.MasterPlan r) { plans.add(r); }
                @Override public void masterPlanPlot(DevRows.MasterPlanPlot r) { plots.add(r); }
                @Override public void constructionSite(DevRows.ConstructionSite r) { sites.add(r); }
                @Override public void capitalProject(DevRows.CapitalProject r) { projects.add(r); }
                @Override public void permit(DevRows.Permit r) { permits.add(r); }
                @Override public void buildingApplication(DevRows.BuildingApplication r) { apps.add(r); }
                @Override public void sourceStatus(String themeTable, String note) { sourceNotes.put(themeTable, note); }
            });
            List<Batch<?>> batches = List.of(plans, plots, sites, projects, permits, apps);
            batches.forEach(Batch::flush);
            long total = batches.stream().mapToLong(b -> b.total).sum();
            log.info("主题库 · 开发建设：写入 {} 行，开始关联", total);
            TABLES.forEach(common::analyze);
            step("总规项目 → 社区 / 区域", mapper::linkMasterPlans);
            step("总规地块 → 地块（gisid）", mapper::linkMasterPlanPlotsByGisid);
            step("总规地块 → 地块（位置）", mapper::linkMasterPlanPlotsByLocation);
            step("总规地块 ← 地块的社区 / 区域", mapper::propagateMasterPlanPlots);
            step("在建工地 → 地块 / 社区", mapper::linkConstructionSites);
            step("在建工地的在建建筑数", mapper::countSiteBuildings);
            if (projects.total > 0) {
                step("资本项目 → 社区 / 区域（位置）", () -> biz.linkLocation("dev_capital_project", "centroid", false));
                step("资本项目 → 区域（区名）", biz::linkProjectsByDistrictName);
                step("资本项目完工 / 延期标记", biz::flagProjects);
            }
            if (permits.total > 0) {
                step("建设许可 → 地块（gisid）", () -> biz.linkGisid("dev_permit", "gisid"));
                step("建设许可 → 地块（zone + sector + 地块号）",
                        () -> biz.linkZoneSectorPlot("dev_permit", "zone", "sector", "plot_number"));
                step("建设许可 → 地块 / 社区 / 区域（坐标）", () -> biz.linkLocation("dev_permit", "geom", true));
            }
            if (apps.total > 0) {
                step("建筑类申请 → 地块（zone + sector + 地块号）",
                        () -> biz.linkZoneSectorPlot("dev_building_application", "zone", "sector", "plot_number"));
            }

            Map<String, Long> counts = new LinkedHashMap<>();
            for (String t : TABLES) {
                long n = common.count(t);
                counts.put(t, n);
                common.updateMeta(t, n, asOf);
                if (sourceNotes.containsKey(t)) {
                    notes.updateNote(t, sourceNotes.get(t));
                }
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
