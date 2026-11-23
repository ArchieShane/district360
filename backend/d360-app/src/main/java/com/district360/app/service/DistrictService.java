package com.district360.app.service;

import com.district360.app.config.LandUseProperties;
import com.district360.app.etl.EtlService;
import com.district360.app.web.dto.BlockStatus;
import com.district360.app.web.dto.DistrictListItem;
import com.district360.app.web.dto.DistrictOverview;
import com.district360.app.web.dto.FacilityItem;
import com.district360.app.web.dto.MapFeatureDetail;
import com.district360.app.web.dto.MetaInfo;
import com.district360.app.web.dto.PageResult;
import com.district360.stat.mapper.StatQueryMapper;
import com.district360.stat.row.District360Row;
import com.district360.stat.row.DistrictListRow;
import com.district360.stat.row.LandUseRow;
import com.district360.stat.row.MapFeatureRow;
import com.district360.stat.row.SourceStatusRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/** 只读统计库，组装接口返回。 */
@Service
public class DistrictService {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");

    private final StatQueryMapper query;
    private final LandUseProperties landUse;
    private final EtlService etl;
    private final ObjectMapper json;

    public DistrictService(StatQueryMapper query, LandUseProperties landUse, EtlService etl, ObjectMapper json) {
        this.query = query;
        this.landUse = landUse;
        this.etl = etl;
        this.json = json;
    }

    public PageResult<DistrictListItem> listDistricts(int page, int size, String q) {
        int p = Math.max(0, page);
        int n = Math.max(1, Math.min(size, 100));
        String filter = q == null || q.isBlank() ? null : q.trim();
        int total = query.countDistricts(filter);
        List<DistrictListItem> items = query.listDistricts(filter, p * n, n).stream().map(r -> new DistrictListItem(
                r.code, r.nameEn, r.nameAr, r.sortNo, r.municipality, r.makaniDistrictId,
                r.centroidLon == null ? null : new double[]{r.centroidLon, r.centroidLat},
                r.minLon == null ? null : new double[]{r.minLon, r.minLat, r.maxLon, r.maxLat},
                r.areaKm2,
                outOfScope(r.livInScope) ? null : r.scoreCurrent,
                scoreStatus(livStatus(r.livPresent, Boolean.TRUE.equals(r.livSample)), r.livInScope))).toList();
        return new PageResult<>(items, p, n, total, (long) (p + 1) * n < total);
    }

    public DistrictOverview overview(String code) {
        District360Row r = query.findDistrict360(code);
        if (r == null) {
            throw new NoSuchElementException("没有这个区: " + code);
        }
        boolean makani = r.plots != null;
        boolean livPresent = r.scoreCurrent != null || r.livAsOf != null;
        boolean adrecPresent = r.adrecAsOf != null || r.transactions != null;

        String makaniStatus = makani ? BlockStatus.OK : BlockStatus.UNAVAILABLE;
        String livStatus = livStatus(livPresent, Boolean.TRUE.equals(r.livSample));
        String adrecStatus = !adrecPresent ? BlockStatus.UNAVAILABLE
                : Boolean.TRUE.equals(r.adrecSample) ? BlockStatus.SAMPLE : BlockStatus.OK;

        var header = new DistrictOverview.Header(r.code, r.nameEn, r.nameAr, r.municipality, r.makaniDistrictId, r.areaKm2,
                r.communities, r.plots);

        // 不在宜居评估范围：得分区块标 not_assessed 且不返回得分（源库里这些区的得分多为 0，不能展示）；公园照常
        DistrictOverview.Liveability liveability;
        if (outOfScope(r.livInScope)) {
            liveability = new DistrictOverview.Liveability(scoreStatus(livStatus, r.livInScope), r.livAsOf, null, null, null, null);
        } else {
            BigDecimal gap = (r.scoreTarget != null && r.scoreCurrent != null)
                    ? r.scoreTarget.subtract(r.scoreCurrent).max(BigDecimal.ZERO) : null;
            liveability = new DistrictOverview.Liveability(livStatus, r.livAsOf, r.scoreExisting, r.scoreCurrent, r.scoreTarget, gap);
        }

        var land = new DistrictOverview.Land(makaniStatus, r.makaniAsOf, r.plots, r.buildings, r.buildingsUnderConstruction,
                pct(r.plotsConstructed, r.plots),
                new DistrictOverview.PlotStatus(r.plotsConstructed, r.plotsUnderConstruction, r.plotsVacantServiced, r.plotsVacantUnserviced),
                makani ? landUseShares(query.listLandUse(code)) : List.of());

        var realEstate = new DistrictOverview.RealEstate(adrecStatus, r.adrecAsOf,
                r.periodStartMonth == null ? null : r.periodStartMonth.format(YM),
                r.periodEndMonth == null ? null : r.periodEndMonth.format(YM),
                r.salesAed == null ? null : r.salesAed.divide(BigDecimal.valueOf(1_000_000_000L), 2, RoundingMode.HALF_UP),
                r.salesYoyPct, r.transactions, r.mortgages, r.avgPriceSqm, r.activeLeases,
                adrecPresent ? query.listAdrecMonthly(code).stream()
                        .map(m -> new DistrictOverview.MonthlyCount(m.month.format(YM), m.transactions)).toList() : List.of());

        var parks = new DistrictOverview.Parks(livStatus, r.livAsOf, r.parks, r.parkAreaHa, r.parkGapHa);
        var facilities = new DistrictOverview.Facilities(makaniStatus, r.makaniAsOf, r.schools, r.medical, r.mosques, parks);

        return new DistrictOverview(header, liveability, land, realEstate, facilities);
    }

