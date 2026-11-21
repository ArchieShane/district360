package com.district360.adrec.extract;

import com.district360.adrec.mapper.AdrecMapper;
import com.district360.adrec.mapper.AdrecMapper.DistrictCount;
import com.district360.adrec.mapper.AdrecMapper.SalesRow;
import com.district360.adrec.mapper.AdrecMapper.SourceDates;
import com.district360.common.district.DistrictRef;
import com.district360.common.extract.StatExtractor;
import com.district360.common.extract.snapshot.AdrecSnapshot;
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
        SourceDates dates = mapper.sourceDates();
        if (dates == null || dates.sourceAsOf() == null) {
            throw new IllegalStateException("No ADREC source ingestion date is available");
        }

        YearMonth periodEnd = YearMonth.now(DUBAI).minusMonths(1);
        YearMonth periodStart = periodEnd.minusMonths(11);
        LocalDate endExclusive = periodEnd.plusMonths(1).atDay(1);
        LocalDate start = periodStart.atDay(1);
        LocalDate previousStart = start.minusYears(1);

        Map<String, SalesRow> salesByDistrict = new HashMap<>();
        for (SalesRow sale : mapper.salesStats(scopes, sourceSrid, previousStart, start, endExclusive)) {
            salesByDistrict.put(sale.districtCode(), sale);
        }
        Map<String, Integer> mortgagesByDistrict = byDistrict(
                mapper.mortgageCounts(scopes, sourceSrid, start, endExclusive));
        Map<String, Integer> leasesByDistrict = dates.rentSnapshotDate() == null
                ? Map.of()
                : byDistrict(mapper.activeLeaseCounts(scopes, sourceSrid, dates.rentSnapshotDate()));

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
