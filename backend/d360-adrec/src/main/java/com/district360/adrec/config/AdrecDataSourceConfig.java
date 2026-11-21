package com.district360.adrec.config;

import com.district360.adrec.extract.AdrecExtractor;
import com.district360.adrec.mapper.AdrecMapper;
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
 * adrec 源库（只读，只给抽取器用）。配置了 d360.datasource.adrec.url 才启用；
 * 没配置时统计库里保留开发期样例数据（页面标「样例数值」）。
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
    public HikariDataSource adrecDataSource(@Qualifier("adrecDataSourceProperties") DataSourceProperties properties) {
        HikariDataSource ds = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        ds.setReadOnly(true);
        ds.setMaximumPoolSize(2);
        ds.setMinimumIdle(0);
        return ds;
    }

    @Bean
    public SqlSessionFactory adrecSqlSessionFactory(@Qualifier("adrecDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/adrec/*.xml"));
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        return factory.getObject();
    }

    @Bean
    public AdrecExtractor adrecExtractor(AdrecMapper mapper) {
        return new AdrecExtractor(mapper);
    }
}
