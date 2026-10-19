# 推荐查询优化与执行计划验收

## 变更摘要

`RecommendationService` 的在线请求不再加载完整帖子、活动和评论表，也不再按互动、评论或报名记录循环执行 `findById()`。

当前查询上限：

- 帖子候选：100 条，按置顶、点赞、收藏和创建时间排序。
- 活动候选：80 条，仅查询审核通过且属于可推荐状态的记录。
- 点赞、收藏、评论和活动报名历史：每类最多 50 条。
- 用户自己发布的活动历史：最多 20 条。
- 最终帖子与活动卡片：分别最多 6 条和 4 条，作者、评论数和报名状态使用批量查询。

对应索引由 `V7__recommendation_query_indexes.sql` 创建。

## MySQL EXPLAIN 验收

在应用迁移到包含 V7 的测试数据库后执行：

```sql
EXPLAIN FORMAT=TREE
SELECT post_id, user_id, title, content, images, category,
       like_count, collect_count, is_top, create_time
FROM tb_post
ORDER BY is_top DESC, like_count DESC, collect_count DESC, create_time DESC
LIMIT 100;

EXPLAIN FORMAT=TREE
SELECT activity_id, user_id, title, content, activity_desc, activity_type,
       place, address, city, district, longitude, latitude, activity_time,
       max_num, sign_num, review_status, activity_status, status, create_time
FROM tb_activity
WHERE review_status = '1'
  AND activity_status IN ('2', '3', '4', '5')
ORDER BY create_time DESC
LIMIT 80;

EXPLAIN FORMAT=TREE
SELECT post_id
FROM tb_comment
WHERE user_id = 7
ORDER BY create_time DESC
LIMIT 50;

EXPLAIN FORMAT=TREE
SELECT target_id
FROM tb_interaction
WHERE user_id = 7 AND target_type = 'POST' AND type = 'LIKE'
ORDER BY create_time DESC
LIMIT 50;

EXPLAIN FORMAT=TREE
SELECT activity_id
FROM tb_activity_sign
WHERE user_id = 7
ORDER BY sign_time DESC
LIMIT 50;
```

验收重点：

- 候选查询读取行数受 `LIMIT` 控制。
- 活动查询先按 `review_status`、`activity_status` 过滤。
- 用户行为查询使用 V7 中对应的 `user_id` 组合索引。
- 执行计划不出现与整张候选表规模等量的应用层返回行数。

## 当前环境记录

2026-07-16 本机未安装 Docker，MySQL 8.0 服务存在但处于停止状态，因此本轮未启动或修改本机数据库。仓库已提供上述可复制的 MySQL 8 `EXPLAIN FORMAT=TREE` 验收脚本；应在隔离测试数据库或 CI 的 MySQL 服务中执行并保存输出。

## 回滚

- 应用代码可回滚推荐服务与 Repository 方法的同一提交。
- 索引如需单独回滚，应通过新的 Flyway 迁移执行 `DROP INDEX`，不得修改或删除已经执行过的 V7 文件。
