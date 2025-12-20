package com.demo.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageResult<T> implements Serializable {

    @JsonProperty("list")
    private List<T> records;      // 对外输出 list

    private Long total;           // 对外输出 total

    private Integer page;         // 对外输出 page

    @JsonProperty("pageSize")
    private Integer size;         // 对外输出 pageSize

    @JsonIgnore
    private Integer pages;        // 不对外暴露，避免前端/AI误用

    public PageResult(List<T> records, Long total) {
        this.records = records;
        this.total = total;
    }

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
