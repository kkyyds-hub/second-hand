START TRANSACTION;

INSERT INTO products (
    owner_id,
    title,
    description,
    price,
    images,
    category,
    status,
    view_count,
    reason,
    is_deleted,
    create_time,
    update_time
)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1
    FROM seq
    WHERE n < 36
)
SELECT
    CASE
        WHEN MOD(s.n, 3) = 1 THEN 2
        WHEN MOD(s.n, 3) = 2 THEN 6
        ELSE 7
    END AS owner_id,
    CONCAT('DAY19-P3-S2-CACHE-', LPAD(s.n, 3, '0')) AS title,
    CONCAT(
        'Day19 P3-S2 cache seed #', LPAD(s.n, 3, '0'),
        ' | warm/cold comparison | owner=',
        CASE
            WHEN MOD(s.n, 3) = 1 THEN '2'
            WHEN MOD(s.n, 3) = 2 THEN '6'
            ELSE '7'
        END,
        ' | category=',
        CASE
            WHEN MOD(s.n, 3) = 1 THEN 'Day19-A'
            WHEN MOD(s.n, 3) = 2 THEN 'Day19-B'
            ELSE 'Day19-C'
        END
    ) AS description,
    CASE
        WHEN MOD(s.n, 3) = 1 THEN 99.00
        WHEN MOD(s.n, 3) = 2 THEN 149.00
        ELSE 199.00
    END AS price,
    'https://img.demo/day19/p3s2-1.png,https://img.demo/day19/p3s2-2.png' AS images,
    CASE
        WHEN MOD(s.n, 3) = 1 THEN 'Day19-A'
        WHEN MOD(s.n, 3) = 2 THEN 'Day19-B'
        ELSE 'Day19-C'
    END AS category,
    'on_sale' AS status,
    0 AS view_count,
    NULL AS reason,
    0 AS is_deleted,
    TIMESTAMPADD(MINUTE, s.n, '2026-03-08 10:00:00') AS create_time,
    TIMESTAMPADD(MINUTE, s.n, '2026-03-08 10:00:00') AS update_time
FROM seq s
WHERE NOT EXISTS (
    SELECT 1
    FROM products p
    WHERE p.title = CONCAT('DAY19-P3-S2-CACHE-', LPAD(s.n, 3, '0'))
);

COMMIT;
