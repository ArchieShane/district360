package com.district360.stat.theme;

import com.district360.common.theme.ThemeExtractor;
import com.district360.common.theme.ThemeLoadResult;
import com.district360.common.theme.ThemeLoader;
import com.district360.common.theme.mkt.MktRows;
import com.district360.common.theme.mkt.MktSink;
import com.district360.stat.mapper.ThemeBizWriteMapper;
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
 * 主题库 · 市场写入（ods-mkt）：一个事务内 清空 mkt_* → 逐行写入 → 按 theme.sp_* 关联 → 登记行数与备注。
 * 源表缺失的主题表在 meta_table.note 里写「源表未导入」，页面据此提示。失败整体回滚。
 */
@Component
public class ThemeMktLoader implements ThemeLoader<MktSink> {

    private static final Logger log = LoggerFactory.getLogger(ThemeMktLoader.class);
    private static final int CHUNK = 500;
    static final List<String> TABLES = List.of("mkt_tenancy", "mkt_sale", "mkt_mortgage", "mkt_rent", "mkt_plot_transaction",
            "mkt_unit_status");

    private final ThemeMktWriteMapper mapper;
    private final ThemeSpatialWriteMapper common;
    private final ThemeBizWriteMapper biz;
    private final TransactionTemplate tx;

    public ThemeMktLoader(ThemeMktWriteMapper mapper, ThemeSpatialWriteMapper common, ThemeBizWriteMapper biz,
                          @Qualifier("statTransactionTemplate") TransactionTemplate tx) {
        this.mapper = mapper;
        this.common = common;
        this.biz = biz;
        this.tx = tx;
    }

    @Override
    public Class<MktSink> sinkType() {
        return MktSink.class;
    }

    @Override
    public ThemeLoadResult load(ThemeExtractor<MktSink> extractor) {
        return tx.execute(status -> {
            TABLES.forEach(common::clear);
            Batch<MktRows.Tenancy> tenancies = new Batch<>(mapper::insertTenancies);
            Batch<MktRows.Sale> sales = new Batch<>(mapper::insertSales);
            Batch<MktRows.Mortgage> mortgages = new Batch<>(mapper::insertMortgages);
            Batch<MktRows.Rent> rents = new Batch<>(mapper::insertRents);
            Batch<MktRows.PlotTransaction> plotTxns = new Batch<>(mapper::insertPlotTransactions);
            Batch<MktRows.UnitStatus> units = new Batch<>(biz::insertUnitStatuses);
            Map<String, List<String>> missing = new LinkedHashMap<>();
            Map<String, String> empty = new LinkedHashMap<>();
            LocalDate asOf = extractor.extract(new MktSink() {
                @Override public void tenancy(MktRows.Tenancy r) { tenancies.add(r); }
                @Override public void sale(MktRows.Sale r) { sales.add(r); }
                @Override public void mortgage(MktRows.Mortgage r) { mortgages.add(r); }
                @Override public void rent(MktRows.Rent r) { rents.add(r); }
                @Override public void plotTransaction(MktRows.PlotTransaction r) { plotTxns.add(r); }
                @Override public void unitStatus(MktRows.UnitStatus r) { units.add(r); }
                @Override public void sourceEmpty(String themeTable, String sourceTable) { empty.put(themeTable, sourceTable); }
                @Override public void sourceMissing(String themeTable, String sourceTable) {
                    missing.computeIfAbsent(themeTable, k -> new ArrayList<>()).add(sourceTable);
                }
            });
            List.of(tenancies, sales, mortgages, rents, plotTxns, units).forEach(Batch::flush);
            long total = tenancies.total + sales.total + mortgages.total + rents.total + plotTxns.total + units.total;
            log.info("主题库 · 市场：写入 {} 行，缺失源表 {}，开始关联", total, missing);
            TABLES.forEach(common::analyze);
            step("Tawtheeq → 地块（gisid）", mapper::linkTenancies);
            step("ADREC 买卖 → 地块 / 建筑（坐标）", () -> mapper.linkByLocation("mkt_sale"));
            step("ADREC 抵押 → 地块 / 建筑（坐标）", () -> mapper.linkByLocation("mkt_mortgage"));
            step("ADREC 租赁 → 地块 / 建筑（坐标）", () -> mapper.linkByLocation("mkt_rent"));
            step("ELMS 地块交易 → 地块", mapper::linkPlotTransactions);
            if (units.total > 0) {
                step("Tawtheeq 单元状态 → 地块（gisid）", () -> biz.linkGisid("mkt_unit_status", "gisid"));
                step("Tawtheeq 单元状态 → BDMS 单元", biz::linkUnitStatuses);
            }

            Map<String, Long> counts = new LinkedHashMap<>();
            for (String t : TABLES) {
                long n = common.count(t);
                counts.put(t, n);
                common.updateMeta(t, n, asOf);
                List<String> src = missing.get(t);
                mapper.updateNote(t, src != null ? "ODS 无源表：" + String.join("、", src)
                        : empty.containsKey(t) ? "ODS 源表无数据：" + empty.get(t) : null);
            }
            log.info("主题库 · 市场完成 {}", counts);
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
