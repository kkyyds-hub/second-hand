package com.demo.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageResult<T> implements Serializable {

    private List<T> records;      // 当前页数据（泛型安全）
    private Long total;           // 总记录数
    private Integer page;         // 当前页码
    private Integer size;         // 每页大小
    private Integer pages;        // 总页数（自动计算）

    // 便捷构造方法
    public PageResult(List<T> records, Long total) {
        this.records = records;
        this.total = total;
    }

    public PageResult(List<T> records, Long total, Integer page, Integer size) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
        this.pages = (int) Math.ceil((double) total / size);
    }
}