package com.demo.enumeration;

/**
 * 用户信用等级枚举
 * 等级阈值（闭区间 [minScore, maxScore]）：
 * - LV1: 0 ~ 39
 * - LV2: 40 ~ 79
 * - LV3: 80 ~ 119
 * - LV4: 120 ~ 159
 * - LV5: 160 ~ 200
 */
public enum CreditLevel {

    LV1("lv1", "等级1", 0, 39),
    LV2("lv2", "等级2", 40, 79),
    LV3("lv3", "等级3", 80, 119),
    LV4("lv4", "等级4", 120, 159),
    LV5("lv5", "等级5", 160, 200);

    private final String dbValue;
    private final String cnName;
    private final int minScore;
    private final int maxScore;

    CreditLevel(String dbValue, String cnName, int minScore, int maxScore) {
        this.dbValue = dbValue;
        this.cnName = cnName;
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public String getDbValue() {
        return dbValue;
    }

    public String getCnName() {
        return cnName;
    }

    public int getMinScore() {
        return minScore;
    }

    public int getMaxScore() {
        return maxScore;
    }

    /**
     * 根据分数映射等级（边界处理：分数超界按边界夹取）
     * 分数范围：0 ~ 200（闭区间）
     * 如果分数 < 0，返回 LV1
     * 如果分数 > 200，返回 LV5
     *
     * @param score 信用分数
     * @return 对应的信用等级
     */
    public static CreditLevel fromScore(int score) {
        if (score < 0) {
            return LV1;
        }
        if (score > 200) {
            return LV5;
        }
        for (CreditLevel level : values()) {
            if (score >= level.minScore && score <= level.maxScore) {
                return level;
            }
        }
        // 理论上不会到这里，但为了安全返回 LV3
        return LV3;
    }

    /**
     * 严格模式：仅接受 dbValue（给 Service/DB 用）
     * 非法值直接抛 IllegalArgumentException
     *
     * @param dbValue 数据库存储值（如 "lv1", "lv2" 等）
     * @return 对应的信用等级
     * @throws IllegalArgumentException 如果 dbValue 不合法
     */
    public static CreditLevel fromDbValue(String dbValue) {
        if (dbValue == null || dbValue.trim().isEmpty()) {
            throw new IllegalArgumentException("信用等级不能为空");
        }
        String s = dbValue.trim();
        for (CreditLevel level : values()) {
            if (level.dbValue.equalsIgnoreCase(s)) {
                return level;
            }
        }
        throw new IllegalArgumentException("非法信用等级(dbValue): " + dbValue);
    }

    /**
     * Controller 常用：把任何输入统一转成 dbValue
     */
    public static String normalizeToDbValue(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("信用等级不能为空");
        }
        String s = input.trim();
        
        // 先匹配 dbValue
        for (CreditLevel level : values()) {
            if (level.dbValue.equalsIgnoreCase(s)) {
                return level.dbValue;
            }
            if (level.cnName.equals(s)) {
                return level.dbValue;
            }
            if (level.name().equalsIgnoreCase(s)) {
                return level.dbValue;
            }
        }
        
        throw new IllegalArgumentException("非法信用等级: " + input);
    }
}

