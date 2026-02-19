package com.demo.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户视图对象。
 */
@Data
public class UserVO {
    /** 用户 ID。 */
    private Long id;
    /** 用户名。 */
    private String username;
    /** 手机号。 */
    private String mobile;
    /** 头像 URL。 */
    private String avatar;
    /** 昵称。 */
    private String nickname;
    /** 个性签名。 */
    private String bio;
    /** 邮箱。 */
    private String email;
    /** 注册时间。 */
    private LocalDateTime registerTime;
    /** 最近登录 IP。 */
    private String lastLoginIp;
    /** 信用分。 */
    private Integer creditScore;
    /** 发布商品数量。 */
    private Integer productCount;
    /** 用户状态。 */
    private String status;
    /** 地区。 */
    private String region;
}
