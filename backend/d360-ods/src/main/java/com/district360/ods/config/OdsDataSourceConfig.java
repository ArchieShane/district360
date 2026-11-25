package com.district360.ods.config;

import com.district360.ods.extract.OdsSpatialExtractor;
import com.district360.ods.extract.OdsUnitExtractor;
import com.district360.ods.mapper.OdsSpatialMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * ODS 源库（只读，只给 ODS 抽取器用）。配置了 d360.datasource.ods.url 才启用。
 * <p>
 * 连接关闭自动提交：PostgreSQL JDBC 只有在事务内才按 fetchSize 用游标分批取数，
 * 否则会把整张表（几十万行带几何）一次读进内存。
 */
@Configuration
@ConditionalOnProperty(prefix = "d360.datasource.ods", name = "url")
@EnableConfigurationProperties(OdsProperties.class)
@MapperScan(basePackages = "com.district360.ods.mapper", sqlSessionFactoryRef = "odsSqlSessionFactory")
public class OdsDataSourceConfig {

    @Bean
    @ConfigurationProperties("d360.datasource.ods")
    public DataSourceProperties odsDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("d360.datasource.ods.hikari")
    public HikariDataSource odsDataSource(@Qualifier("odsDataSourceProperties") DataSourceProperties properties) {
        HikariDataSource ds = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        ds.setReadOnly(true);
        ds.setAutoCommit(false);
        ds.setMaximumPoolSize(2);
        ds.setMinimumIdle(0);
        return ds;
    }

    @Bean
    public SqlSessionFactory odsSqlSessionFactory(@Qualifier("odsDataSource") DataSource dataSource,
                                                  OdsProperties properties) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/ods/*.xml"));
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setDefaultFetchSize(properties.getFetchSize());
        configuration.setDefaultStatementTimeout(properties.getStatementTimeoutSeconds());
        factory.setConfiguration(configuration);
        return factory.getObject();
    }

    @Bean
    public OdsSpatialExtractor odsSpatialExtractor(OdsSpatialMapper mapper, OdsProperties properties) {
        return new OdsSpatialExtractor(mapper, properties);
    }

    @Bean
    public OdsUnitExtractor odsUnitExtractor(OdsSpatialMapper mapper, OdsProperties properties) {
        return new OdsUnitExtractor(mapper, properties);
    }
}
