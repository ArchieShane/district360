package com.district360.stat.theme;

import com.district360.common.theme.ThemeExtractor;
import com.district360.common.theme.ThemeLoadResult;
import com.district360.common.theme.ThemeLoader;
import com.district360.common.theme.ast.AstRows;
import com.district360.common.theme.ast.AstSink;
import com.district360.stat.mapper.ThemeAstWriteMapper;
import com.district360.stat.mapper.ThemeSpatialWriteMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.district360.stat.theme.ThemeSpatialLoader.step;

/**
 * 主题库 · 设施写入（ods-ast）：一个事务内 清空 ast_facility → 逐行写入 → DMT 公园明细并入所在公园 → 公园面积
 * → 落地块 / 社区 / 区域 → 登记。失败整体回滚。
 */
@Component
public class ThemeAstLoader implements ThemeLoader<AstSink> {

    private static final Logger log = LoggerFactory.getLogger(ThemeAstLoader.class);
    private static final int CHUNK = 500;
    private static final String TABLE = "ast_facility";

    private final ThemeAstWriteMapper mapper;
    private final ThemeSpatialWriteMapper common;
    private final TransactionTemplate tx;

    public ThemeAstLoader(ThemeAstWriteMapper mapper, ThemeSpatialWriteMapper common,
                          @Qualifier("statTransactionTemplate") TransactionTemplate tx) {
        this.mapper = mapper;
        this.common = common;
        this.tx = tx;
    }

    @Override
    public Class<AstSink> sinkType() {
        return AstSink.class;
    }

    @Override
    public ThemeLoadResult load(ThemeExtractor<AstSink> extractor) {
        return tx.execute(status -> {
            common.clear(TABLE);
            List<AstRows.Facility> batch = new ArrayList<>(CHUNK);
            long[] total = {0};
            LocalDate asOf = extractor.extract(row -> {
                batch.add(row);
                total[0]++;
                if (batch.size() >= CHUNK) {
                    mapper.insertFacilities(batch);
                    batch.clear();
                }
            });
            if (!batch.isEmpty()) {
                mapper.insertFacilities(batch);
            }
            log.info("主题库 · 设施：写入 {} 行，开始关联", total[0]);
            common.analyze(TABLE);
            step("DMT 公园明细并入公园", mapper::mergeParkDetails);
            step("公园面积", mapper::parkAreas);
            step("设施 → 地块", mapper::linkPlots);
            step("设施 → 社区 / 区域", mapper::linkAreas);
            long n = common.count(TABLE);
            common.updateMeta(TABLE, n, asOf);
            log.info("主题库 · 设施完成 {} 行", n);
            return new ThemeLoadResult(total[0], asOf, Map.of(TABLE, n));
        });
    }
}
