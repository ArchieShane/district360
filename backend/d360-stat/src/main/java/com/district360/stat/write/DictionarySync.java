package com.district360.stat.write;

import com.district360.common.district.DistrictDictionary;
import com.district360.stat.mapper.StatWriteMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时把 district-dictionary.yml 同步到 stat.dim_district（只更新字典字段，不动区面）。
 */
@Component
public class DictionarySync implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DictionarySync.class);

    private final DistrictDictionary dictionary;
    private final StatWriteMapper mapper;

    public DictionarySync(DistrictDictionary dictionary, StatWriteMapper mapper) {
        this.dictionary = dictionary;
        this.mapper = mapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        dictionary.all().forEach(mapper::upsertDictionary);
        log.info("区字典已同步到统计库：{} 个区", dictionary.all().size());
    }
}
