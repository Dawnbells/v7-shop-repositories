package cn.v7soft.dao.properties;

import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.zaxxer.hikari.HikariDataSource;

import jakarta.persistence.EntityManagerFactory;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "cn.v7soft.dao.repositories.address",
        entityManagerFactoryRef = "addressEntityManagerFactory",
        transactionManagerRef = "addressTransactionManager"
)
public class AddressDataSourceConfig {

    @Bean(name = "addressDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.address")
    public DataSource addressDataSource() {
        return new HikariDataSource();
    }

    @Bean(name = "addressEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean addressEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("addressDataSource") DataSource dataSource) {

        Map<String, Object> hibernateProps = new HashMap<>();
        hibernateProps.put("hibernate.multiTenancy", "NONE");  // 显式禁用多租户

        return builder
                .dataSource(dataSource)
                .packages("cn.v7soft.dao.entities.address")
                .persistenceUnit("addressPU")
                .properties(hibernateProps)
                .build();
    }

    @Bean(name = "addressTransactionManager")
    public PlatformTransactionManager addressTransactionManager(
            @Qualifier("addressEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}