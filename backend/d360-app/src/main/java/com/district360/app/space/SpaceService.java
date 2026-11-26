package com.district360.app.space;

import com.district360.app.web.dto.PageResult;
import com.district360.stat.mapper.ThemeAstQueryMapper;
import com.district360.stat.mapper.ThemeDevQueryMapper;
import com.district360.stat.mapper.ThemeLivQueryMapper;
import com.district360.stat.mapper.ThemeMktQueryMapper;
import com.district360.stat.mapper.ThemeOpsQueryMapper;
import com.district360.stat.mapper.ThemeSpatialQueryMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/** 空间下钻：读主题库 theme.sp_*，组装节点视图、下级列表、地图图层。 */
@Service
public class SpaceService {

    static final String EMIRATE_KEY = "AD";

    /** node 查询里用于面包屑、外包框和名称的列，不放进 info。 */
    private static final Set<String> RESERVED = Set.of("key", "name", "name_ar", "minx", "miny", "maxx", "maxy", "cx", "cy", "geojson",
            "municipality_name", "district_name", "community_name", "plot_name", "building_name", "floor_name");

    /** 上级链：层级、编码列、名称列。 */
    private static final String[][] PATH = {
            {"municipality", "municipality_code", "municipality_name"},
            {"district", "district_key", "district_name"},
            {"community", "community_key", "community_name"},
            {"plot", "plot_key", "plot_name"},
            {"building", "building_key", "building_name"},
            {"floor", "floor_key", "floor_name"},
    };

    private final ThemeSpatialQueryMapper mapper;
    private final ThemeDevQueryMapper devMapper;
    private final ThemeMktQueryMapper mktMapper;
    private final ThemeOpsQueryMapper opsMapper;
    private final ThemeAstQueryMapper astMapper;
    private final ThemeLivQueryMapper livMapper;

    public SpaceService(ThemeSpatialQueryMapper mapper, ThemeDevQueryMapper devMapper, ThemeMktQueryMapper mktMapper,
                        ThemeOpsQueryMapper opsMapper, ThemeAstQueryMapper astMapper,
                        ThemeLivQueryMapper livMapper) {
        this.mapper = mapper;
        this.devMapper = devMapper;
        this.mktMapper = mktMapper;
        this.opsMapper = opsMapper;
        this.astMapper = astMapper;
        this.livMapper = livMapper;
    }

    public NodeDetail node(SpaceLevel level, String key) {
        Map<String, Object> row = mapper.node(level.id(), key);
        if (row == null || row.get("key") == null) {
            throw new NoSuchElementException("没有这个" + level.id() + ": " + key);
        }
        List<NodeDetail.NodeRef> path = new ArrayList<>();
        if (level != SpaceLevel.EMIRATE) {
            path.add(new NodeDetail.NodeRef("emirate", EMIRATE_KEY, "Emirate of Abu Dhabi"));
            for (String[] p : PATH) {
                if (p[0].equals(level.id())) {
                    break;
                }
                Object k = row.get(p[1]);
                if (k != null) {
                    Object n = row.get(p[2]);
                    path.add(new NodeDetail.NodeRef(p[0], k.toString(), n == null ? k.toString() : n.toString()));
                }
            }
        }
        Map<String, Object> info = new LinkedHashMap<>();
        row.forEach((k, v) -> {
            if (v != null && !RESERVED.contains(k)) {
                info.put(camel(k), v);
            }
        });
        if (level == SpaceLevel.COMMUNITY || level == SpaceLevel.PLOT) {
            String mp = devMapper.masterPlanOf(level.id(), key);
            if (mp != null) {
                info.put("masterPlan", mp);
            }
        }
        String col = level.col();
        String filterKey = level == SpaceLevel.EMIRATE ? null : key;
        Map<String, Object> stat = new LinkedHashMap<>();
        Map<String, Object> s = mapper.stat(level.id(), col, key);
        if (s != null) {
            s.forEach((k, v) -> stat.put(camel(k), v));
        }
        Map<String, List<Map<String, Object>>> breakdowns = new LinkedHashMap<>();
        for (SpaceDim d : level.dims()) {
            breakdowns.put(d.key(), mapper.breakdown(d.table(), d.dimExpr(), d.areaExpr(), d.extra(),
                    filterKey == null ? null : col, filterKey));
        }
        SpaceLevel child = level.child();
        long childCount = child == null ? 0 : mapper.childCount(child.id(), level.col(), key, null);
        return new NodeDetail(level.id(), str(row.get("key")), str(row.get("name")), str(row.get("name_ar")), path,
                child == null ? null : child.id(), childCount,
                nums(row, "minx", "miny", "maxx", "maxy"), nums(row, "cx", "cy"),
                str(row.get("geojson")), info, stat, breakdowns,
                level.layers().stream().map(SpaceLayer::id).toList());
    }

