package com.demo.service;

import com.demo.vo.admin.AdminDashboardOverviewVO;

import java.time.LocalDate;

/**
 * 管理后台首页聚合服务。
 *
 * 作用：
 * 1. 把多个已有业务接口/统计能力聚合成一个首页总览数据包；
 * 2. 避免前端首页为了展示一个 Dashboard 发起过多请求；
 * 3. 保持首页的数据结构稳定，便于后续逐步扩展。
 */
public interface AdminDashboardService {

    /**
     * 查询指定日期的管理后台总览。
     *
     * @param date 统计日期
     * @return 首页工作台总览数据
     */
    AdminDashboardOverviewVO getOverview(LocalDate date);
}
