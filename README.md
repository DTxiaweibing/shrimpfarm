# 小棚养虾

Android 小棚养虾养殖全流程管理 App | Shrimp Farming Management App

## 功能 | Features

- **基础数据** — 做水日/放苗日等养殖参数 | Basic data management (water prep date, stocking date, etc.)
- **批次管理** — 多批次养殖 | Multi-batch management
- **投喂记录** — 每日投喂量记录 | Daily feeding records
- **水质管理** — pH/氨氮/亚盐等检测 | Water quality testing (pH, ammonia, nitrite, etc.)
- **巡塘检查** — 巡塘情况记录 | Pond inspection records
- **拌料计算** — 拌料配比计算器 | Feed mixing calculator
- **数据分析** — 养殖数据图表分析 | Data analysis & charts
- **本地备份** — 备份到手机 Downloads | Local backup (Downloads folder)
- **坚果云备份** — WebDAV 自动/手动备份 | Jianguoyun WebDAV backup (auto & manual)
- **行情资讯** — 对虾市场价格 | Market price info

## 技术 | Tech Stack

- Language: Java
- Min SDK: 28, Target: 33, Compile: 36
- Database: SQLite (本地 | Local)
- Network: OkHttp (WebDAV | 坚果云)
- Backup: MediaStore + WebDAV

## 构建 | Build

```
./gradlew assembleDebug
```
