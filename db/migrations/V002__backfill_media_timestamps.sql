-- 回填类型表里缺失的时间戳。
--
-- 背景
--   gallery 表插入时用 NOW() 由数据库填写时间，而 photo / gif / video / music 四张表
--   把 created_at / updated_at 当参数传入，调用方漏设就写进 NULL ——
--   列上的 DEFAULT CURRENT_TIMESTAMP 对**显式 NULL 不生效**，所以它挡不住。
--   这些行在后台资源浏览器里显示成「未知时间」。
--
-- 回填来源
--   同一份媒体在 gallery 表里有对应行（两表以 src 关联，没有外键、没有唯一约束），
--   取它的时间即可 —— 那是同一次上传写进去的，本来就是同一时刻。
--
-- 查不到对应 gallery 行的历史数据保持 NULL：不编造时间，界面会如实显示「未知时间」。
-- 正因为可能仍有 NULL，这次不加 NOT NULL 约束（加了会直接失败）；
-- 防复发靠代码 —— 四张类型表的 insert 已改成和 gallery 一致，用 NOW()。

UPDATE photo p
  JOIN gallery g ON g.src = p.src
  SET p.created_at = g.created_at,
      p.updated_at = g.updated_at
  WHERE p.created_at IS NULL;

UPDATE gif gi
  JOIN gallery g ON g.src = gi.src
  SET gi.created_at = g.created_at,
      gi.updated_at = g.updated_at
  WHERE gi.created_at IS NULL;

UPDATE video v
  JOIN gallery g ON g.src = v.src
  SET v.created_at = g.created_at,
      v.updated_at = g.updated_at
  WHERE v.created_at IS NULL;

UPDATE music m
  JOIN gallery g ON g.src = m.src
  SET m.created_at = g.created_at,
      m.updated_at = g.updated_at
  WHERE m.created_at IS NULL;
