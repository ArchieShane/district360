package com.district360.app.lineage;

import com.district360.app.config.LandUseProperties;
import com.district360.app.etl.EtlService;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 把 metric-lineage.yml 里的占位符替换成当前实际配置，保证悬浮提示里的库名、表名与真实取数一致。 */
@Service
public class LineageService {

    private static final Pattern JDBC = Pattern.compile("jdbc:postgresql://([^/?]+)/([^?]+).*");

    private final LineageProperties props;
    private final LandUseProperties landUse;
    private final EtlService etl;
    private final Environment env;

    private volatile LineageInfo cached;

    public LineageService(LineageProperties props, LandUseProperties landUse, EtlService etl, Environment env) {
        this.props = props;
        this.landUse = landUse;
        this.etl = etl;
        this.env = env;
    }

    public LineageInfo lineage() {
        LineageInfo c = cached;
        if (c == null) {
            c = build();
            cached = c;
        }
        return c;
    }

    private LineageInfo build() {
        Map<String, String> vars = variables();

        Map<String, LineageInfo.SourceInfo> sources = new LinkedHashMap<>();
        props.getSources().forEach((key, s) -> {
            String url = env.getProperty("d360.datasource." + key + ".url");
            String db = url != null ? describe(url) : s.plannedDb();
            sources.put(key, new LineageInfo.SourceInfo(s.nameZh(), s.nameEn(), db, etl.has(key)));
        });

        Map<String, LineageInfo.MetricInfo> metrics = new LinkedHashMap<>();
        props.getMetrics().forEach((key, m) -> metrics.put(key, new LineageInfo.MetricInfo(
                m.nameZh(), m.nameEn(), m.source(), fill(m.statColumn(), vars),
                m.sourceTables() == null ? List.of() : m.sourceTables().stream().map(t -> fill(t, vars)).toList(),
                fill(m.logicZh(), vars), fill(m.logicEn(), vars), fill(m.sql(), vars) == null ? null : fill(m.sql(), vars).strip())));

        return new LineageInfo(vars.get("stat.db"), sources, metrics);
    }

    private Map<String, String> variables() {
        String schema = env.getProperty("d360.makani.schema", "ods");
        String prefix = env.getProperty("d360.makani.table-prefix", "ods_makani_");
        List<String> exclude = Binder.get(env)
                .bind("d360.makani.medical-exclude-name-patterns", Bindable.listOf(String.class))
                .orElse(List.of("%test%"));

        Map<String, String> v = new LinkedHashMap<>();
        v.put("stat.db", describe(env.getProperty("d360.datasource.stat.url", "")));
        v.put("makani.db", describe(env.getProperty("d360.datasource.makani.url", "")));
        v.put("makani.municipality", env.getProperty("d360.makani.municipality", "ADM"));
        v.put("makani.district", schema + "." + prefix + "udm_district");
        v.put("makani.community", schema + "." + prefix + "udm_community");
        v.put("makani.plot", schema + "." + prefix + "udm_plot");
        v.put("makani.building", schema + "." + prefix + "udm_building");
        v.put("makani.school", schema + "." + prefix + "poi_adek_schools_locations");
        v.put("makani.doh", schema + "." + prefix + "poi_doh_facilities");
        v.put("makani.mosque", schema + "." + prefix + "mosque_awqaf");
        v.put("makani.medicalExclude", String.join(" / ", exclude));
        v.put("makani.medicalExcludeSql", exclude.stream().map(p -> "'" + p.replace("'", "''") + "'").collect(Collectors.joining(", ")));
        v.put("landuse.mapping", landUse.getGroups().stream()
                .filter(g -> g.uses() != null && !g.uses().isEmpty())
                .map(g -> g.nameZh() + "/" + g.nameEn() + " ← " + String.join(", ", g.uses()))
                .collect(Collectors.joining("；")));
        return v;
    }

    /** jdbc:postgresql://host:port/db?x=y → db @ host:port */
    static String describe(String url) {
        Matcher m = JDBC.matcher(url == null ? "" : url);
        return m.matches() ? m.group(2) + " @ " + m.group(1) : url;
    }

    private static String fill(String text, Map<String, String> vars) {
        if (text == null) {
            return null;
        }
        String out = text;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return out;
    }
}
