package com.district360.makani.config;

import com.district360.makani.extract.MakaniExtractor;
import com.district360.makani.mapper.MakaniMapper;
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
 * makani 源库（只读，只给抽取器用）。配置了 d360.datasource.makani.url 才启用。
 */
@Configuration
@ConditionalOnProperty(prefix = "d360.datasource.makani", name = "url")
@EnableConfigurationProperties(MakaniProperties.class)
@MapperScan(basePackages = "com.district360.makani.mapper", sqlSessionFactoryRef = "makaniSqlSessionFactory")
public class MakaniDataSourceConfig {

    @Bean
    @ConfigurationProperties("d360.datasource.makani")
    public DataSourceProperties makaniDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("d360.datasource.makani.hikari")
    public HikariDataSource makaniDataSource(@Qualifier("makaniDataSourceProperties") DataSourceProperties properties) {
        HikariDataSource ds = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        ds.setReadOnly(true);
        ds.setMaximumPoolSize(2);
        ds.setMinimumIdle(0);
        return ds;
    }

    @Bean
    public SqlSessionFactory makaniSqlSessionFactory(@Qualifier("makaniDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/makani/*.xml"));
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setDefaultStatementTimeout(600);
        factory.setConfiguration(configuration);
        return factory.getObject();
    }

    @Bean
    public MakaniExtractor makaniExtractor(MakaniMapper mapper, MakaniProperties properties) {
        return new MakaniExtractor(mapper, properties);
    }
}
