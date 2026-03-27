package com.code.aigateway.infra.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * MyBatis 配置类。
 * <p>
 * 当前主要负责扫描后台管理模块的 Mapper，
 * 后续如需注册 JSON 字段 TypeHandler，可在此处继续扩展。
 * </p>
 */
@Configuration
@MapperScan("com.code.aigateway.admin.mapper")
public class MybatisConfig {

    /**
     * 提供编程式事务模板，便于在“先提交数据库、再刷新运行时快照”的场景中精细控制事务边界。
     */
    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    // 预留：后续可在此注册 JSON TypeHandler 等自定义配置。
}
