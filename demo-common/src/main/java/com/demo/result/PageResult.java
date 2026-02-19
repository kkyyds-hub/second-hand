package com.demo.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 通用分页返回模型。
 *
 * @param <T> 列表元素类型
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageResult<T> implements Serializable {

    /** 当前页数据列表，对外字段名为 list。 */
    @JsonProperty("list")
    private List<T> records;

    /** 总记录数。 */
    private Long total;

    /** 当前页码（从 1 开始）。 */
    private Integer page;

    /** 每页条数，对外字段名为 pageSize。 */
    @JsonProperty("pageSize")
    private Integer size;

    /** 总页数，仅内部使用，不对外输出。 */
    @JsonIgnore
    private Integer pages;

    /**
     * 仅包含数据列表与总数的构造器。
     */
    public PageResult(List<T> records, Long total) {
        this.records = records;
        this.total = total;
    }

    /**
     * 包含完整分页信息的构造器。
     */
    public PageResult(List<T> records, Long total, Integer page, Integer size) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
        if (size != null && size > 0 && total != null) {
            this.pages = (int) Math.ceil((double) total / size);
        }
    }
}
