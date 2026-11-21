package com.district360.adrec.config;

import com.district360.adrec.extract.AdrecExtractor;
import com.district360.adrec.mapper.AdrecMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * adrec 源库（只读，只给抽取器用）。配置了 d360.datasource.adrec.url 才启用；
 * 未配置时不启用 ADREC 抽取。
 */
@Configuration
@ConditionalOnProperty(prefix = "d360.datasource.adrec", name = "url")
@MapperScan(basePackages = "com.district360.adrec.mapper", sqlSessionFactoryRef = "adrecSqlSessionFactory")
public class AdrecDataSourceConfig {

    @Bean
    @ConfigurationProperties("d360.datasource.adrec")
    public DataSourceProperties adrecDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("d360.datasource.adrec.hikari")
    public HikariDataSource adrecDataSource(
            @Qualifier("adrecDataSourceProperties") DataSourceProperties properties,
            @Value("${d360.datasource.adrec.statement-timeout-seconds:1800}") int statementTimeoutSeconds) {
        HikariDataSource ds = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        ds.setReadOnly(true);
        // 兜底：连接中途静默断开时，驱动在语句超时之后再多等 2 分钟就放弃，不会让抽取线程一直占着抽取锁
        ds.addDataSourceProperty("socketTimeout", String.valueOf(statementTimeoutSeconds + 120));
        ds.addDataSourceProperty("connectTimeout", "15");
        ds.setConnectionInitSql("SET TIME ZONE 'Asia/Dubai'");
        ds.setMaximumPoolSize(2);
        ds.setMinimumIdle(0);
        return ds;
    }

    @Bean
    public SqlSessionFactory adrecSqlSessionFactory(
            @Qualifier("adrecDataSource") DataSource dataSource,
            @Value("${d360.datasource.adrec.statement-timeout-seconds:1800}") int statementTimeoutSeconds) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/adrec/*.xml"));
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        // 单条语句最长执行时间（秒），超时抛错、抽取记为失败；可用 d360.datasource.adrec.statement-timeout-seconds 调整
        configuration.setDefaultStatementTimeout(statementTimeoutSeconds);
        factory.setConfiguration(configuration);
        return factory.getObject();
    }

    @Bean
    public AdrecExtractor adrecExtractor(
            AdrecMapper mapper,
            @Qualifier("adrecDataSource") DataSource dataSource,
            @Value("${d360.datasource.adrec.source-srid}") int sourceSrid) {
        TransactionTemplate readTransaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        readTransaction.setReadOnly(true);
        readTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return new AdrecExtractor(mapper, readTransaction, sourceSrid);
    }
}
