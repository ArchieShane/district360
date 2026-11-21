package com.district360.app.etl;

import com.district360.common.district.DistrictRef;
import com.district360.common.extract.SourceSnapshot;
import com.district360.common.extract.StatExtractor;
import com.district360.stat.mapper.EtlLogMapper;
import com.district360.stat.mapper.StatQueryMapper;
import com.district360.stat.write.StatWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 运行各源抽取：抽取 → 统一写入统计库 → 记日志。某个源失败不影响其他源，也不会清掉它已有的数据。
 */
@Service
public class EtlService {

    private static final Logger log = LoggerFactory.getLogger(EtlService.class);
    private static final String MAKANI = "makani";

    private final Map<String, StatExtractor<?>> extractors = new LinkedHashMap<>();
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final StatWriter writer;
    private final EtlLogMapper logMapper;
    private final StatQueryMapper query;

    public EtlService(List<StatExtractor<?>> extractors, StatWriter writer, EtlLogMapper logMapper,
                      StatQueryMapper query) {
        // makani 排第一：它产出统计范围（区面），宜居 / ADREC 依赖这个范围
        extractors.stream().filter(e -> MAKANI.equals(e.source())).forEach(e -> this.extractors.put(e.source(), e));
        extractors.stream().filter(e -> !MAKANI.equals(e.source())).forEach(e -> this.extractors.put(e.source(), e));
        this.query = query;
        this.writer = writer;
        this.logMapper = logMapper;
        log.info("已启用的抽取器：{}", this.extractors.keySet());
    }

    public List<String> sources() {
        return List.copyOf(extractors.keySet());
    }

    public boolean has(String source) {
        return extractors.containsKey(source);
    }

    public List<EtlRunResult> runAll() {
        List<EtlRunResult> results = new ArrayList<>();
        extractors.keySet().forEach(s -> results.add(run(s)));
        return results;
    }

    public EtlRunResult run(String source) {
        StatExtractor<?> extractor = extractors.get(source);
        if (extractor == null) {
            throw new NoSuchElementException("没有启用的抽取器: " + source + "（已启用：" + extractors.keySet() + "）");
        }
        ReentrantLock lock = locks.computeIfAbsent(source, k -> new ReentrantLock());
        if (!lock.tryLock()) {
            return new EtlRunResult(source, "skipped", null, null, 0, "该数据源正在抽取中");
        }
        EtlLogMapper.Started job = new EtlLogMapper.Started();
        job.source = source;
        long t0 = System.currentTimeMillis();
        try {
            logMapper.start(job);
            SourceSnapshot snapshot = extractor.extract(districtsFor(source));
            writer.write(snapshot);
            long ms = System.currentTimeMillis() - t0;
            logMapper.finish(job.id, "success", snapshot.rowCount(), snapshot.sourceAsOf(), null);
            log.info("抽取成功 source={} rows={} asOf={} {}ms", source, snapshot.rowCount(), snapshot.sourceAsOf(), ms);
            return new EtlRunResult(source, "success", snapshot.rowCount(), snapshot.sourceAsOf(), ms, null);
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

    /**
     * makani 抽取器自己从 makani 产出区列表（传空列表）；其他源拿到 ADM 全部区及其统计范围（makani 原始区面），
     * 按范围做空间查询，不依赖各库自己的区 ID / 区名。
     */
    private List<DistrictRef> districtsFor(String source) {
        if (MAKANI.equals(source)) {
            return List.of();
        }
        List<DistrictRef> districts = query.listScopes().stream()
                .map(r -> new DistrictRef(r.code, r.nameEn, r.makaniDistrictId, r.scopeHex))
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
