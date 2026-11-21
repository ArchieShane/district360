package com.district360.stat.write;

import com.district360.common.extract.SourceSnapshot;
import com.district360.common.extract.snapshot.AdrecSnapshot;
import com.district360.common.extract.snapshot.LiveabilitySnapshot;
import com.district360.common.extract.snapshot.MakaniSnapshot;
import com.district360.stat.mapper.StatWriteMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.function.Consumer;

/**
 * 把各源的快照写入统计库：每个源只动自己的表，整批替换，单事务。失败则整体回滚，旧数据保持不变。
 */
@Component
public class StatWriter {

    private static final int CHUNK = 500;

    private final StatWriteMapper mapper;
    private final TransactionTemplate tx;

    public StatWriter(StatWriteMapper mapper, @Qualifier("statTransactionTemplate") TransactionTemplate tx) {
        this.mapper = mapper;
        this.tx = tx;
    }

    public void write(SourceSnapshot snapshot) {
        if (snapshot instanceof MakaniSnapshot s) {
            writeMakani(s);
        } else if (snapshot instanceof LiveabilitySnapshot s) {
            writeLiveability(s);
        } else if (snapshot instanceof AdrecSnapshot s) {
            writeAdrec(s);
        } else {
            throw new IllegalArgumentException("不支持的快照类型: " + snapshot.getClass().getName());
        }
    }

    private void writeMakani(MakaniSnapshot s) {
        tx.executeWithoutResult(status -> {
            mapper.deleteAllDistricts();
            s.districts().forEach(mapper::insertDistrict);
            mapper.deleteAllMakani();
            chunked(s.districts(), list -> mapper.insertMakani(list, s.sourceAsOf()));
            mapper.deleteAllLandUse();
            chunked(s.landUse(), mapper::insertLandUse);
            mapper.deleteAllBuildings();
            chunked(s.buildings(), mapper::insertBuildings);
            mapper.deleteAllPlots();
            chunked(s.plots(), mapper::insertPlots);
            mapper.deleteAllPois();
            chunked(s.pois(), mapper::insertPois);
        });
    }

    private void writeLiveability(LiveabilitySnapshot s) {
        tx.executeWithoutResult(status -> {
            mapper.deleteAllLiveability();
            chunked(s.districts(), list -> mapper.insertLiveability(list, s.sourceAsOf()));
        });
    }

    private void writeAdrec(AdrecSnapshot s) {
        tx.executeWithoutResult(status -> {
            mapper.deleteAllAdrec();
            chunked(s.districts(), list -> mapper.insertAdrec(list, s));
            mapper.deleteAllAdrecMonthly();
            chunked(s.monthly(), mapper::insertAdrecMonthly);
            mapper.deleteAllAdrecCommunity();
            chunked(s.communityMappings(), mapper::insertAdrecCommunity);
        });
    }

    private static <T> void chunked(List<T> rows, Consumer<List<T>> insert) {
        for (int i = 0; i < rows.size(); i += CHUNK) {
            insert.accept(rows.subList(i, Math.min(rows.size(), i + CHUNK)));
        }
    }
}
