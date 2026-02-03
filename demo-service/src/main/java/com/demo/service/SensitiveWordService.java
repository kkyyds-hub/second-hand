package com.demo.service;

/**
 * Day13 Step6 - 敏感词检测服务
 */
public interface SensitiveWordService {

    /**
     * 检测文本是否包含敏感词
     * @param text 待检测文本
     * @return true=包含敏感词，false=无敏感词
     */
    boolean containsSensitiveWord(String text);

    /**
     * 获取文本中的敏感词列表（用于记录违规）
     * @param text 待检测文本
     * @return 敏感词列表
     */
    String getMatchedWords(String text);
}
