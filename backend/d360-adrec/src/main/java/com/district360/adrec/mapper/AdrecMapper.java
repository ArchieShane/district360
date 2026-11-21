package com.district360.adrec.mapper;

import com.district360.common.district.DistrictRef;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** ADREC queries grouped by the supplied Makani district boundaries. */
public interface AdrecMapper {

    SourceDates sourceDates();

    List<SalesRow> salesStats(@Param("districts") List<DistrictRef> districts,
                              @Param("sourceSrid") int sourceSrid,
                              @Param("previousStartDate") LocalDate previousStartDate,
                              @Param("periodStartDate") LocalDate periodStartDate,
                              @Param("periodEndNextDate") LocalDate periodEndNextDate);

    List<MonthlyRow> salesMonthly(@Param("districts") List<DistrictRef> districts,
                                  @Param("sourceSrid") int sourceSrid,
                                  @Param("previousStartDate") LocalDate previousStartDate,
                                  @Param("periodStartDate") LocalDate periodStartDate,
                                  @Param("periodEndNextDate") LocalDate periodEndNextDate);

    List<DistrictCount> mortgageCounts(@Param("districts") List<DistrictRef> districts,
                                       @Param("sourceSrid") int sourceSrid,
                                       @Param("periodStartDate") LocalDate periodStartDate,
                                       @Param("periodEndNextDate") LocalDate periodEndNextDate);

    List<DistrictCount> activeLeaseCounts(@Param("districts") List<DistrictRef> districts,
                                          @Param("sourceSrid") int sourceSrid,
                                          @Param("rentSnapshotDate") LocalDate rentSnapshotDate);

    record SourceDates(LocalDate sourceAsOf, LocalDate rentSnapshotDate) {}

    record SalesRow(String districtCode, BigDecimal salesAed, BigDecimal previousSalesAed,
                    Integer transactions, BigDecimal avgPriceSqm) {}

    record MonthlyRow(String districtCode, LocalDate month, int transactions) {}

    record DistrictCount(String districtCode, Integer count) {}
}
