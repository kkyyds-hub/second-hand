# Day16 注释规范全量快扫记录（2026-02-19）

## 扫描范围
1. `demo-service`、`demo-common`、`demo-pojo` 全部 `*.java`。
2. `demo-service/src/main/resources/mapper` 全部 `*.xml`。
3. 回归目录 `day*`（用于术语/乱码扫描）。

## 分批扫描
1. 批次 A：基础门禁
- `ZERO_COMMENT`
- Java 注释密度 `<10%`
- `P0`（controller/service/serviceimpl）`public` 方法 JavaDoc
2. 批次 B：结构门禁
- 类级注释（class/interface/enum/record）
- Mapper 方法注释
- Mapper XML 语句注释
- MQ/Job 入口注释
- 模型字段注释（entity/dto/vo/enumeration）
3. 批次 C：文本门禁
- 术语漂移
- 乱码字符

## 最终结果
1. `JAVA_TOTAL=280`
2. `ZERO_COMMENT=0`
3. `LOW_DENSITY_LT10=0`
4. `P0_PUBLIC_MISSING_JAVADOC=0`
5. `CLASS_MISSING_COMMENT=0`
6. `MAPPER_METHOD_MISSING_COMMENT_STRICT=0`
7. `MODEL_FIELD_MISSING_COMMENT_STRICT=0`
8. `XML_STMT_MISSING_COMMENT_STRICT=0`
9. `MQJOB_ENTRY_MISSING_COMMENT_STRICT=0`
10. `TERM_DRIFT_HITS=0`
11. `GARBLED_HITS=0`

## 产物
1. `day16回归/注释规范/03_扫描产出/Day16_gate_summary.json`
2. `day16回归/注释规范/03_扫描产出/Day16_strict_scan_summary.json`
3. `day16回归/注释规范/03_扫描产出/Day16_class_missing_comment.csv`
4. `day16回归/注释规范/03_扫描产出/Day16_p0_public_missing_javadoc_strict.csv`
5. `day16回归/注释规范/03_扫描产出/Day16_mapper_method_missing_comment_strict.csv`
6. `day16回归/注释规范/03_扫描产出/Day16_model_field_missing_comment_strict.csv`
7. `day16回归/注释规范/03_扫描产出/Day16_xml_missing_comment_strict.csv`
8. `day16回归/注释规范/03_扫描产出/Day16_mqjob_missing_comment_strict.csv`
