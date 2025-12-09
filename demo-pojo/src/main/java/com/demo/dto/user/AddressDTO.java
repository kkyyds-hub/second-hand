package com.demo.dto.user;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户地址 DTO
 *
 * 用于在服务层/持久层之间传递用户地址数据。
 */
@Data
public class AddressDTO {

    /** 地址记录主键 */
    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 收货人姓名 */
    private String receiverName;

    /** 收货人手机号 */
    private String mobile;

    /** 省份名称 */
    private String provinceName;

    /** 城市名称 */
    private String cityName;

    /** 区 / 县名称 */
    private String districtName;

    /** 详细地址 */
    private String detailAddress;

    /** 是否默认地址 */
    private Boolean isDefault;

    /** 最近更新时间（用于排序） */
    private LocalDateTime updatedAt;
}