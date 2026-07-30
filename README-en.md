# Shrimp Farming

A full-cycle management app for shed-based shrimp farming on Android.

[![Language: Java](https://img.shields.io/badge/language-Java-orange)](https://java.com)
[![Min SDK: 28](https://img.shields.io/badge/minSdk-28-brightgreen)](https://developer.android.com/studio)
[![Target SDK: 36](https://img.shields.io/badge/targetSdk-36-brightgreen)](https://developer.android.com/studio)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

## Features

### Farming Management
| Feature | Description |
|---------|-------------|
| **Basic Data** | Water prep date, stocking date, species settings |
| **Batch Management** | Run multiple batches in parallel, independently managed |
| **Feeding Records** | Daily meal tracking (breakfast/lunch/dinner/night snack) |
| **Water Quality** | pH, ammonia, nitrite, alkalinity, H₂S, ORP, DO, etc. |
| **Feed Check** | Trough inspection, feeding time monitoring, feed increase advice |
| **Feed Mix Calc** | Dry/powder/fermented/water ratio calculator with dynamic column config |
| **Data Analysis** | Feeding & water quality trend charts, FCR calculation |
| **Task Planner** | Farming task reminders and scheduling |

### Utility Tools
| Feature | Description |
|---------|-------------|
| **Seawater Calculator** | Temperature/Salinity/Density conversion (EOS-80) |
| **Water Quality Board** | Interactive pH/ammonia/temperature simulation |
| **Smart Q&A** | RAG-based knowledge base Q&A system |
| **Expert System** | Rule-based water quality anomaly advice |
| **Market Prices** | Shrimp market price trends |
| **Smart Assistant** | Configurable alert agents (feed increase, timeout, water quality, etc.) |

### System Features
- **i18n** — Chinese & English, follow system or manual switch
- **Local Backup** — Export to Downloads folder
- **Cloud Backup** — WebDAV (Jianguoyun) auto/manual sync
- **Knowledge Base** — Local vectorized KB with semantic search

## Tech Stack

| Category | Technology |
|----------|-----------|
| Language | Java 11 |
| Min SDK | 28 (Android 9) |
| Target SDK | 36 |
| Compile SDK | 36 |
| Database | SQLite (encrypted) |
| UI | Native XML Layout |
| Network | OkHttp (WebDAV) |
| NLP | Local Tokenizer + Embedder + Reranker |
| Vector DB | Local KB (cosine similarity) |
| Backup | MediaStore + WebDAV |

## Build

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease
```

## Project Structure

```
app/src/main/java/com/shrimpfarm/app/
├── MainActivity.java          # Home + function grid
├── BaseActivity.java          # Bottom nav base class
├── BasicDataActivity.java     # Basic data
├── BatchManageActivity.java   # Batch management
├── FeedingRecordActivity.java # Feeding records
├── checkfeed/                 # Feed check
│   └── CheckFeedActivity.java
├── mixcalc/                   # Feed mix calculator
│   └── MixCalcActivity.java
├── water/                     # Water quality
│   └── WaterQualityActivity.java
├── analysis/                  # Data analysis
│   └── DataAnalysisActivity.java
├── model/                     # Alerts, KB, RAG modules
├── home/                      # Home alert generator
├── startup/                   # Startup manager
├── utils/                     # Utilities (i18n, crypto, etc.)
res/
├── values/strings.xml         # Chinese strings
├── values-en/strings.xml      # English strings
└── assets/                    # Help pages (CN/EN)
```

## Internationalization

- Auto: follows device language
- Manual switch: drawer menu → Language
- HTML pages: `help-en.html`, `privacy-policy-en.html`, etc.
- All alerts, dialogs, forms support both Chinese and English
