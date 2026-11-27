package com.district360.app.space;

import com.district360.app.config.WatchProperties;
import com.district360.stat.mapper.ThemeBizQueryMapper;
import com.district360.stat.mapper.ThemeLivQueryMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 需关注区域（原型 Districts to watch）：酋长国 / 市政下，越过任一阈值的区域，带上越过的每一项。
 * <p>
 * 原型 6 项指标，按原型顺序：
 * <ol>
 *   <li>{@code gap} 距目标差距 = 目标 − 在途 ≥ gapMin（只看宜居评估范围内的区）；</li>
 *   <li>{@code capex} 达标所需投资 ≥ capexMinAed，附占本市政 / 全酋长国 CAPEX 的比例；</li>
 *   <li>{@code prj} 延期资本项目 ≥ 2 个（延期口径见 dev_capital_project.delayed）；</li>
 *   <li>{@code occ} 入住率 &lt; 80%（Tawtheeq 单元状态 is_rented；只看有状态的单元 ≥ 300 个的区）；</li>
 *   <li>{@code park} 公园供给率 = 现有面积 ÷ 需求面积 &lt; parkProvisionMax（需求面积 = 现有 + 缺口，取自宜居库 fact_parks_demand；
 *       只看需求 ≥ parkNeedMinHa 的区）；</li>
 *   <li>{@code vio} 视觉违规按期整改率 &lt; 68%（近 12 个月、有时限的上报 ≥ 50 条的区）。</li>
 * </ol>
 * prj / occ / vio 来自业务视图（ods_pop_view）：对应主题表没有数据时 available = false，note 写明原因。
 * 排序与原型一致：轮流取每项指标里最差的区，前几张卡片就覆盖各个主题。
 */
@Service
public class WatchService {

    private final ThemeLivQueryMapper livMapper;
    private final ThemeBizQueryMapper bizMapper;
    private final WatchProperties props;

    /** 延期项目数阈值、入住率阈值与最少单元数、按期整改率阈值与最少上报数（原型）。 */
    static final int PRJ_MIN = 2;
    static final double OCC_MAX = 0.80;
    static final int OCC_MIN_UNITS = 300;
    static final double VIO_MAX = 0.68;
    static final int VIO_MIN_REPORTS = 50;

    public WatchService(ThemeLivQueryMapper livMapper, ThemeBizQueryMapper bizMapper, WatchProperties props) {
        this.livMapper = livMapper;
        this.bizMapper = bizMapper;
        this.props = props;
    }

    /** 一项指标：key、名称、阈值、比较方向（hi = 大于等于阈值算越过）、是否可算、点卡片后打开的页签。 */
    private record Indicator(String key, String name, String threshold, boolean available, String tab, String note) {
    }

    /** 一个区在一项指标上越过阈值：value 越「差」排序越前（hi 降序，否则升序）。 */
    private record Hit(Map<String, Object> district, double value, Map<String, Object> item) {
    }