    public PageResult<Map<String, Object>> children(SpaceLevel level, String key, String q, int page, int size) {
        SpaceLevel child = level.child();
        if (child == null) {
            return new PageResult<>(List.of(), 0, size, 0, false);
        }
        String query = StringUtils.hasText(q) ? q.trim() : null;
        int p = Math.max(page, 0);
        int n = Math.max(1, Math.min(size, 500));
        long total = mapper.childCount(child.id(), level.col(), key, query);
        List<Map<String, Object>> items = mapper.children(child.id(), level.col(), key, query, n, p * n);
        return new PageResult<>(items, p, n, (int) total, (long) (p + 1) * n < total);
    }

    public String features(SpaceLevel level, String key, SpaceLayer layer) {
        if (!level.layers().contains(layer)) {
            throw new IllegalArgumentException(level.id() + " 层级没有图层 " + layer.id() + "（可用："
                    + level.layers().stream().map(SpaceLayer::id).toList() + "）");
        }
        String col = level == SpaceLevel.EMIRATE ? null : level.col();
        return mapper.features(layer.id(), col, key, layer.limit(), layer.tolerance());
    }

    /**
     * 开发建设页签：总规、规划建筑面积、总规实现率、在建工地。建筑 / 楼层 / 单元按所在地块统计（管控指标在地块上）。
     * 返回 scope 表示实际统计的范围；所在建筑没有地块时 scope 为 null、其余为空。
     */
    public Map<String, Object> dev(SpaceLevel level, String key) {
        Scope sc = scope(level, key);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scope", sc.ref());
        if (sc.key() == null) {
            return out;
        }
        String col = sc.col();
        String k = sc.filterKey();
        SpaceLevel scopeLevel = sc.level();
        String scopeKey = sc.key();
        out.put("summary", devMapper.summary(col, k));
        out.put("stages", devMapper.stages(col, k));
        out.put("gfaByUse", devMapper.gfaByUse(col, k));
        out.put("masterPlans", devMapper.masterPlans(col, k, 50));
        out.put("sites", devMapper.sites(col, k, 50));
        out.put("plotControls", scopeLevel == SpaceLevel.PLOT ? devMapper.plotControls(scopeKey) : List.of());
        return out;
    }

    /**
     * 市场页签：Tawtheeq 租约（状态、户型、年份、租金）与 ADREC 买卖 / 抵押 / 租赁、ELMS 地块交易。
     * 建筑 / 楼层 / 单元按所在地块统计（Tawtheeq 目前只能关联到地块）。地块范围另给租约明细（不含个人信息）。
     */
    public Map<String, Object> mkt(SpaceLevel level, String key) {
        Scope sc = scope(level, key);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scope", sc.ref());
        if (sc.key() == null) {
            return out;
        }
        String col = sc.col();
        String k = sc.filterKey();
        out.put("tenancy", mktMapper.tenancySummary(col, k));
        out.put("byStatus", mktMapper.tenancyBreakdown(col, k, "tenancy_status"));
        out.put("byUnitType", mktMapper.tenancyBreakdown(col, k, "unit_type"));
        out.put("byPropertyUsage", mktMapper.tenancyBreakdown(col, k, "property_usage"));
        out.put("byYear", mktMapper.tenancyByYear(col, k));
        out.put("tenancies", sc.level() == SpaceLevel.PLOT ? mktMapper.tenancies(col, k, 50) : List.of());
        out.put("adrec", mktMapper.adrecSummary(col, k));
        out.put("adrecByMonth", mktMapper.adrecByMonth(col, k));
        return out;
    }

