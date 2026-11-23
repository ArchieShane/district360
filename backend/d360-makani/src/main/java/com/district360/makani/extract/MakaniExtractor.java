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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * makani 抽取：区列表本身（udm_district 全部区，所有市政）、区面、社区 / 地块 / 建筑 / 学校 / 医疗 / 清真寺计数、
 * 土地利用、建筑体块。区列表以数据库为准，不维护配置清单。
 */
public class MakaniExtractor implements StatExtractor<MakaniSnapshot> {

    private static final Logger log = LoggerFactory.getLogger(MakaniExtractor.class);

    /** 按区名排序：忽略大小写和撇号等符号，同名时按市政 */
    private static final Comparator<MakaniRows.District> BY_NAME = Comparator
            .comparing((MakaniRows.District r) -> sortKey(r.makaniName))
            .thenComparing(r -> Objects.requireNonNullElse(r.municipality, ""));

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
        log.info("makani 源表：{}、{}、{}、{}（schema = {}，前缀 = '{}'）",
                t.getDistrict(), t.getPlot(), t.getBuilding(), t.getCommunity(), props.getSchema(), props.getTablePrefix());

        List<MakaniRows.District> base = new ArrayList<>(mapper.districts(t, props.getBoundarySimplifyTolerance()));
        if (base.isEmpty()) {
            throw new IllegalStateException("makani udm_district 里没有区");
        }
        base.sort(BY_NAME);
        Map<Integer, String> codeByKey = new HashMap<>();
        Set<String> used = new HashSet<>();
        for (MakaniRows.District r : base) {
            String code = code(r.municipality, r.makaniName);
            if (!used.add(code)) {
                code = code + "-" + r.districtKey; // 同一市政内同名（极少见）时带上 objectid
                used.add(code);
            }
            codeByKey.put(r.districtKey, code);
        }

        Map<Integer, Integer> communities = counts(mapper.communities(t));
        Map<Integer, MakaniRows.Plot> plots = byKey(mapper.plots(t), r -> r.districtKey);
        Map<Integer, MakaniRows.BuildingStat> buildingStats = byKey(mapper.buildingStats(t), r -> r.districtKey);
        Map<Integer, Integer> schools = counts(mapper.schools(t));
        Map<Integer, Integer> medical = counts(mapper.medical(t, props.getMedicalExcludeNamePatterns()));
        Map<Integer, Integer> mosques = counts(mapper.mosques(t));

        List<MakaniSnapshot.District> out = new ArrayList<>();
        int sortNo = 0;
        for (MakaniRows.District r : base) {
            Integer k = r.districtKey;
            MakaniRows.Plot p = plots.getOrDefault(k, new MakaniRows.Plot());
            MakaniRows.BuildingStat b = buildingStats.getOrDefault(k, new MakaniRows.BuildingStat());
            out.add(new MakaniSnapshot.District(
                    codeByKey.get(k), displayName(r.makaniName), ++sortNo, r.municipality, r.makaniId, r.makaniName, r.nameAr, r.areaKm2,
                    r.centroidLon, r.centroidLat, r.minLon, r.minLat, r.maxLon, r.maxLat, r.boundaryHex, r.scopeHex,
                    communities.getOrDefault(k, 0),
                    p.total, p.constructed, p.underConstruction, p.vacantServiced, p.vacantUnserviced,
                    b.total, b.underConstruction,
                    schools.getOrDefault(k, 0),
                    medical.getOrDefault(k, 0),
                    mosques.getOrDefault(k, 0)));
        }

        List<MakaniSnapshot.LandUse> landUse = mapper.landUse(t).stream()
                .map(r -> new MakaniSnapshot.LandUse(codeByKey.get(r.districtKey), r.useDesc, r.plotCount))
                .toList();
        List<MakaniSnapshot.Building> buildings = mapper.buildings(t).stream()
                .map(r -> new MakaniSnapshot.Building(codeByKey.get(r.districtKey), r.buildingId, r.height, r.underConstruction, r.attrs, r.geomHex))
                .toList();

        List<MakaniSnapshot.Plot> plotShapes = mapper.plotShapes(t).stream()
                .map(r -> new MakaniSnapshot.Plot(codeByKey.get(r.districtKey), r.plotId, r.status, r.useDesc, r.attrs, r.geomHex))
                .toList();

        List<MakaniSnapshot.Poi> pois = mapper.pois(t, props.getMedicalExcludeNamePatterns()).stream()
                .map(r -> new MakaniSnapshot.Poi(codeByKey.get(r.districtKey), r.category, r.sourceId, r.nameEn, r.nameAr, r.subtype, r.lon, r.lat, r.attrs))
                .toList();

        log.info("makani 抽取完成：{} 个区，{} 条土地利用，{} 栋建筑，{} 个地块，{} 个公共设施点",
                out.size(), landUse.size(), buildings.size(), plotShapes.size(), pois.size());
        return new MakaniSnapshot(mapper.plotDataDate(t), out, landUse, buildings, plotShapes, pois);
    }

    /** ADM + AL REEM ISLAND → adm-al-reem-island；ADM + HADBAT AL ZA`FARANAH → adm-hadbat-al-zafaranah */
    static String code(String municipality, String makaniName) {
        return slug(municipality + " " + makaniName);
    }

    static String slug(String s) {
        String v = Objects.requireNonNullElse(s, "").toLowerCase(Locale.ROOT).replaceAll("[`'’]", "");
        return v.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    }

    /** 排序键：只保留字母数字和空格，小写 */
    static String sortKey(String name) {
        return Objects.requireNonNullElse(name, "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", "").trim();
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
        return rows.stream().filter(r -> r.districtKey != null).collect(Collectors.toMap(r -> r.districtKey, r -> r.n));
    }

    private static <T> Map<Integer, T> byKey(List<T> rows, Function<T, Integer> key) {
        return rows.stream().filter(r -> Objects.nonNull(key.apply(r))).collect(Collectors.toMap(key, Function.identity()));
    }
}