    public Map<String, Object> watch(SpaceLevel level, String key) {
        if (level != SpaceLevel.EMIRATE && level != SpaceLevel.MUNICIPALITY) {
            throw new IllegalArgumentException("需关注区域只在酋长国、市政层级提供");
        }
        List<Map<String, Object>> districts = livMapper.districts(level == SpaceLevel.EMIRATE ? null : key, null);
        double capexTotal = districts.stream().mapToDouble(d -> num(d.get("capexAed"))).sum();
        String shareOf = level == SpaceLevel.EMIRATE ? "占全酋长国" : "占本市政";

        String muni = level == SpaceLevel.EMIRATE ? null : key;
        Map<String, Map<String, Object>> meta = new LinkedHashMap<>();
        bizMapper.tableRows().forEach(r -> meta.put(String.valueOf(r.get("table")), r));
        boolean prjOn = rows(meta, "dev_capital_project") > 0;
        boolean occOn = rows(meta, "mkt_unit_status") > 0;
        boolean vioOn = rows(meta, "ops_distortion") > 0;

        List<Indicator> indicators = List.of(
                new Indicator("gap", "距目标差距", "≥ " + fmt(props.gapMin()) + " 分", true, "liv", null),
                new Indicator("capex", "达标所需投资", "≥ AED " + fmt(props.capexMinAed() / 1e6) + "M", true, "liv", null),
                new Indicator("prj", "延期资本项目", "≥ " + PRJ_MIN + " 个", prjOn, "dev", prjOn ? null : note(meta, "dev_capital_project")),
                new Indicator("occ", "入住率", "< " + fmt(OCC_MAX * 100) + "%", occOn, "mkt", occOn ? null : note(meta, "mkt_unit_status")),
                new Indicator("park", "公园供给率", "< " + fmt(props.parkProvisionMax() * 100) + "%", true, "liv", null),
                new Indicator("vio", "视觉违规按期整改率", "< " + fmt(VIO_MAX * 100) + "%", vioOn, "ops", vioOn ? null : note(meta, "ops_distortion")));
        Map<String, Map<String, Object>> byDistrict = new LinkedHashMap<>();
        districts.forEach(d -> byDistrict.put(String.valueOf(d.get("key")), d));

        Map<String, List<Hit>> hits = new LinkedHashMap<>();
        hits.put("gap", collect(districts, d -> {
            if (!Boolean.TRUE.equals(d.get("inScope")) || d.get("gap") == null) return null;
            double v = num(d.get("gap"));
            return v >= props.gapMin() ? new Hit(d, -v, item("gap", v, Map.of("pipeline", d.get("pipeline"), "target", d.get("target")))) : null;
        }));
        hits.put("capex", collect(districts, d -> {
            double v = num(d.get("capexAed"));
            return v >= props.capexMinAed() ? new Hit(d, -v, item("capex", v,
                    Map.of("sharePct", capexTotal > 0 ? Math.round(v / capexTotal * 1000) / 10.0 : 0, "shareOf", shareOf))) : null;
        }));
        hits.put("prj", !prjOn ? List.of() : collect(bizMapper.watchProjects(muni), r -> {
            Map<String, Object> d = byDistrict.get(String.valueOf(r.get("key")));
            double v = num(r.get("delayed"));
            return d != null && v >= PRJ_MIN ? new Hit(d, -v, item("prj", v,
                    Map.of("projects", r.get("projects"), "delayedBudgetAed", r.get("delayedBudgetAed")))) : null;
        }));
        hits.put("occ", !occOn ? List.of() : collect(bizMapper.watchOccupancy(muni), r -> {
            Map<String, Object> d = byDistrict.get(String.valueOf(r.get("key")));
            double units = num(r.get("units"));
            if (d == null || units < OCC_MIN_UNITS) return null;
            double p = num(r.get("rented")) / units;
            return p < OCC_MAX ? new Hit(d, p, item("occ", Math.round(p * 1000) / 10.0,
                    Map.of("units", (long) units, "rented", r.get("rented")))) : null;
        }));
        hits.put("park", collect(districts, d -> {
            double area = num(d.get("parkAreaHa")), need = area + num(d.get("parkGapHa"));
            if (need < props.parkNeedMinHa()) return null;
            double p = area / need;
            return p < props.parkProvisionMax() ? new Hit(d, p, item("park", Math.round(p * 1000) / 10.0,
                    Map.of("areaHa", Math.round(area * 10) / 10.0, "needHa", Math.round(need * 10) / 10.0))) : null;
        }));
        hits.put("vio", !vioOn ? List.of() : collect(bizMapper.watchDistortions(muni), r -> {
            Map<String, Object> d = byDistrict.get(String.valueOf(r.get("key")));
            double n = num(r.get("reports"));
            if (d == null || n < VIO_MIN_REPORTS) return null;
            double p = num(r.get("fixed")) / n;
            return p < VIO_MAX ? new Hit(d, p, item("vio", Math.round(p * 1000) / 10.0,
                    Map.of("reports", (long) n, "fixed", r.get("fixed")))) : null;
        }));
        // 按原型指标顺序排列（轮流取各项最差的区时也按这个顺序）
        Map<String, List<Hit>> ordered = new LinkedHashMap<>();
        for (Indicator i : indicators) {
            ordered.put(i.key(), hits.getOrDefault(i.key(), List.of()));
        }
        hits = ordered;

        // 轮流取每项指标里最差的区（原型 watchScan 的排序），同一区只进一次，带上它越过的所有指标（按指标顺序）
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> items = new LinkedHashMap<>();
        for (List<Hit> list : hits.values()) {
            list.forEach(h -> items.computeIfAbsent((String) h.district().get("key"), x -> new ArrayList<>()).add(h.item()));
        }
        for (int r = 0; ; r++) {
            boolean more = false;
            for (List<Hit> list : hits.values()) {
                if (r >= list.size()) continue;
                more = true;
                Map<String, Object> d = list.get(r).district();
                String dk = (String) d.get("key");
                if (!byKey.containsKey(dk)) {
                    Map<String, Object> w = new LinkedHashMap<>();
                    w.put("key", dk);
                    w.put("name", d.get("name"));
                    w.put("municipalityCode", d.get("municipalityCode"));
                    w.put("cx", d.get("cx"));
                    w.put("cy", d.get("cy"));
                    w.put("items", items.get(dk));
                    byKey.put(dk, w);
                }
            }
            if (!more) break;
        }

        List<Map<String, Object>> ind = new ArrayList<>();
        for (Indicator i : indicators) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", i.key());
            m.put("name", i.name());
            m.put("threshold", i.threshold());
            m.put("available", i.available());
            m.put("count", i.available() ? hits.get(i.key()).size() : null);
            m.put("tab", i.tab());
            if (i.note() != null) m.put("note", i.note());
            ind.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scope", Map.of("level", level.id(), "key", key));
        out.put("indicators", ind);
        out.put("maxCards", props.maxCards());
        out.put("districts", new ArrayList<>(byKey.values()));
        out.put("asOf", districts.stream().map(d -> d.get("asOf")).filter(x -> x != null).map(Object::toString).max(Comparator.naturalOrder()).orElse(null));
        return out;
    }

    private static long rows(Map<String, Map<String, Object>> meta, String table) {
        Map<String, Object> m = meta.get(table);
        return m == null ? 0 : (long) num(m.get("rows"));
    }

    private static String note(Map<String, Map<String, Object>> meta, String table) {
        Map<String, Object> m = meta.get(table);
        Object n = m == null ? null : m.get("note");
        return n != null ? n.toString() : "主题表 " + table + " 暂无数据";
    }

    private static List<Hit> collect(List<Map<String, Object>> districts, Function<Map<String, Object>, Hit> f) {
        List<Hit> out = new ArrayList<>();
        for (Map<String, Object> d : districts) {
            Hit h = f.apply(d);
            if (h != null) out.add(h);
        }
        out.sort(Comparator.comparingDouble(Hit::value));
        return out;
    }

    private static Map<String, Object> item(String key, double value, Map<String, Object> ref) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("value", value);
        m.putAll(ref);
        return m;
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
