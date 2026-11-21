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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * makani 抽取：区列表本身（municipalityname = ADM 的全部区）、区面、社区 / 地块 / 建筑 / 学校 / 医疗 / 清真寺计数、
 * 土地利用、建筑体块。区列表以数据库为准，不再维护配置清单。
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
    public MakaniSnapshot extract(List<DistrictRef> ignored) {
        MakaniTables t = new MakaniTables(props);
        String muni = props.getMunicipality();
        List<Integer> all = List.of(); // 空 = 该市政全部区

        List<MakaniRows.District> base = mapper.districts(t, muni, all, props.getBoundarySimplifyTolerance());
        if (base.isEmpty()) {
            throw new IllegalStateException("makani 里没有 municipalityname = " + muni + " 的区");
        }
        Map<Integer, String> codeById = new HashMap<>();
        Map<String, Integer> seen = new HashMap<>();
        for (MakaniRows.District r : base) {
            String code = code(r.makaniName);
            Integer dup = seen.putIfAbsent(code, r.makaniId);
            if (dup != null) {
                code = code + "-" + r.makaniId; // 极少见：区名生成的编码重复时带上 districtid
            }
            codeById.put(r.makaniId, code);
        }

        Map<Integer, Integer> communities = counts(mapper.communities(t, muni, all));
        Map<Integer, MakaniRows.Plot> plots = byId(mapper.plots(t, muni, all), r -> r.makaniId);
        Map<Integer, MakaniRows.BuildingStat> buildingStats = byId(mapper.buildingStats(t, muni, all), r -> r.makaniId);
        Map<Integer, Integer> schools = counts(mapper.schools(t, muni, all));
        Map<Integer, Integer> medical = counts(mapper.medical(t, muni, all, props.getMedicalExcludeNamePatterns()));
        Map<Integer, Integer> mosques = counts(mapper.mosques(t, muni, all));

        List<MakaniSnapshot.District> out = new ArrayList<>();
        int sortNo = 0;
        for (MakaniRows.District r : base) { // 已按 nameenglish 排序
            MakaniRows.Plot p = plots.getOrDefault(r.makaniId, new MakaniRows.Plot());
            MakaniRows.BuildingStat b = buildingStats.getOrDefault(r.makaniId, new MakaniRows.BuildingStat());
            out.add(new MakaniSnapshot.District(
                    codeById.get(r.makaniId), displayName(r.makaniName), ++sortNo, r.makaniId, r.makaniName, r.nameAr, r.areaKm2,
                    r.centroidLon, r.centroidLat, r.minLon, r.minLat, r.maxLon, r.maxLat, r.boundaryHex, r.scopeHex,
                    communities.getOrDefault(r.makaniId, 0),
                    p.total, p.constructed, p.underConstruction, p.vacantServiced, p.vacantUnserviced,
                    b.total, b.underConstruction,
                    schools.getOrDefault(r.makaniId, 0),
                    medical.getOrDefault(r.makaniId, 0),
                    mosques.getOrDefault(r.makaniId, 0)));
        }

        List<MakaniSnapshot.LandUse> landUse = mapper.landUse(t, muni, all).stream()
                .map(r -> new MakaniSnapshot.LandUse(codeById.get(r.makaniId), r.useDesc, r.plotCount))
                .toList();
        List<MakaniSnapshot.Building> buildings = mapper.buildings(t, muni, all).stream()
                .map(r -> new MakaniSnapshot.Building(codeById.get(r.makaniId), r.buildingId, r.height, r.underConstruction, r.geomHex))
                .toList();

        log.info("makani 抽取完成：{} 个区，{} 条土地利用，{} 栋建筑", out.size(), landUse.size(), buildings.size());
        return new MakaniSnapshot(mapper.plotDataDate(t), out, landUse, buildings);
    }

    /** AL REEM ISLAND → al-reem-island；HADBAT AL ZA`FARANAH → hadbat-al-zafaranah */
    static String code(String makaniName) {
        String s = Objects.requireNonNullElse(makaniName, "").toLowerCase(Locale.ROOT).replaceAll("[`'’]", "");
        return s.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    }

    /** AL REEM ISLAND → Al Reem Island；HADBAT AL ZA`FARANAH → Hadbat Al Za'faranah */
    static String displayName(String makaniName) {
        String s = Objects.requireNonNullElse(makaniName, "").replace('`', '\'').toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(s.length());
        boolean start = true;
        for (char c : s.toCharArray()) {
            if (Character.isLetter(c) && start) {
                out.append(Character.toUpperCase(c));
                start = false;
            } else {
                out.append(c);
                if (c == ' ' || c == '-') {
                    start = true;
                }
            }
        }
        return out.toString().trim();
    }

    private static Map<Integer, Integer> counts(List<MakaniRows.Count> rows) {
        return rows.stream().filter(r -> r.makaniId != null).collect(Collectors.toMap(r -> r.makaniId, r -> r.n));
    }

    private static <T> Map<Integer, T> byId(List<T> rows, Function<T, Integer> id) {
        return rows.stream().filter(r -> Objects.nonNull(id.apply(r))).collect(Collectors.toMap(id, Function.identity()));
    }
}
