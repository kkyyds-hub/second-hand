package com.demo.vo.address;

import lombok.Data;

/**
 * 用户地址视图对象。
 */
@Data
public class AddressVO {


    /** 地址记录主键 */
    private Long id;

    /** 收货人姓名 */
    private String receiverName;

    /** 收货人手机号 */
    private String mobile;

    /** 省份行政区划代码 */
    private String provinceCode;

    /** 省份名称 */
    private String provinceName;

    /** 城市行政区划代码 */
    private String cityCode;

    /** 城市名称 */
    private String cityName;

    /** 区 / 县行政区划代码 */
    private String districtCode;

    /** 区 / 县名称 */
    private String districtName;

    /** 详细地址 */
    private String detailAddress;

    /** 是否默认地址 */
    private Boolean isDefault;
}