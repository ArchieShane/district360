package com.district360.stat.theme;

import com.district360.common.theme.ThemeExtractor;
import com.district360.common.theme.ThemeLoadResult;
import com.district360.common.theme.ThemeLoader;
import com.district360.common.theme.ops.OpsRows;
import com.district360.common.theme.ops.OpsSink;
import com.district360.stat.mapper.ThemeBizWriteMapper;
import com.district360.stat.mapper.ThemeMktWriteMapper;
import com.district360.stat.mapper.ThemeOpsWriteMapper;
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
 * 主题库 · 城市运行写入（ods-ops）：一个事务内 清空七张表 → 逐行写入 → 落地块 / 社区 / 区域 → 登记行数与各表备注。
 * 居民诉求、道路事故、视觉违规、违规处罚、住宅占用来自业务视图（ods_pop_view），源表为空时表也为空，备注写明原因。
 * 失败整体回滚。
 */
@Component
public class ThemeOpsLoader implements ThemeLoader<OpsSink> {

    private static final Logger log = LoggerFactory.getLogger(ThemeOpsLoader.class);
    private static final int CHUNK = 500;
    static final List<String> ALL = List.of("ops_case", "ops_accident", "ops_distortion", "ops_violation",
            "ops_occupancy_case", "ops_streetlight", "ops_bus_stop");

    private final ThemeOpsWriteMapper mapper;
    private final ThemeSpatialWriteMapper common;
    private final ThemeMktWriteMapper notes;
    private final ThemeBizWriteMapper biz;
    private final TransactionTemplate tx;

    public ThemeOpsLoader(ThemeOpsWriteMapper mapper, ThemeSpatialWriteMapper common, ThemeMktWriteMapper notes,
                          ThemeBizWriteMapper biz, @Qualifier("statTransactionTemplate") TransactionTemplate tx) {
        this.mapper = mapper;
        this.common = common;
        this.biz = biz;
        this.notes = notes;
        this.tx = tx;
    }

    @Override
    public Class<OpsSink> sinkType() {
        return OpsSink.class;
    }

    @Override
    public ThemeLoadResult load(ThemeExtractor<OpsSink> extractor) {
        return tx.execute(status -> {
            ALL.forEach(common::clear);
            Batch<OpsRows.Streetlight> lights = new Batch<>(mapper::insertStreetlights);
            Batch<OpsRows.BusStop> stops = new Batch<>(mapper::insertBusStops);
            Batch<OpsRows.Case> cases = new Batch<>(biz::insertCases);
            Batch<OpsRows.Accident> accidents = new Batch<>(biz::insertAccidents);
            Batch<OpsRows.Distortion> distortions = new Batch<>(biz::insertDistortions);
            Batch<OpsRows.Violation> violations = new Batch<>(biz::insertViolations);
            Batch<OpsRows.OccupancyCase> occupancy = new Batch<>(biz::insertOccupancyCases);
            Map<String, String> sourceNotes = new LinkedHashMap<>();
            LocalDate asOf = extractor.extract(new OpsSink() {
                @Override public void streetlight(OpsRows.Streetlight r) { lights.add(r); }
                @Override public void busStop(OpsRows.BusStop r) { stops.add(r); }
                @Override public void communityCase(OpsRows.Case r) { cases.add(r); }
                @Override public void accident(OpsRows.Accident r) { accidents.add(r); }
                @Override public void distortion(OpsRows.Distortion r) { distortions.add(r); }
                @Override public void violation(OpsRows.Violation r) { violations.add(r); }
                @Override public void occupancyCase(OpsRows.OccupancyCase r) { occupancy.add(r); }
                @Override public void sourceStatus(String themeTable, String note) { sourceNotes.put(themeTable, note); }
            });
            List<Batch<?>> batches = List.of(lights, stops, cases, accidents, distortions, violations, occupancy);
            batches.forEach(Batch::flush);
            long total = batches.stream().mapToLong(b -> b.total).sum();
            log.info("主题库 · 城市运行：写入 {} 行，开始关联", total);
            ALL.forEach(common::analyze);
            step("路灯 → 社区 / 区域", () -> mapper.linkPoints("ops_streetlight"));
            step("公交站台 → 社区 / 区域", () -> mapper.linkPoints("ops_bus_stop"));
            if (cases.total > 0) {
                step("居民诉求 → 地块 / 社区 / 区域（坐标）", () -> biz.linkLocation("ops_case", "geom", true));
            }
            if (accidents.total > 0) {
                step("道路事故 → 社区 / 区域（坐标）", () -> biz.linkLocation("ops_accident", "geom", false));
            }
            if (distortions.total > 0) {
                step("视觉违规 → 地块（gisid）", () -> biz.linkGisid("ops_distortion", "plot_gis_id"));
                step("视觉违规 → 地块（plot_id）", () -> biz.linkPlotId("ops_distortion", "plot_gis_id"));
                step("视觉违规 → 地块（zone + sector + 地块号）",
                        () -> biz.linkZoneSectorPlot("ops_distortion", "zone_name", "sector_name", "plot_number"));
                step("视觉违规 → 地块 / 社区 / 区域（坐标）", () -> biz.linkLocation("ops_distortion", "geom", true));
            }
            if (violations.total > 0) {
                step("违规 → 地块（gisid）", () -> biz.linkGisid("ops_violation", "plot_gis_id"));
                step("违规 → 地块（plot_id）", () -> biz.linkPlotId("ops_violation", "plot_gis_id"));
                step("违规 → 地块（zone + sector + 地块号）",
                        () -> biz.linkZoneSectorPlot("ops_violation", "zone_name", "sector_name", "plot_number"));
                step("违规 → 地块 / 社区 / 区域（坐标）", () -> biz.linkLocation("ops_violation", "geom", true));
            }
            if (occupancy.total > 0) {
                step("住宅占用 → 地块（zone + sector + 地块号）",
                        () -> biz.linkZoneSectorPlot("ops_occupancy_case", "zone_name", "sector_name", "plot_number"));
                step("住宅占用 → 地块 / 社区 / 区域（坐标）", () -> biz.linkLocation("ops_occupancy_case", "geom", true));
            }

            Map<String, Long> counts = new LinkedHashMap<>();
            for (String t : ALL) {
                long n = common.count(t);
                counts.put(t, n);
                common.updateMeta(t, n, asOf);
                notes.updateNote(t, sourceNotes.get(t));
            }
            log.info("主题库 · 城市运行完成 {}，备注 {}", counts, sourceNotes);
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