    /**
     * 城市运行页签：路灯（点数、灯盏、状态、灯型；源 roadtype 全为空，不统计）与公交站（站、站台、列表）。
     * 都是区域设施、没有地块属性：地块 / 建筑 / 楼层 / 单元按所在社区统计。
     */
    public Map<String, Object> ops(SpaceLevel level, String key) {
        SpaceLevel scopeLevel = level;
        String scopeKey = key;
        if (level == SpaceLevel.PLOT || level == SpaceLevel.BUILDING || level == SpaceLevel.FLOOR || level == SpaceLevel.UNIT) {
            Map<String, Object> row = mapper.node(level.id(), key);
            if (row == null || row.get("key") == null) {
                throw new NoSuchElementException("没有这个" + level.id() + ": " + key);
            }
            Object c = row.get("community_key");
            scopeLevel = SpaceLevel.COMMUNITY;
            scopeKey = c == null ? null : c.toString();
        }
        Scope sc = new Scope(scopeLevel, scopeKey);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scope", sc.ref());
        if (sc.key() == null) {
            return out;
        }
        String col = sc.col();
        String k = sc.filterKey();
        out.put("summary", opsMapper.summary(col, k));
        out.put("lightsByStatus", opsMapper.lightsBy(col, k, "status"));
        out.put("lightsByLampType", opsMapper.lightsBy(col, k, "lamp_type"));
        out.put("busStops", opsMapper.busStops(col, k, 100));
        return out;
    }

    /** 设施分类（与 ast_facility.category 一致）。 */
    private static final List<String> AST_CATEGORIES = List.of("school", "medical", "pharmacy", "mosque", "park");

    /**
     * 设施页签：学校、医疗、药房、清真寺、公园的数量与容量、分类、列表（带详情）。建筑 / 楼层 / 单元按所在地块统计。
     */
    public Map<String, Object> ast(SpaceLevel level, String key) {
        Scope sc = scope(level, key);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scope", sc.ref());
        if (sc.key() == null) {
            return out;
        }
        String col = sc.col();
        String k = sc.filterKey();
        out.put("summary", astMapper.summary(col, k));
        Map<String, Object> subtypes = new LinkedHashMap<>();
        Map<String, Object> items = new LinkedHashMap<>();
        for (String c : AST_CATEGORIES) {
            subtypes.put(c, astMapper.bySubtype(col, k, c));
            items.put(c, astMapper.items(col, k, c, 30));
        }
        out.put("bySubtype", subtypes);
        out.put("items", items);
        return out;
    }

