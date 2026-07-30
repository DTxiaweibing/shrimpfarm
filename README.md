# 小棚养虾 | ShrimpFarm

Android 小棚养殖全流程管理 App | A full-cycle management app for shed-based shrimp farming.

[![Language: Java](https://img.shields.io/badge/language-Java-orange)](https://java.com)
[![Min SDK: 28](https://img.shields.io/badge/minSdk-28-brightgreen)](https://developer.android.com/studio)
[![Target SDK: 33](https://img.shields.io/badge/targetSdk-36-brightgreen)](https://developer.android.com/studio)
[![License](https://img.shields.io/badge/license-GPLv3-blue)](LICENSE)

## 功能 | Features

### 养殖管理 | Farming Management
| 功能 | 说明 |
|------|------|
| **基础数据** | 做水日/放苗日/养殖品种等参数设置 |
| **批次管理** | 多批次并行养殖，独立管理 |
| **投喂记录** | 每日四餐投喂量记录（早/中/晚/夜宵） |
| **水质管理** | pH/氨氮/亚硝酸盐/总碱度/硫化氢/ORP/溶氧等全面检测 |
| **巡塘检查** | 查料台、吃料时间、加料建议 |
| **拌料计算** | 干料/粉料/发酵料/水分配比，支持动态列配置 |
| **数据分析** | 投喂/水质趋势图表，料比计算 |
| **任务计划** | 养殖任务提醒与排程 |

### 辅助工具 | Utility Tools
| 功能 | 说明 |
|------|------|
| **海水密度计算器** | 温度/盐度/密度三参数互算（EOS-80） |
| **水质调控看板** | 动态模拟 pH/氨氮/温度对养殖的影响 |
| **智能问答** | 接入知识库的 RAG 问答系统 |
| **专家系统** | 基于规则的水质异常处理建议 |
| **行情资讯** | 对虾市场价格走势 |
| **智能助手** | 可配置的告警代理（加料/超时/水质等） |

### 系统特性 | System Features
- **中英双语** — 跟随系统或手动切换 | i18n: Chinese & English
- **本地备份** — 导出到手机 Downloads
- **云端备份** — 坚果云 WebDAV 自动/手动同步
- **知识库** — 本地向量化知识库，支持语义检索

## 技术栈 | Tech Stack

| 类别 | 技术 |
|------|------|
| 语言 | Java 11 |
| 最低 SDK | 28 (Android 9) |
| 目标 SDK | 36 |
| 编译 SDK | 36 |
| 数据库 | SQLite（加密存储） |
| 前端 | 原生 XML Layout |
| 网络 | OkHttp（WebDAV） |
| NLP | 本地 Tokenizer + Embedder + Reranker |
| 向量库 | 本地知识库（余弦相似度检索） |
| 备份 | MediaStore + WebDAV |

## 构建 | Build

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease
```

## 项目结构 | Project Structure

```
app/src/main/java/com/shrimpfarm/app/
├── MainActivity.java          # 主页 + 功能菜单
├── BaseActivity.java          # 底部导航基类
├── BasicDataActivity.java     # 基础数据
├── BatchManageActivity.java   # 批次管理
├── FeedingRecordActivity.java # 投喂记录
├── checkfeed/                 # 巡塘检查
│   └── CheckFeedActivity.java
├── mixcalc/                   # 拌料计算
│   └── MixCalcActivity.java
├── water/                     # 水质管理
│   └── WaterQualityActivity.java
├── analysis/                  # 数据分析
│   └── DataAnalysisActivity.java
├── model/                     # 告警、知识库、RAG 等模块
├── home/                      # 首页告警生成器
├── startup/                   # 启动管理
├── utils/                     # 工具类（国际化、加密等）
res/
├── values/strings.xml         # 中文文案
├── values-en/strings.xml      # 英文文案
└── assets/                    # 帮助页 HTML（中/英）
```

## F-Droid

[![F-Droid](https://img.shields.io/badge/F--Droid-available-brightgreen)](https://f-droid.org/)

此应用已上架 F-Droid。由于依赖 Supabase 后端服务，标记为 **Non-Free-Networks** Anti-feature。

## 国际化 | Internationalization

- 系统自动：跟随设备语言
- 手动切换：侧边栏菜单 → 语言选择
- HTML 页面：`help-en.html` / `privacy-policy-en.html` 等
- 所有告警、弹窗、表单均支持中英双语
