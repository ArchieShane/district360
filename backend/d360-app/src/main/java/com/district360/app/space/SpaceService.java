package com.district360.app.space;

import com.district360.app.web.dto.PageResult;
import com.district360.stat.mapper.ThemeDevQueryMapper;
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

    public SpaceService(ThemeSpatialQueryMapper mapper, ThemeDevQueryMapper devMapper) {
        this.mapper = mapper;
        this.devMapper = devMapper;
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
        SpaceLevel scopeLevel = level;
        String scopeKey = key;
        if (level == SpaceLevel.BUILDING || level == SpaceLevel.FLOOR || level == SpaceLevel.UNIT) {
            Map<String, Object> row = mapper.node(level.id(), key);
            if (row == null || row.get("key") == null) {
                throw new NoSuchElementException("没有这个" + level.id() + ": " + key);
            }
            Object plot = row.get("plot_key");
            scopeLevel = SpaceLevel.PLOT;
            scopeKey = plot == null ? null : plot.toString();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        if (scopeKey == null) {
            out.put("scope", null);
            return out;
        }
        String col = scopeLevel == SpaceLevel.EMIRATE ? null : scopeLevel.col();
        String k = scopeLevel == SpaceLevel.EMIRATE ? null : scopeKey;
        out.put("scope", Map.of("level", scopeLevel.id(), "key", scopeKey));
        out.put("summary", devMapper.summary(col, k));
        out.put("stages", devMapper.stages(col, k));
        out.put("gfaByUse", devMapper.gfaByUse(col, k));
        out.put("masterPlans", devMapper.masterPlans(col, k, 50));
        out.put("sites", devMapper.sites(col, k, 50));
        out.put("plotControls", scopeLevel == SpaceLevel.PLOT ? devMapper.plotControls(scopeKey) : List.of());
        return out;
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
