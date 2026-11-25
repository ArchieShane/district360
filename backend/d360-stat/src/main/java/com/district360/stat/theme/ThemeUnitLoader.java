package com.district360.stat.theme;

import com.district360.common.theme.ThemeExtractor;
import com.district360.common.theme.ThemeLoadResult;
import com.district360.common.theme.ThemeLoader;
import com.district360.common.theme.spatial.SpatialRows;
import com.district360.common.theme.spatial.UnitSink;
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

import static com.district360.stat.theme.ThemeSpatialLoader.step;

/**
 * 只同步单元（ods-units），一个事务内：
 * 删单元和上次由单元推导的楼层 → 写入单元 → 补推导楼层 → 新出现的 BDMS 楼匹配 udm 建筑或生成 bdms 建筑 → 删无楼层的 bdms 建筑
 * → 新建筑挂地块 / 社区 → 新楼层挂建筑 → 楼层序号 → 单元挂楼层 → 建筑楼层 / 单元数 → 重算各级汇总。
 * 区域、社区、地块、udm 建筑、楼层表来的楼层都不动。
 */
@Component
public class ThemeUnitLoader implements ThemeLoader<UnitSink> {

    private static final Logger log = LoggerFactory.getLogger(ThemeUnitLoader.class);
    private static final int CHUNK = 500;

    private final ThemeSpatialWriteMapper mapper;
    private final TransactionTemplate tx;

    public ThemeUnitLoader(ThemeSpatialWriteMapper mapper, @Qualifier("statTransactionTemplate") TransactionTemplate tx) {
        this.mapper = mapper;
        this.tx = tx;
    }

    @Override
    public Class<UnitSink> sinkType() {
        return UnitSink.class;
    }

    @Override
    public ThemeLoadResult load(ThemeExtractor<UnitSink> extractor) {
        return tx.execute(status -> {
            if (mapper.legacyFloors() > 0) {
                throw new IllegalStateException("主题库里的楼层还是旧编码（V12 之前，键里没有市政），请先执行一次全量 ods-spatial 抽取");
            }
            mapper.clear("sp_unit");
            step("删除上次由单元推导的楼层", mapper::deleteDerivedFloors);
            List<SpatialRows.Unit> batch = new ArrayList<>(CHUNK);
            long[] total = {0};
            LocalDate asOf = extractor.extract(row -> {
                batch.add(row);
                total[0]++;
                if (batch.size() >= CHUNK) {
                    mapper.insertUnits(batch);
                    batch.clear();
                }
            });
            if (!batch.isEmpty()) {
                mapper.insertUnits(batch);
            }
            log.info("主题库 · 单元：写入 {} 行，开始关联", total[0]);
            mapper.analyze("sp_unit");
            step("楼层表缺的楼层由单元推导", mapper::deriveFloorsFromUnits);
            mapper.analyze("sp_floor");
            step("新 BDMS 建筑代表点", () -> { mapper.createBdmsPoints(); mapper.indexBdmsPoints(); mapper.analyzeBdmsPoints();
                mapper.createUdmFootprints(); mapper.indexUdmFootprints(); mapper.analyzeUdmFootprints(); return null; });
            step("新 BDMS 建筑匹配 udm 建筑", mapper::matchBdmsBuildings);
            step("未匹配的生成 bdms 建筑", mapper::insertBdmsBuildings);
            step("删除无楼层的 bdms 建筑", mapper::deleteOrphanBdmsBuildings);
            mapper.analyze("sp_building");
            step("新建筑 → 地块（位置）", mapper::linkBuildingsByLocation);
            step("新建筑 → 社区（位置）", mapper::linkBuildingsToCommunities);
            step("新楼层 → 建筑", () -> mapper.linkFloorsToBuildings(true));
            step("楼层序号", () -> { mapper.numberFloors(); return null; });
            step("单元 → 楼层", mapper::linkUnitsToFloors);
            step("建筑的楼层 / 单元数", () -> { mapper.fillBuildingCounts(); return null; });
            mapper.clear("sp_node_stat");
            step("各级汇总", () -> { mapper.computeNodeStats(); return null; });

            Map<String, Long> counts = new LinkedHashMap<>();
            for (String t : List.of("sp_building", "sp_floor", "sp_unit")) {
                long n = mapper.count(t);
                counts.put(t, n);
                mapper.updateMeta(t, n, asOf);
            }
            log.info("主题库 · 单元同步完成 {}", counts);
            return new ThemeLoadResult(total[0], asOf, counts);
        });
    }
}
