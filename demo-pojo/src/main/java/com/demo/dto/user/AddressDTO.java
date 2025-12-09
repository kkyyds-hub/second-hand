package com.demo.dto.user;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
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
    @NotBlank(message = "收件人姓名不能为空")
    @Size(min = 1, max = 50, message = "收件人姓名长度需在 1-50 字符之间")
    private String receiverName;

    /** 收货人手机号 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String mobile;

    /** 省份行政区划代码 */
    @NotBlank(message = "省份编码不能为空")
    private String provinceCode;

    /** 省份名称 */
    @NotBlank(message = "省份名称不能为空")
    private String provinceName;

    /** 城市行政区划代码 */
    @NotBlank(message = "城市编码不能为空")
    private String cityCode;

    /** 城市名称 */
    @NotBlank(message = "城市名称不能为空")
    private String cityName;

    /** 区 / 县行政区划代码 */
    @NotBlank(message = "区县编码不能为空")
    private String districtCode;

    /** 区 / 县名称 */
    @NotBlank(message = "区县名称不能为空")
    private String districtName;

    /** 详细地址 */
    @NotBlank(message = "详细地址不能为空")
    @Size(min = 1, max = 200, message = "详细地址长度需在 1-200 字符之间")
    private String detailAddress;

    /** 是否默认地址 */
    private Boolean isDefault;

    /** 最近更新时间（用于排序） */
    private LocalDateTime updatedAt;
}