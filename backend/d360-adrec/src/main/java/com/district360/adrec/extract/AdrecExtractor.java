package com.district360.adrec.extract;

import com.district360.adrec.mapper.AdrecMapper;
import com.district360.adrec.mapper.AdrecMapper.DistrictCount;
import com.district360.adrec.mapper.AdrecMapper.SalesRow;
import com.district360.adrec.mapper.AdrecMapper.SourceDates;
import com.district360.common.district.DistrictRef;
import com.district360.common.extract.StatExtractor;
import com.district360.common.extract.snapshot.AdrecSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds the ADREC snapshot from a consistent read of its source views. */
public class AdrecExtractor implements StatExtractor<AdrecSnapshot> {

    private static final Logger log = LoggerFactory.getLogger(AdrecExtractor.class);
    private static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");
    private final AdrecMapper mapper;
    private final TransactionOperations readTransaction;
    private final int sourceSrid;

    public AdrecExtractor(AdrecMapper mapper, TransactionOperations readTransaction, int sourceSrid) {
        this.mapper = mapper;
        this.readTransaction = readTransaction;
        if (sourceSrid <= 0) {
            throw new IllegalArgumentException("ADREC source SRID must be positive");
        }
        this.sourceSrid = sourceSrid;
    }

    @Override
    public String source() {
        return "adrec";
    }

    @Override
    public AdrecSnapshot extract(List<DistrictRef> districts) {
        List<DistrictRef> scopes = districts.stream()
                .filter(d -> d.scopeEwkbHex() != null && !d.scopeEwkbHex().isBlank())
                .toList();
        if (scopes.isEmpty()) {
            throw new IllegalStateException("No Makani district boundaries are available for ADREC extraction");
        }
        return readTransaction.execute(status -> readSnapshot(scopes));
    }

    private AdrecSnapshot readSnapshot(List<DistrictRef> scopes) {
        long t0 = System.currentTimeMillis();
        SourceDates dates = mapper.sourceDates();
        log.info("ADREC 数据日期：{}，租赁快照日期：{}（{}ms）", dates == null ? null : dates.sourceAsOf(),
                dates == null ? null : dates.rentSnapshotDate(), System.currentTimeMillis() - t0);
        if (dates == null || dates.sourceAsOf() == null) {
            throw new IllegalStateException("No ADREC source ingestion date is available");
        }

        YearMonth periodEnd = YearMonth.now(DUBAI).minusMonths(1);
        YearMonth periodStart = periodEnd.minusMonths(11);
        LocalDate endExclusive = periodEnd.plusMonths(1).atDay(1);
        LocalDate start = periodStart.atDay(1);
        LocalDate previousStart = start.minusYears(1);

        log.info("ADREC 开始抽取：{} 个区，统计期 {} ~ {}，源坐标系 SRID={}", scopes.size(), periodStart, periodEnd, sourceSrid);
        long t = System.currentTimeMillis();
        Map<String, SalesRow> salesByDistrict = new HashMap<>();
        for (SalesRow sale : mapper.salesStats(scopes, sourceSrid, previousStart, start, endExclusive)) {
            salesByDistrict.put(sale.districtCode(), sale);
        }
        log.info("ADREC 成交统计完成：{} 个区有数据（{}ms）", salesByDistrict.size(), System.currentTimeMillis() - t);
        t = System.currentTimeMillis();
        Map<String, Integer> mortgagesByDistrict = byDistrict(
                mapper.mortgageCounts(scopes, sourceSrid, start, endExclusive));
        log.info("ADREC 抵押统计完成：{} 个区有数据（{}ms）", mortgagesByDistrict.size(), System.currentTimeMillis() - t);
        t = System.currentTimeMillis();
        Map<String, Integer> leasesByDistrict = dates.rentSnapshotDate() == null
                ? Map.of()
                : byDistrict(mapper.activeLeaseCounts(scopes, sourceSrid, dates.rentSnapshotDate()));
        log.info("ADREC 有效租约统计完成：{} 个区有数据（{}ms）", leasesByDistrict.size(), System.currentTimeMillis() - t);
        t = System.currentTimeMillis();

        List<AdrecSnapshot.District> summaries = new ArrayList<>();
        for (DistrictRef scope : scopes) {
            String code = scope.code();
            SalesRow sales = salesByDistrict.get(code);
            BigDecimal salesAed = sales == null ? null : sales.salesAed();
            Integer transactions = sales == null ? null : sales.transactions();
            BigDecimal averagePrice = sales == null ? null : sales.avgPriceSqm();
            Integer mortgageCount = mortgagesByDistrict.get(code);
            Integer activeLeaseCount = leasesByDistrict.get(code);
            if (salesAed == null && transactions == null && averagePrice == null
                    && mortgageCount == null && activeLeaseCount == null) {
                continue;
            }
            BigDecimal yearOverYear = sales == null ? null : growth(salesAed, sales.previousSalesAed());
            summaries.add(new AdrecSnapshot.District(
                    code, salesAed, yearOverYear, transactions, mortgageCount, averagePrice, activeLeaseCount));
        }

        List<AdrecSnapshot.Monthly> monthly = mapper.salesMonthly(scopes, sourceSrid, previousStart, start, endExclusive)
                .stream()
                .filter(row -> row.transactions() > 0)
                .map(row -> new AdrecSnapshot.Monthly(row.districtCode(), YearMonth.from(row.month()), row.transactions()))
                .toList();

        log.info("ADREC 月度成交完成：{} 条；抽取结束：{} 个区有汇总，总耗时 {}ms",
                monthly.size(), summaries.size(), System.currentTimeMillis() - t0);
        return new AdrecSnapshot(dates.sourceAsOf(), periodStart, periodEnd, summaries, monthly, List.of());
    }

    private static Map<String, Integer> byDistrict(List<DistrictCount> rows) {
        Map<String, Integer> counts = new HashMap<>();
        for (DistrictCount row : rows) {
            counts.put(row.districtCode(), row.count());
        }
        return counts;
    }

    private static BigDecimal growth(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || current.signum() == 0 || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous).movePointRight(2).divide(previous, 2, RoundingMode.HALF_UP);
    }
}