    /** 某区某类公共设施（school / medical / mosque），与面板上的计数同一口径。 */
    public List<FacilityItem> facilities(String code, String category) {
        if (!List.of("school", "medical", "mosque").contains(category)) {
            throw new IllegalArgumentException("不支持的设施类别: " + category);
        }
        if (query.findDistrict360(code) == null) {
            throw new NoSuchElementException("没有这个区: " + code);
        }
        return query.listPois(code, category).stream()
                .map(p -> new FacilityItem(p.sourceId, p.category, p.nameEn, p.nameAr, p.subtype, new double[]{p.lon, p.lat},
                        facilityAttrs(p.attrs)))
                .toList();
    }

    /** 地图点击地块的详情。 */
    public MapFeatureDetail plot(long id) {
        MapFeatureRow r = query.findPlot(id);
        if (r == null) {
            throw new NoSuchElementException("没有这个地块: " + id);
        }
        return new MapFeatureDetail("plot", r.id, r.code, r.refId, r.status, r.useDesc, null, null,
                new double[]{r.lon, r.lat}, attrs(r.attrs), r.relatedCount, null);
    }

    /** 地图点击建筑的详情。 */
    public MapFeatureDetail building(long id) {
        MapFeatureRow r = query.findBuilding(id);
        if (r == null) {
            throw new NoSuchElementException("没有这栋建筑: " + id);
        }
        var plot = r.relatedId == null ? null : new MapFeatureDetail.PlotRef(r.relatedId, r.relatedRef, r.relatedName);
        return new MapFeatureDetail("building", r.id, r.code, r.refId, null, null, r.height, r.underConstruction,
                new double[]{r.lon, r.lat}, attrs(r.attrs), null, plot);
    }

    public String boundaries() {
        return query.districtBoundariesGeoJson();
    }

    public byte[] buildingTile(int z, int x, int y) {
        var tile = query.buildingTile(z, x, y);
        return tile == null || tile.mvt == null ? new byte[0] : tile.mvt;
    }

    public byte[] plotTile(int z, int x, int y) {
        var tile = query.plotTile(z, x, y);
        return tile == null || tile.mvt == null ? new byte[0] : tile.mvt;
    }

    public MetaInfo meta() {
        List<MetaInfo.SourceInfo> sources = new ArrayList<>();
        for (SourceStatusRow s : query.listSourceStatus()) {
            sources.add(new MetaInfo.SourceInfo(s.source, etl.has(s.source), s.rowCount, s.sourceAsOf,
                    Boolean.TRUE.equals(s.sample), s.lastStatus, s.lastFinishedAt, s.lastMessage));
        }
        return new MetaInfo(query.countDistricts(null), 4, sources);
    }

