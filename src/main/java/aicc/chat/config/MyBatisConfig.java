package aicc.chat.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;

/**
 * MyBatis 설정 클래스
 * SQL 로깅 인터셉터를 등록합니다.
 */
@Slf4j
@Configuration
public class MyBatisConfig {

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        log.info("▼ sqlSessionFactory");
        SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);

        // Mapper XML 위치 설정
        sessionFactory.setMapperLocations(
            new PathMatchingResourcePatternResolver().getResources("classpath:mybatis/mapper/**/*.xml")
        );

        // Type Aliases 패키지 설정
        sessionFactory.setTypeAliasesPackage("aicc.chat.domain.persistence");

        // SQL 로깅 인터셉터 등록
        sessionFactory.setPlugins(new MyBatisSqlLogger());

        // MyBatis Configuration 설정
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setDefaultFetchSize(100);
        configuration.setDefaultStatementTimeout(30);
        configuration.setCacheEnabled(true);
        configuration.setLazyLoadingEnabled(false);
        configuration.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.SESSION);

        sessionFactory.setConfiguration(configuration);

        return sessionFactory.getObject();
    }
}
