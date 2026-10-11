# AGENTS.md

## 构建 / 打包

- **不要执行 `gradlew assembleDebug` / `assembleRelease` 等打包命令。**
  签名配置在 Android Studio 里，命令行出的包没有正确签名，装上无法使用（会被当成盗版）。
  构建一律交给用户在 AS 中完成。
- 命令行构建会覆盖 `app\build\outputs\apk\debug\app-debug.apk`，该文件随后以 debug keystore 签名，**不是可发布的产物**。
- 真机验证前确认用户已用 AS 重新构建。

## 知识库（RAG）

- **唯一权威建库脚本：`D:\TIMU\kb_source\build_knowledge_db.py`**（不要在别处另存副本）。
- 工作流：改 `kb_source/*.md` → push 到 `DTxiaweibing/TIMU` main →
  `.github/workflows/build_knowledge_base.yml` 在 ubuntu 上自动跑该脚本 →
  产出 `knowledge_base.db` + `knowledge_base_version.json` 并提交 →
  客户端比对 `version` 变大后自动下载热更新。
- 因此**不需要手动提交生成的 db/json/kb_version.txt**，也**不要手动推 db**，CI 会产出。
- 同步方式：`git@github.com:DTxiaweibing/TIMU.git`（SSH）。**HTTPS 到 github.com:443 当前不通**，用 SSH。
- 本机另有 `D:\TIMU`（TIMU 仓库）与 `D:\shrimpfarm`（App 仓库）两个本地仓库。

### 关键约束（改动前必读）

- `doc_id` 由 `NAME_MAP` 决定，App 侧 `searchByDoc` 用 `startsWith(docIdPrefix)` 前缀路由，
  `routeQuery` 也按前缀分派模型 —— **改名会让线上路由失效**。
- 向量化必须与 `app\src\main\java\...\model\TokenEmbedder.java` 一致：
  **字符级分词 + CLS 池化 + L2 归一化，seq_len=512**。
  `BertTokenizer`/`WordPiece` 是误导性命名，实际按单字符查表。
- `kb_source\vocab.txt` 与 `assets\vocab.txt` 只有 CRLF/LF 差异，21127 项映射完全一致，可用任一。
- 切块目标：200~500 字语义块 + 60 字重叠（`T_MIN`/`T_MAX`/`OVERLAP`）。
  不要退回到按 doc_type 各写一套粗糙切分 —— 那曾导致 53% 碎块和 58136 字巨块。
- ARM64 上 ONNX 单条推理约 2~3 秒，**必须分桶批量推理**（`BATCH`），全量约 5 块/秒。
  单条推理跑完需一小时以上，不要这么跑。

## 数据库（Android）

- `DatabaseHelper` 的建表语句与 `onUpgrade` 迁移必须**同步维护**：
  只加 `case N` 的 `ALTER` 而不改 `CREATE TABLE`，会导致已处于新版本号的设备永远缺列（崩溃）。
  `onOpen` 中已有 `ensureColumn()` 用于幂等自愈，新增列时应同时更新建表语句。

## 源文件

- `shrimpfarm\kb_source` 与 `TIMU\kb_source` 的共有 9 个 md 逐字节一致，
  但 **`范老师徒弟班文字内容整理.md`（140KB，`lecture` 全部内容）只在 TIMU**。
  本地建库/校验请以 `D:\TIMU\kb_source` 为源。
