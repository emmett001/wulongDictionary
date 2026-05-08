# 乌龙词典

> *Words build worlds.* — 一本安静的口袋词典，乌龙茶气质的查词工具。

---

## 简介

乌龙词典是一款 Android 离线词典应用。基于 SQLite3 检索引擎，支持牛津、柯林斯、韦氏大学三本权威词典的全文检索与对比阅读。

- **完全离线** — 无需网络，词典数据存储在本地
- **三词典并行** — 同一单词在三本词典间横向滑动切换
- **极简设计** — 奶油色暖调、Playfair 衬线字体、安静克制的排版
- **手工导入** — 用户自行准备词典文件，App 仅提供检索与渲染能力

## 技术栈

| 层 | 技术 |
|---|------|
| UI | Jetpack Compose + Material 3 |
| 导航 | Navigation Compose |
| 架构 | Clean Architecture + 手动 DI |
| 检索引擎 | SQLite3（只读） |
| 数据库 | Room（搜索历史） |
| 语言 | Kotlin |

## 词典准备

> 因版权原因，本仓库**不包含任何词典数据**。

你需要自行获取 `.sqlite3` 格式的词典文件，并在手机上通过以下步骤导入：

1. 下载词典压缩包，解压到手机存储
2. 打开乌龙词典 → 右上角齿轮图标 → 设置
3. 点击 **「导入词典文件…」**
4. 选择解压后的文件夹
5. App 会自动复制所有文件并加载词典

词典文件目录结构应为：

```
Dictionary/
  oaldpe/
    oaldpe.sqlite3
    oaldpe.css
    oaldpe.js
    ...
  柯林斯高阶双解/
    柯林斯高阶双解.sqlite3
    ...
  Merriam-Webster's Collegiate Dictionary 11th Edtion/
    Merriam-Webster's Collegiate Dictionary 11th Edtion.sqlite3
    ...
```

## 安装

从 [Releases](../../releases) 下载 `乌龙词典.apk`，在 Android 手机上直接安装。

或自行编译：

```bash
git clone https://github.com/emmett001/wulongDictionary.git
# 用 Android Studio 打开项目 → Build → Build APK(s)
```

## 截图

> 待补充

## License

本项目代码采用 MIT License。

词典数据版权归原作者 / 出版社所有，本项目不提供、不分发、不附带任何词典内容。

---

*Made with oolong tea & Compose.*
