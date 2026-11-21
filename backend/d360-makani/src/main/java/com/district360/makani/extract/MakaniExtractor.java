package com.district360.makani.extract;

import com.district360.common.district.DistrictRef;
import com.district360.common.extract.StatExtractor;
import com.district360.common.extract.snapshot.MakaniSnapshot;
import com.district360.makani.config.MakaniProperties;
import com.district360.makani.mapper.MakaniMapper;
import com.district360.makani.mapper.MakaniTables;
import com.district360.makani.row.MakaniRows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * makani 抽取：区面、社区 / 地块 / 建筑 / 学校 / 医疗 / 清真寺计数、土地利用、17 区建筑体块。
 */
public class MakaniExtractor implements StatExtractor<MakaniSnapshot> {

    private static final Logger log = LoggerFactory.getLogger(MakaniExtractor.class);

    private final MakaniMapper mapper;
    private final MakaniProperties props;

    public MakaniExtractor(MakaniMapper mapper, MakaniProperties props) {
        this.mapper = mapper;
        this.props = props;
    }

    @Override
    public String source() {
        return "makani";
    }

    @Override
    public MakaniSnapshot extract(List<DistrictRef> districts) {
        MakaniTables t = new MakaniTables(props);
        String muni = props.getMunicipality();

        Map<Integer, String> codeById = districts.stream()
                .filter(d -> d.makaniDistrictId() != null)
                .collect(Collectors.toMap(DistrictRef::makaniDistrictId, DistrictRef::code));
        List<Integer> ids = List.copyOf(codeById.keySet());
        if (ids.isEmpty()) {
            throw new IllegalStateException("区字典里没有任何 makaniDistrictId");
        }

        List<MakaniRows.District> base = mapper.districts(t, muni, ids, props.getBoundarySimplifyTolerance());
        Map<Integer, Integer> communities = counts(mapper.communities(t, muni, ids));
        Map<Integer, MakaniRows.Plot> plots = byId(mapper.plots(t, muni, ids), r -> r.makaniId);
        Map<Integer, MakaniRows.BuildingStat> buildingStats = byId(mapper.buildingStats(t, muni, ids), r -> r.makaniId);
        Map<Integer, Integer> schools = counts(mapper.schools(t, muni, ids));
        Map<Integer, Integer> medical = counts(mapper.medical(t, muni, ids, props.getMedicalExcludeNamePatterns()));
        Map<Integer, Integer> mosques = counts(mapper.mosques(t, muni, ids));

        List<MakaniSnapshot.District> out = new ArrayList<>();
        for (MakaniRows.District r : base) {
            MakaniRows.Plot p = plots.getOrDefault(r.makaniId, new MakaniRows.Plot());
            MakaniRows.BuildingStat b = buildingStats.getOrDefault(r.makaniId, new MakaniRows.BuildingStat());
            out.add(new MakaniSnapshot.District(
                    codeById.get(r.makaniId), r.makaniId, r.makaniName, r.nameAr, r.areaKm2,
                    r.centroidLon, r.centroidLat, r.minLon, r.minLat, r.maxLon, r.maxLat, r.boundaryHex, r.scopeHex,
                    communities.getOrDefault(r.makaniId, 0),
                    p.total, p.constructed, p.underConstruction, p.vacantServiced, p.vacantUnserviced,
                    b.total, b.underConstruction,
                    schools.getOrDefault(r.makaniId, 0),
                    medical.getOrDefault(r.makaniId, 0),
                    mosques.getOrDefault(r.makaniId, 0)));
        }
        List<Integer> missing = ids.stream().filter(id -> base.stream().noneMatch(r -> id.equals(r.makaniId))).toList();
        if (!missing.isEmpty()) {
            log.warn("makani 里找不到这些区（{} 市政）：{}", muni, missing);
        }

        List<MakaniSnapshot.LandUse> landUse = mapper.landUse(t, muni, ids).stream()
                .map(r -> new MakaniSnapshot.LandUse(codeById.get(r.makaniId), r.useDesc, r.plotCount))
                .toList();
        List<MakaniSnapshot.Building> buildings = mapper.buildings(t, muni, ids).stream()
                .map(r -> new MakaniSnapshot.Building(codeById.get(r.makaniId), r.buildingId, r.height, r.underConstruction, r.geomHex))
                .toList();

        log.info("makani 抽取完成：{} 个区，{} 条土地利用，{} 栋建筑", out.size(), landUse.size(), buildings.size());
        return new MakaniSnapshot(mapper.plotDataDate(t), out, landUse, buildings);
    }

    private static Map<Integer, Integer> counts(List<MakaniRows.Count> rows) {
        return rows.stream().filter(r -> r.makaniId != null).collect(Collectors.toMap(r -> r.makaniId, r -> r.n));
    }

    private static <T> Map<Integer, T> byId(List<T> rows, Function<T, Integer> id) {
        return rows.stream().filter(r -> Objects.nonNull(id.apply(r))).collect(Collectors.toMap(id, Function.identity()));
    }
}
