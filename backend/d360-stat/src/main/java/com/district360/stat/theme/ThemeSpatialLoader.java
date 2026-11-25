package com.district360.stat.theme;

import com.district360.common.theme.ThemeExtractor;
import com.district360.common.theme.ThemeLoadResult;
import com.district360.common.theme.ThemeLoader;
import com.district360.common.theme.spatial.SpatialRows;
import com.district360.common.theme.spatial.SpatialSink;
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
import java.util.function.Supplier;

/**
 * 主题库 · 空间下钻写入：一个事务内 清空 sp_* → 抽取器逐行写入（每 500 行一批）→ 在主题库内关联上下级 → 汇总 → 登记。
 * 任何一步失败整体回滚，主题库保持上一次的结果。
 */
@Component
public class ThemeSpatialLoader implements ThemeLoader<SpatialSink> {

    private static final Logger log = LoggerFactory.getLogger(ThemeSpatialLoader.class);
    private static final int CHUNK = 500;

    /** 本主题的表，按删除顺序（下级在前）。 */
    static final List<String> TABLES = List.of(
            "sp_node_stat", "sp_unit", "sp_floor", "sp_building", "sp_plot", "sp_community", "sp_district", "sp_municipality");

    private final ThemeSpatialWriteMapper mapper;
    private final TransactionTemplate tx;

    public ThemeSpatialLoader(ThemeSpatialWriteMapper mapper, @Qualifier("statTransactionTemplate") TransactionTemplate tx) {
        this.mapper = mapper;
        this.tx = tx;
    }

    @Override
    public Class<SpatialSink> sinkType() {
        return SpatialSink.class;
    }

    @Override
    public ThemeLoadResult load(ThemeExtractor<SpatialSink> extractor) {
        return tx.execute(status -> {
            TABLES.forEach(mapper::clear);
            Buffer sink = new Buffer();
            LocalDate asOf = extractor.extract(sink);
            sink.flushAll();
            log.info("主题库 · 空间：写入 {} 行，开始关联", sink.total);

            List.of("sp_municipality", "sp_district", "sp_community", "sp_plot", "sp_building", "sp_floor", "sp_unit")
                    .forEach(mapper::analyze);
            step("楼层表缺的楼层由单元推导", mapper::deriveFloorsFromUnits);
            step("BDMS 建筑代表点", () -> { mapper.createBdmsPoints(); mapper.indexBdmsPoints(); mapper.analyzeBdmsPoints();
                mapper.createUdmFootprints(); mapper.indexUdmFootprints(); mapper.analyzeUdmFootprints(); return null; });
            step("BDMS 建筑匹配 udm 建筑", mapper::matchBdmsBuildings);
            step("未匹配的 BDMS 建筑生成建筑", mapper::insertBdmsBuildings);
            step("市政缺面的由区域合并", mapper::fillMunicipalitiesFromDistricts);
            step("面积", () -> { mapper.fillAreas(); return null; });
            step("社区 → 区域", mapper::linkCommunitiesToDistricts);
            step("地块 → 社区（属性）", mapper::linkPlotsByAttributes);
            step("地块 → 社区（位置）", mapper::linkPlotsToCommunities);
            step("地块 → 区域（无社区）", mapper::linkPlotsToDistricts);
            mapper.analyze("sp_plot");
            mapper.analyze("sp_building");
            step("建筑 → 地块（gisid）", mapper::linkBuildingsByGisid);
            step("建筑 → 地块（位置）", mapper::linkBuildingsByLocation);
            step("建筑 ← 地块的社区 / 区域", mapper::propagateBuildingsFromPlots);
            step("建筑 → 社区（无地块）", mapper::linkBuildingsToCommunities);
            step("楼层 → 建筑", () -> mapper.linkFloorsToBuildings(false));
            step("楼层序号", () -> { mapper.numberFloors(); return null; });
            step("单元 → 楼层", mapper::linkUnitsToFloors);
            step("建筑的楼层 / 单元数", () -> { mapper.fillBuildingCounts(); return null; });
            step("各级汇总", () -> { mapper.computeNodeStats(); return null; });

            Map<String, Long> counts = new LinkedHashMap<>();
            for (String t : List.of("sp_municipality", "sp_district", "sp_community", "sp_plot", "sp_building", "sp_floor", "sp_unit")) {
                long n = mapper.count(t);
                counts.put(t, n);
                mapper.updateMeta(t, n, asOf);
            }
            log.info("主题库 · 空间：完成 {}", counts);
            return new ThemeLoadResult(sink.total, asOf, counts);
        });
    }

    static void step(String name, Supplier<Integer> body) {
        long t0 = System.currentTimeMillis();
        Integer n = body.get();
        log.info("主题库 · 空间 · {}：{}{} ms", name, n == null ? "" : n + " 行，", System.currentTimeMillis() - t0);
    }

    /** 逐行收、满 500 行写一批。 */
    private final class Buffer implements SpatialSink {

        long total;
        private final List<Batch<?>> batches = new ArrayList<>();
        private final Batch<SpatialRows.Municipality> municipalities = batch(mapper::insertMunicipalities);
        private final Batch<SpatialRows.District> districts = batch(mapper::insertDistricts);
        private final Batch<SpatialRows.Community> communities = batch(mapper::insertCommunities);
        private final Batch<SpatialRows.Plot> plots = batch(mapper::insertPlots);
        private final Batch<SpatialRows.Building> buildings = batch(mapper::insertBuildings);
        private final Batch<SpatialRows.Floor> floors = batch(mapper::insertFloors);
        private final Batch<SpatialRows.Unit> units = batch(mapper::insertUnits);

        private <T> Batch<T> batch(Consumer<List<T>> insert) {
            Batch<T> b = new Batch<>(insert);
            batches.add(b);
            return b;
        }

        @Override public void municipality(SpatialRows.Municipality r) { municipalities.add(r); }
        @Override public void district(SpatialRows.District r) { districts.add(r); }
        @Override public void community(SpatialRows.Community r) { communities.add(r); }
        @Override public void plot(SpatialRows.Plot r) { plots.add(r); }
        @Override public void building(SpatialRows.Building r) { buildings.add(r); }
        @Override public void floor(SpatialRows.Floor r) { floors.add(r); }
        @Override public void unit(SpatialRows.Unit r) { units.add(r); }

        void flushAll() {
            batches.forEach(Batch::flush);
        }

        private final class Batch<T> {
            private final Consumer<List<T>> insert;
            private final List<T> rows = new ArrayList<>(CHUNK);

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
}
