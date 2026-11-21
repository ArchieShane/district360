package com.district360.liveability.config;

import com.district360.liveability.extract.LiveabilityExtractor;
import com.district360.liveability.mapper.LiveabilityMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * liveability 源库（只读，只给抽取器用）。配置了 d360.datasource.liveability.url 才启用；
 * 没配置时统计库里保留开发期样例数据（页面标「样例数值」）。
 */
@Configuration
@ConditionalOnProperty(prefix = "d360.datasource.liveability", name = "url")
@MapperScan(basePackages = "com.district360.liveability.mapper", sqlSessionFactoryRef = "liveabilitySqlSessionFactory")
public class LiveabilityDataSourceConfig {

    @Bean
    @ConfigurationProperties("d360.datasource.liveability")
    public DataSourceProperties liveabilityDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("d360.datasource.liveability.hikari")
    public HikariDataSource liveabilityDataSource(@Qualifier("liveabilityDataSourceProperties") DataSourceProperties properties) {
        HikariDataSource ds = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        ds.setReadOnly(true);
        ds.setMaximumPoolSize(2);
        ds.setMinimumIdle(0);
        return ds;
    }

    @Bean
    public SqlSessionFactory liveabilitySqlSessionFactory(@Qualifier("liveabilityDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/liveability/*.xml"));
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        return factory.getObject();
    }

    @Bean
    public LiveabilityExtractor liveabilityExtractor(LiveabilityMapper mapper) {
        return new LiveabilityExtractor(mapper);
    }
}