    /**
     * 宜居页签：评分只有区域粒度（v1 stat.stat_district_liveability，每日从宜居库抽取）。
     * 酋长国 / 市政：范围内各区评分排名与汇总；区域：本区评分、排名、公园；社区及以下：按所在区域。
     */
    public Map<String, Object> liv(SpaceLevel level, String key) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (level == SpaceLevel.EMIRATE || level == SpaceLevel.MUNICIPALITY) {
            List<Map<String, Object>> list = livMapper.districts(level == SpaceLevel.EMIRATE ? null : key, null);
            out.put("scope", Map.of("level", level.id(), "key", key));
            out.put("summary", livSummary(list));
            out.put("districts", list);
            return out;
        }
        String district = key;
        if (level != SpaceLevel.DISTRICT) {
            Map<String, Object> row = mapper.node(level.id(), key);
            if (row == null || row.get("key") == null) {
                throw new NoSuchElementException("没有这个" + level.id() + ": " + key);
            }
            Object d = row.get("district_key");
            district = d == null ? null : d.toString();
        }
        out.put("scope", district == null ? null : Map.of("level", "district", "key", district));
        if (district != null) {
            List<Map<String, Object>> one = livMapper.districts(null, district);
            out.put("district", one.isEmpty() ? null : one.get(0));
            out.put("facilityGaps", livMapper.facilityGaps(district, 3));
            out.put("needs", livMapper.needs(district));
        }
        return out;
    }

    /** 范围内区数、平均评分（现状 / 在途 / 目标）、公园合计与缺口合计、数据日期。 */
    private static Map<String, Object> livSummary(List<Map<String, Object>> list) {
        int n = 0;
        double ex = 0, cu = 0, tg = 0, gap = 0, area = 0, capex = 0;
        long parks = 0;
        Map<String, Integer> tiers = new LinkedHashMap<>();
        tiers.put("high_priority", 0);
        tiers.put("priority", 0);
        tiers.put("remaining", 0);
        String asOf = null;
        for (Map<String, Object> r : list) {
            if (r.get("parks") instanceof Number p) parks += p.longValue();
            if (r.get("parkAreaHa") instanceof Number a) area += a.doubleValue();
            if (r.get("parkGapHa") instanceof Number g) gap += g.doubleValue();
            if (r.get("capexAed") instanceof Number c) capex += c.doubleValue();
            if (r.get("tier") instanceof String t && tiers.containsKey(t)) tiers.merge(t, 1, Integer::sum);
            if (r.get("asOf") != null && (asOf == null || asOf.compareTo(r.get("asOf").toString()) < 0)) asOf = r.get("asOf").toString();
            if (Boolean.TRUE.equals(r.get("inScope")) && r.get("pipeline") instanceof Number c) {
                n++;
                cu += c.doubleValue();
                ex += ((Number) r.get("existing")).doubleValue();
                tg += ((Number) r.get("target")).doubleValue();
            }
        }
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("districts", list.size());
        s.put("inScope", n);
        s.put("avgExisting", n == 0 ? null : Math.round(ex / n * 100) / 100.0);
        s.put("avgPipeline", n == 0 ? null : Math.round(cu / n * 100) / 100.0);
        s.put("avgTarget", n == 0 ? null : Math.round(tg / n * 100) / 100.0);
        s.put("parks", parks);
        s.put("parkAreaHa", Math.round(area * 10) / 10.0);
        s.put("parkGapHa", Math.round(gap * 10) / 10.0);
        // CAPEX 合计不按评估范围过滤（与宜居平台 city_budget 一致）
        s.put("capexAed", Math.round(capex));
        s.put("tiers", tiers);
        s.put("asOf", asOf);
        return s;
    }

    /**
     * 首页五主题总览（原型 Overview 的 Development / Assets / Market / Liveability / City operations 五块），
     * 只取各主题的汇总，不带列表；只在酋长国 / 市政 / 区域 / 社区层级提供（地块及以下的概况是地块卡、建筑卡）。
     * 地块建设状态、建筑用途前几类由节点视图（stat、breakdowns）提供，这里不重复。
     */
    public Map<String, Object> overview(SpaceLevel level, String key) {
        if (level != SpaceLevel.EMIRATE && level != SpaceLevel.MUNICIPALITY && level != SpaceLevel.DISTRICT
                && level != SpaceLevel.COMMUNITY) {
            throw new IllegalArgumentException("首页总览只在酋长国、市政、区域、社区层级提供");
        }
        String col = level == SpaceLevel.EMIRATE ? null : level.col();
        String k = level == SpaceLevel.EMIRATE ? null : key;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scope", Map.of("level", level.id(), "key", key));
        Map<String, Object> dev = new LinkedHashMap<>();
        dev.put("summary", devMapper.summary(col, k));
        dev.put("stages", devMapper.stages(col, k));
        out.put("dev", dev);
        out.put("ast", astMapper.summary(col, k));
        Map<String, Object> mkt = new LinkedHashMap<>();
        mkt.put("tenancy", mktMapper.tenancySummary(col, k));
        mkt.put("adrec", mktMapper.adrecSummary(col, k));
        out.put("mkt", mkt);
        out.put("ops", opsMapper.summary(col, k));
        // 宜居：区域及社区给所在区域的评分与 CAPEX；酋长国 / 市政给平均分、CAPEX 合计、分档分布（不带区域列表）
        Map<String, Object> liv = new LinkedHashMap<>(liv(level, key));
        liv.remove("districts");
        liv.remove("facilityGaps");
        liv.remove("needs");
        out.put("liv", liv);
        return out;
    }

    /** 范围圈半径（米），与原型一致 */
    private static final int[] RINGS = {500, 1000};

    /**
     * 地块周边设施（原型选中地块后的 500 / 1000 m 范围圈）：圆心为地块面内一点；建筑 / 楼层 / 单元按所在地块。
     * 返回圆心、半径、1000 m 内的设施与公交站（含距离），以及各类在 500 / 1000 m 内的个数与最近距离。
     */
    public Map<String, Object> nearby(SpaceLevel level, String key) {
        if (level != SpaceLevel.PLOT && level != SpaceLevel.BUILDING && level != SpaceLevel.FLOOR && level != SpaceLevel.UNIT) {
            throw new IllegalArgumentException("周边设施只在地块及以下层级提供");
        }
        Scope sc = scope(level, key);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scope", sc.ref());
        if (sc.key() == null) {
            return out;
        }
        Map<String, Object> c = astMapper.plotPoint(sc.key());
        out.put("center", c == null ? null : List.of(c.get("cx"), c.get("cy")));
        out.put("radii", RINGS);
        List<Map<String, Object>> items = astMapper.nearby(sc.key(), RINGS[RINGS.length - 1]);
        Map<String, Map<String, Object>> counts = new LinkedHashMap<>();
        for (Map<String, Object> it : items) {
            String cat = String.valueOf(it.get("category"));
            double d = it.get("dist") instanceof Number n ? n.doubleValue() : Double.MAX_VALUE;
            Map<String, Object> m = counts.computeIfAbsent(cat, k -> {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("r500", 0);
                x.put("r1000", 0);
                x.put("nearest", null);
                return x;
            });
            if (d <= RINGS[0]) m.put("r500", (int) m.get("r500") + 1);
            m.put("r1000", (int) m.get("r1000") + 1);
            if (m.get("nearest") == null) m.put("nearest", it.get("dist"));
        }
        out.put("counts", counts);
        out.put("items", items);
        return out;
    }

    /** 主题页签的统计范围：建筑 / 楼层 / 单元按所在地块；没有地块时 key 为 null。 */
    private record Scope(SpaceLevel level, String key) {
        String col() { return level == SpaceLevel.EMIRATE ? null : level.col(); }
        String filterKey() { return level == SpaceLevel.EMIRATE ? null : key; }
        Map<String, Object> ref() { return key == null ? null : Map.of("level", level.id(), "key", key); }
    }

    private Scope scope(SpaceLevel level, String key) {
        if (level == SpaceLevel.BUILDING || level == SpaceLevel.FLOOR || level == SpaceLevel.UNIT) {
            Map<String, Object> row = mapper.node(level.id(), key);
            if (row == null || row.get("key") == null) {
                throw new NoSuchElementException("没有这个" + level.id() + ": " + key);
            }
            Object plot = row.get("plot_key");
            return new Scope(SpaceLevel.PLOT, plot == null ? null : plot.toString());
        }
        return new Scope(level, key);
    }

    public List<Map<String, Object>> search(String q, int limit) {
        if (!StringUtils.hasText(q) || q.trim().length() < 2) {
            return List.of();
        }
        return mapper.search(q.trim(), Math.max(1, Math.min(limit, 20)));
    }

    public Map<String, Object> meta() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tables", mapper.metaTables());
        m.put("runs", mapper.lastRuns("ods-"));
        return m;
    }

    private static String camel(String s) {
        StringBuilder b = new StringBuilder(s.length());
        boolean up = false;
        for (char c : s.toCharArray()) {
            if (c == '_') {
                up = true;
            } else {
                b.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return b.toString();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static double[] nums(Map<String, Object> row, String... keys) {
        double[] out = new double[keys.length];
        for (int i = 0; i < keys.length; i++) {
            Object v = row.get(keys[i]);
            if (!(v instanceof Number n)) {
                return null;
            }
            out[i] = n.doubleValue();
        }
        return out;
    }
}
