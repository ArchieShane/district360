package com.district360.stat.config;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * 统计库数据源（主数据源）。接口只读这个库；Flyway 也只管这个库。
 */
@Configuration
@MapperScan(basePackages = "com.district360.stat.mapper", sqlSessionFactoryRef = "statSqlSessionFactory")
public class StatDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("d360.datasource.stat")
    public DataSourceProperties statDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @FlywayDataSource
    @ConfigurationProperties("d360.datasource.stat.hikari")
    public HikariDataSource statDataSource(@Qualifier("statDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    public SqlSessionFactory statSqlSessionFactory(@Qualifier("statDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/stat/*.xml"));
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        return factory.getObject();
    }

    @Bean
    @Primary
    public PlatformTransactionManager statTransactionManager(@Qualifier("statDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public TransactionTemplate statTransactionTemplate(@Qualifier("statTransactionManager") PlatformTransactionManager tm) {
        return new TransactionTemplate(tm);
    }
}
