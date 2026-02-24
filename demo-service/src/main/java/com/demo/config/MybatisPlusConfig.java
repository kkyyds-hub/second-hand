package com.demo.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.plugin.Interceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 统一配置入口。
 */
@Configuration
public class MybatisPlusConfig {

    private static final long PAGE_MAX_LIMIT = 100L;
    private static final boolean PAGE_OVERFLOW_TO_FIRST = false;

    /**
     * 注册 MP 插件链。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 安全基线：阻断无条件 update/delete
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        // 分页策略：限制单页大小，页码越界不自动回第一页
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInnerInterceptor.setMaxLimit(PAGE_MAX_LIMIT);
        paginationInnerInterceptor.setOverflow(PAGE_OVERFLOW_TO_FIRST);

        // 按官方建议，分页插件放在最后
        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        return interceptor;
    }

    /**
     * 分页混用守卫：禁止同一查询链路同时启用 PageHelper 与 MP 分页。
     */
    @Bean
    public Interceptor paginationMixGuardInterceptor() {
        return new PaginationMixGuardInterceptor();
    }
}
