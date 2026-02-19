package com.demo.service.serviceimpl;

import com.demo.service.SensitiveWordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * Day13 Step6 - 敏感词检测实现（DFA 算法）
 * 词库：本地配置（可扩展为从 DB 加载）
 */
@Service
@Slf4j
public class SensitiveWordServiceImpl implements SensitiveWordService {

    /**
     * 敏感词树（DFA）
     */
    private Map<String, Object> sensitiveWordTree;

    /**
     * Day13 敏感词库（示例）
     * 实际项目中可从 DB/配置文件加载
     */
    private static final String[] SENSITIVE_WORDS = {
            "黄赌毒", "枪支", "毒品", "赌博", "色情",
            "诈骗", "传销", "非法集资", "高仿", "假货",
            "盗版", "走私", "洗钱", "贩毒", "赌场"
    };

    @PostConstruct
    /**
     * 初始化敏感词缓存。
     */
    public void init() {
        sensitiveWordTree = new HashMap<>();
        for (String word : SENSITIVE_WORDS) {
            addWord(word);
        }
        log.info("敏感词库初始化完成，共 {} 个词", SENSITIVE_WORDS.length);
    }

    /**
     * 添加敏感词到树
     */
    private void addWord(String word) {
        if (word == null || word.isEmpty()) {
            return;
        }
        Map<String, Object> current = sensitiveWordTree;
        for (char c : word.toCharArray()) {
            String key = String.valueOf(c);
            if (!current.containsKey(key)) {
                current.put(key, new HashMap<String, Object>());
            }
            current = (Map<String, Object>) current.get(key);
        }
        current.put("isEnd", true);
    }

    /**
     * 实现接口定义的方法。
     */
    @Override
    public boolean containsSensitiveWord(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        for (int i = 0; i < text.length(); i++) {
            int matchLength = matchWord(text, i);
            if (matchLength > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 查询并返回相关结果。
     */
    @Override
    public String getMatchedWords(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        Set<String> matchedWords = new HashSet<>();
        for (int i = 0; i < text.length(); i++) {
            int matchLength = matchWord(text, i);
            if (matchLength > 0) {
                matchedWords.add(text.substring(i, i + matchLength));
            }
        }

        return String.join(", ", matchedWords);
    }

    /**
     * 从指定位置开始匹配敏感词
     * @param text 文本
     * @param startIndex 起始位置
     * @return 匹配长度（0 表示未匹配）
     */
    private int matchWord(String text, int startIndex) {
        Map<String, Object> current = sensitiveWordTree;
        int matchLength = 0;

        for (int i = startIndex; i < text.length(); i++) {
            String key = String.valueOf(text.charAt(i));
            if (!current.containsKey(key)) {
                break;
            }
            matchLength++;
            current = (Map<String, Object>) current.get(key);

            if (current.containsKey("isEnd") && (Boolean) current.get("isEnd")) {
                return matchLength;
            }
        }

        return 0;
    }
}
