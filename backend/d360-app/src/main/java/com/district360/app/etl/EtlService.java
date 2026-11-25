package com.district360.app.etl;

import com.district360.common.district.DistrictRef;
import com.district360.common.extract.SourceSnapshot;
import com.district360.common.extract.StatExtractor;
import com.district360.common.theme.ThemeExtractor;
import com.district360.common.theme.ThemeLoadResult;
import com.district360.common.theme.ThemeLoader;
import com.district360.stat.mapper.EtlLogMapper;
import com.district360.stat.mapper.StatQueryMapper;
import com.district360.stat.write.StatWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 运行各源抽取：抽取 → 统一写入 → 记日志。某个源失败不影响其他源，也不会清掉它已有的数据。
 * <p>
 * 两类抽取器共用手动触发接口、定时任务、并发锁和抽取日志（stat.etl_job_log）：
 * <ul>
 *   <li>{@link StatExtractor}：产出区级快照，由 {@link StatWriter} 写统计库（stat）；</li>
 *   <li>{@link ThemeExtractor}：流式写主题库（theme）明细，由同一 Sink 类型的 {@link ThemeLoader} 负责事务、清表、关联与汇总。</li>
 * </ul>
 */
@Service
public class EtlService {

    private static final Logger log = LoggerFactory.getLogger(EtlService.class);
    private static final String MAKANI = "makani";

    private final Map<String, StatExtractor<?>> extractors = new LinkedHashMap<>();
    private final Map<String, ThemeExtractor<?>> themeExtractors = new LinkedHashMap<>();
    private final List<ThemeLoader<?>> themeLoaders;
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final StatWriter writer;
    private final EtlLogMapper logMapper;
    private final StatQueryMapper query;

    public EtlService(List<StatExtractor<?>> extractors, StatWriter writer, EtlLogMapper logMapper,
                      StatQueryMapper query, ObjectProvider<ThemeExtractor<?>> themeExtractors,
                      ObjectProvider<ThemeLoader<?>> themeLoaders) {
        // makani 排第一：它产出统计范围（区面），宜居 / ADREC 依赖这个范围
        extractors.stream().filter(e -> MAKANI.equals(e.source())).forEach(e -> this.extractors.put(e.source(), e));
        extractors.stream().filter(e -> !MAKANI.equals(e.source())).forEach(e -> this.extractors.put(e.source(), e));
        themeExtractors.orderedStream().forEach(e -> this.themeExtractors.put(e.source(), e));
        this.themeLoaders = themeLoaders.orderedStream().toList();
        this.query = query;
        this.writer = writer;
        this.logMapper = logMapper;
        log.info("已启用的抽取器：统计库 {}，主题库 {}", this.extractors.keySet(), this.themeExtractors.keySet());
    }

    public List<String> sources() {
        List<String> all = new ArrayList<>(extractors.keySet());
        all.addAll(themeExtractors.keySet());
        return List.copyOf(all);
    }

    public boolean has(String source) {
        return extractors.containsKey(source) || themeExtractors.containsKey(source);
    }

    /** 每日定时 / POST /admin/etl/all：统计库全部抽取器 + scheduled() 为 true 的主题库抽取器。 */
    public List<EtlRunResult> runAll() {
        List<EtlRunResult> results = new ArrayList<>();
        extractors.keySet().forEach(s -> results.add(run(s)));
        themeExtractors.values().stream().filter(ThemeExtractor::scheduled).forEach(e -> results.add(run(e.source())));
        return results;
    }

    public EtlRunResult run(String source) {
        ThemeExtractor<?> theme = themeExtractors.get(source);
        if (theme != null) {
            return locked(source, () -> {
                ThemeLoadResult r = load(loaderFor(theme), theme);
                int rows = (int) Math.min(Integer.MAX_VALUE, r.rows());
                return new Outcome(rows, r.sourceAsOf(), String.valueOf(r.tableRows()));
            });
        }
        StatExtractor<?> extractor = extractors.get(source);
        if (extractor == null) {
            throw new NoSuchElementException("没有启用的抽取器: " + source + "（已启用：" + sources() + "）");
        }
        return locked(source, () -> {
            SourceSnapshot snapshot = extractor.extract(districtsFor(source));
            writer.write(snapshot);
            return new Outcome(snapshot.rowCount(), snapshot.sourceAsOf(), null);
        });
    }

    private record Outcome(int rows, LocalDate asOf, String note) {
    }

    @FunctionalInterface
    private interface Job {
        Outcome run() throws Exception;
    }

    /** 同一数据源不并发；记抽取日志；异常即失败（写入方已回滚，原数据不变）。 */
    private EtlRunResult locked(String source, Job body) {
        ReentrantLock lock = locks.computeIfAbsent(source, k -> new ReentrantLock());
        if (!lock.tryLock()) {
            return new EtlRunResult(source, "skipped", null, null, 0, "该数据源正在抽取中");
        }
        EtlLogMapper.Started job = new EtlLogMapper.Started();
        job.source = source;
        long t0 = System.currentTimeMillis();
        try {
            logMapper.start(job);
            Outcome o = body.run();
            long ms = System.currentTimeMillis() - t0;
            logMapper.finish(job.id, "success", o.rows(), o.asOf(), o.note() == null ? null : truncate(o.note()));
            log.info("抽取成功 source={} rows={} asOf={} {}ms {}", source, o.rows(), o.asOf(), ms, o.note() == null ? "" : o.note());
            return new EtlRunResult(source, "success", o.rows(), o.asOf(), ms, o.note());
        } catch (Exception e) {
            long ms = System.currentTimeMillis() - t0;
            String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error("抽取失败 source={}", source, e);
            if (job.id != null) {
                logMapper.finish(job.id, "failed", null, null, truncate(msg));
            }
            return new EtlRunResult(source, "failed", null, null, ms, msg);
        } finally {
            lock.unlock();
        }
    }

    private ThemeLoader<?> loaderFor(ThemeExtractor<?> extractor) {
        return themeLoaders.stream()
                .filter(l -> l.sinkType().equals(extractor.sinkType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "没有处理 " + extractor.sinkType().getSimpleName() + " 的主题库 Loader（d360-stat 的 theme 包）"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ThemeLoadResult load(ThemeLoader loader, ThemeExtractor extractor) {
        return loader.load(extractor);
    }

    /**
     * makani 抽取器自己从 makani 产出区列表（传空列表）；其他源拿到全部区及其统计范围（makani 原始区面），
     * 按范围做空间查询，不依赖各库自己的区 ID / 区名。
     */
    private List<DistrictRef> districtsFor(String source) {
        if (MAKANI.equals(source)) {
            return List.of();
        }
        List<DistrictRef> districts = query.listScopes().stream()
                .map(r -> new DistrictRef(r.code, r.nameEn, r.municipality, r.makaniDistrictId, r.scopeHex))
                .toList();
        if (districts.isEmpty()) {
            throw new IllegalStateException("统计库里还没有区，请先执行 makani 抽取");
        }
        return districts;
    }

    private static String truncate(String s) {
        return s.length() > 4000 ? s.substring(0, 4000) : s;
    }
}
