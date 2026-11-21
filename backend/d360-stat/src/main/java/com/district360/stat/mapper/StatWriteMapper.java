package com.district360.stat.mapper;

import com.district360.common.extract.snapshot.AdrecSnapshot;
import com.district360.common.extract.snapshot.LiveabilitySnapshot;
import com.district360.common.extract.snapshot.MakaniSnapshot;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/** 统计库写入。只由 {@link com.district360.stat.write.StatWriter} 调用。 */
public interface StatWriteMapper {

    void deleteAllDistricts();

    void insertDistrict(@Param("d") MakaniSnapshot.District district);

    void deleteAllMakani();

    void insertMakani(@Param("list") List<MakaniSnapshot.District> list, @Param("asOf") LocalDate asOf);

    void deleteAllLandUse();

    void insertLandUse(@Param("list") List<MakaniSnapshot.LandUse> list);

    void deleteAllBuildings();

    void insertBuildings(@Param("list") List<MakaniSnapshot.Building> list);

    void deleteAllPlots();

    void insertPlots(@Param("list") List<MakaniSnapshot.Plot> list);

    void deleteAllLiveability();

    void insertLiveability(@Param("list") List<LiveabilitySnapshot.District> list, @Param("asOf") LocalDate asOf);

    void deleteAllAdrec();

    void insertAdrec(@Param("list") List<AdrecSnapshot.District> list, @Param("s") AdrecSnapshot snapshot);

    void deleteAllAdrecMonthly();

    void insertAdrecMonthly(@Param("list") List<AdrecSnapshot.Monthly> list);

    void deleteAllAdrecCommunity();

    void insertAdrecCommunity(@Param("list") List<AdrecSnapshot.CommunityMapping> list);
}