    // ------------------------------------------------------------------------------------------

    private ObjectNode attrs(String text) {
        if (text == null || text.isBlank()) {
            return json.createObjectNode();
        }
        try {
            JsonNode n = json.readTree(text);
            return n instanceof ObjectNode o ? o : json.createObjectNode();
        } catch (Exception e) {
            return json.createObjectNode();
        }
    }

    /**
     * 设施详情字段。医疗的 specialities 在源表里是 JSON 文本
     * （[{"Speciality":"Pathology","Sub Speciality":"Hematology"}]），这里整理成去重后的专科名数组；解析不了就原样保留。
     */
    private ObjectNode facilityAttrs(String text) {
        ObjectNode a = attrs(text);
        JsonNode spec = a.get("specialities");
        if (spec != null && spec.isTextual()) {
            try {
                JsonNode arr = json.readTree(spec.asText());
                if (arr.isArray()) {
                    Set<String> names = new LinkedHashSet<>();
                    for (JsonNode e : arr) {
                        String main = e.path("Speciality").asText("").trim();
                        String sub = e.path("Sub Speciality").asText("").trim();
                        String name = sub.isEmpty() || sub.equalsIgnoreCase(main) ? main : main.isEmpty() ? sub : main + " · " + sub;
                        if (!name.isEmpty()) {
                            names.add(name);
                        }
                    }
                    ArrayNode out = json.createArrayNode();
                    names.forEach(out::add);
                    if (out.isEmpty()) {
                        a.remove("specialities");
                    } else {
                        a.set("specialities", out);
                    }
                }
            } catch (Exception ignored) {
                // 不是 JSON：按普通文本展示
            }
        }
        return a;
    }

    /** 宜居库明确标为不在评估范围（param_district_scope.in_scope = false）。NULL 视为在范围内（旧数据、样例数据）。 */
    private static boolean outOfScope(Boolean inScope) {
        return Boolean.FALSE.equals(inScope);
    }

    /** 宜居得分的区块状态：有数据但不在评估范围时为 not_assessed（只影响得分，公园区块仍用 livStatus）。 */
    private static String scoreStatus(String livStatus, Boolean inScope) {
        return outOfScope(inScope) ? BlockStatus.NOT_ASSESSED : livStatus;
    }

    /**
     * 宜居数据本身的状态（公园区块直接用它；得分区块再经 {@link #scoreStatus} 叠加评估范围）。
     * 没有行 = 范围内找不到宜居区或还没抽取 → 「没有查询到数据」；「不在评估范围」只由 in_scope = false 判定。
     */
    private static String livStatus(boolean present, boolean sample) {
        if (!present) {
            return BlockStatus.UNAVAILABLE;
        }
        return sample ? BlockStatus.SAMPLE : BlockStatus.OK;
    }

    private List<DistrictOverview.LandUseShare> landUseShares(List<LandUseRow> rows) {
        List<LandUseProperties.Group> groups = landUse.getGroups();
        Map<String, String> groupOfUse = new HashMap<>();
        groups.forEach(g -> g.uses().forEach(u -> groupOfUse.put(u.toLowerCase(), g.key())));
        String fallback = groups.isEmpty() ? "other" : groups.get(groups.size() - 1).key();

        Map<String, Integer> counts = new LinkedHashMap<>();
        groups.forEach(g -> counts.put(g.key(), 0));
        int total = 0;
        for (LandUseRow r : rows) {
            String key = groupOfUse.getOrDefault(r.useDesc == null ? "" : r.useDesc.toLowerCase(), fallback);
            counts.merge(key, r.plotCount, Integer::sum);
            total += r.plotCount;
        }
        List<DistrictOverview.LandUseShare> out = new ArrayList<>();
        for (LandUseProperties.Group g : groups) {
            int n = counts.getOrDefault(g.key(), 0);
            out.add(new DistrictOverview.LandUseShare(g.key(), g.nameZh(), g.nameEn(), n, pct(n, total), g.highlight(), g.key().equals(fallback)));
        }
        return out;
    }

    private static BigDecimal pct(Integer part, Integer total) {
        if (part == null || total == null || total == 0) {
            return null;
        }
        return BigDecimal.valueOf(part * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
    }
}
