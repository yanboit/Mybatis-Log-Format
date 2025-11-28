# MyBatis SQL Formatt - IDEA 插件

一款专为 IDEA 设计的 **MyBatis 日志 SQL 格式化插件**，一键将杂乱的 MyBatis 日志 SQL 转换为对齐规范、语法高亮的可读格式，提升开发调试效率。

 ![image-20251127155333305](https://up.imold.wang/2025/11/20251127112852946.png)


<br />



## ✨ 核心功能

- 🚀 一键格式化：控制台选中 MyBatis 日志（含 `Preparing`/`Parameters`），右键快速格式化
- 📝 规范对齐：自动按 SQL 语法分行对齐（字段、表名、条件、排序单独成行）
- 🌈 语法高亮：原生 SQL 语法高亮（关键字、字符串、数字区分颜色）
- 🎨 极简图标：统一视觉风格，适配 IDEA 亮 / 暗主题
- 📱 自适应窗口：根据 SQL 长度自动调整窗口大小，支持滚动查看


<br />
<br />


## 📋 格式化效果示例

### 输入（MyBatis 日志）

```plaintext
Preparing: SELECT id, name, age FROM user WHERE id = ? AND age > ? ORDER BY age DESC LIMIT ?, ?
Parameters: "1", 18, 0, 10
```

### 输出（格式化后）

```sql
SELECT
    id,
    name,
    age
FROM
    user
WHERE
    id = "1" AND
    age > 18
ORDER BY
    age DESC
LIMIT
    0,
    10;
```

<br />



## 📥 安装方式

### 方式 1：本地安装（推荐）

1. 下载插件包：[Releases](https://github.com/yanboit/Mybatis-Log-Format/releases/tag/MybatisLogFormat) 中下载最新版本的 `mybatis-sql-formatter-1.0.0.zip`
2. 打开 IDEA → `File → Settings → Plugins → Install Plugin from Disk...`
3. 选择下载的 zip 包，重启 IDEA 即可生效



### 方式 2：源码编译安装

```bash
# 克隆仓库
git clone https://github.com/yanboit/Mybatis-Log-Format.git
cd mybatis-sql-formatter

# 编译插件（需 JDK 17+、Gradle 7+）
./gradlew clean buildPlugin

# 编译产物在 build/distributions/ 目录下
```

<br />



## 🚀 使用教程

1. 打开 IDEA 控制台（`Run`/`Debug` 面板）
2. 选中包含 `Preparing` 和 `Parameters` 的 MyBatis 日志行
3. 右键弹出菜单，点击「格式化 MyBatis SQL」
4. 自动弹出格式化后的 SQL 窗口，支持复制、语法高亮查看


<br />


## 📐 支持的 SQL 类型

- ✅ SELECT（含 WHERE/ORDER BY/LIMIT 子句）
- ✅ INSERT INTO（字段分行对齐）
- ✅ UPDATE（SET 子句分行）
- ✅ DELETE
- ✅ 多参数、多条件、多字段场景


<br />


## 🛠️ 技术栈

- 开发框架：IntelliJ Platform Plugin SDK
- 构建工具：Gradle
- 语言：Java 17
- 图标：SVG 极简风格（适配高分辨率）


<br />


## 🎨 插件截图

| 右键菜单触发                                                 | 格式化结果窗口                                               |
| ------------------------------------------------------------ | ------------------------------------------------------------ |
| ![image-20251127155144959](https://up.imold.wang/2025/11/20251127112845666.png) | ![image-20251127155217744](https://up.imold.wang/2025/11/20251127112843972.png) |


<br />


## 🔧 常见问题

### Q1：图标不显示？

A1：确保 `sql_icon.svg` 在 `src/main/resources` 根目录，执行 `./gradlew clean processResources` 重新编译

### Q2：格式化无反应？

A2：检查选中的日志是否包含 `Preparing` 和 `Parameters` 关键字，且 SQL 语法合法

### Q3：不支持复杂 SQL？

A3：当前版本支持绝大多数常用 SQL 语法，复杂子查询场景将在后续版本迭代支持


<br />


## 📌 版本迭代
### v1.2（2025-11-28）

- 修复注释格式化问题
- 修复子查询嵌套格式化问题

### v1.1（2025-11-27）

- 兼容PLSQL语法（DECODE、NVL、CONNECT BY等）及复杂SQL（多层函数、dblink、多表关联）
- 适配IDEA 2023-2024全版本，解决图标加载、API兼容问题
- 修复日期等带空格参数的?替换问题，确保参数正确填充
- 解决字符串越界报错，优化格式整洁度（GROUP BY后无多余空行）
- 完善错误提示（区分无Preparing/Parameters等场景）

### v1.0（2025-11-27）

- 初始版本：支持 SELECT/INSERT/UPDATE/DELETE 格式化
- 原生 SQL 语法高亮
- 自动分行对齐功能
- 极简风格插件图标


<br />


## 🤝 贡献指南

1. Fork 本仓库
2. 创建特性分支：`git checkout -b feature/xxx`
3. 提交代码：`git commit -m "feat: 新增xxx功能"`
4. 推送分支：`git push origin feature/xxx`
5. 提交 Pull Request


<br />


## 📄 许可证

本项目基于 [MIT 许可证](https://github.com/yanboit/Mybatis-Log-Format/blob/master/LICENSE) 开源，可自由使用、修改和分发。

------

如果觉得插件有用，欢迎给个 ⭐️ 支持一下！有任何问题或需求，可在 [Issues](https://github.com/yanboit/Mybatis-Log-Format/issues) 中反馈～
