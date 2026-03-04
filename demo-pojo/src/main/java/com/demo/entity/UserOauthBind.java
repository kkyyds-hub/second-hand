package com.demo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户第三方账号绑定实体（DB 真源）。
 *
 * 说明：
 * 1) provider + externalId 表示“第三方平台侧唯一账号”；
 * 2) userId 表示“本系统本地用户”；
 * 3) Redis 里的 auth:oauth:* 仅作为加速缓存，最终一致口径以本表为准。
 */
@Data
public class UserOauthBind extends BaseAuditEntity {

    /** 主键 ID。 */
    private Long id;

    /** 本地用户 ID（关联 users.id）。 */
    private Long userId;

    /** 第三方平台类型（wechat/alipay/github/google...）。 */
    private String provider;

    /** 第三方平台唯一用户标识（openId/sub 等）。 */
    private String externalId;

    /** 绑定状态：1=已绑定，0=已解绑。 */
    private Integer bindStatus;

    /** 最近一次第三方登录时间（用于审计与风控补充）。 */
    private LocalDateTime lastLoginTime;

    /** 备注（预留）。 */
    private String remark;
}

