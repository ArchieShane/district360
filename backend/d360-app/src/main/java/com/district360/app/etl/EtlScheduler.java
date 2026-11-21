package com.district360.app.etl;

import com.district360.app.config.EtlProperties;
import com.district360.stat.mapper.StatQueryMapper;
import com.district360.stat.row.SourceStatusRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每日定时抽取；首次启动时如果 makani 统计表为空，后台补跑一次。 */
@Component
public class EtlScheduler {

    private static final Logger log = LoggerFactory.getLogger(EtlScheduler.class);

    private final EtlService etl;
    private final EtlProperties props;
    private final StatQueryMapper query;

    public EtlScheduler(EtlService etl, EtlProperties props, StatQueryMapper query) {
        this.etl = etl;
        this.props = props;
        this.query = query;
    }

    @Scheduled(cron = "${d360.etl.cron}", zone = "${d360.etl.zone}")
    public void daily() {
        if (!props.enabled()) {
            return;
        }
        log.info("每日抽取开始：{}", etl.sources());
        etl.runAll().forEach(r -> log.info("每日抽取结果：{}", r));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!props.enabled() || !props.runMakaniOnStartupIfEmpty() || !etl.has("makani")) {
            return;
        }
        boolean empty = query.listSourceStatus().stream()
                .filter(s -> "makani".equals(s.source))
                .mapToInt((SourceStatusRow s) -> s.rowCount)
                .sum() == 0;
        if (empty) {
            log.info("makani 统计表为空，后台执行首次抽取");
            Thread t = new Thread(() -> etl.run("makani"), "etl-makani-startup");
            t.setDaemon(true);
            t.start();
        }
    }
}
