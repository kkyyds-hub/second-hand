package com.demo.entity;


import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 收货地址实体
 */
@Data
public class Address {

    private Long id;                    // 地址 ID
    private Long userId;                // 用户 ID
    private String receiverName;        // 收货人姓名
    private String mobile;              // 联系电话
    private String provinceCode;        // 省份行政区划代码
    private String provinceName;        // 省份名称
    private String cityCode;            // 城市行政区划代码
    private String cityName;            // 城市名称
    private String districtCode;        // 区县行政区划代码
    private String districtName;        // 区县名称
    private String detailAddress;       // 详细地址
    private Boolean isDefault;          // 是否为默认地址

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;   // 创建时间

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;   // 更新时间
}
