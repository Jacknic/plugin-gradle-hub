# GradleHub - Gradle 下载管理插件

<!-- Plugin description -->

GradleHub 是一款 IntelliJ IDEA 平台插件，旨在解决 Gradle Wrapper 及项目依赖下载缓慢的问题。通过配置镜像代理地址，插件可大幅加速 Gradle 发行版与依赖包的下载速度，同时提供本地 Gradle 版本的管理能力，包括版本查询、切换与清理，帮助开发者高效管理 Gradle 构建环境。

<!-- Plugin description end -->

## 功能特性

- **镜像代理加速** — 支持自定义代理镜像地址，加速 Gradle Wrapper 发行版及项目依赖的下载，告别因网络问题导致的构建超时。
- **一键配置代理** — 在 IDE 内直接设置代理地址，无需手动修改 `gradle-wrapper.properties` 或 `init.gradle` 文件。
- **本地版本查询** — 扫描并展示本地已安装的所有 Gradle 版本及其存储路径，便于掌握当前环境。
- **版本快速切换** — 在项目间快速切换使用的 Gradle 版本，自动更新 `gradle-wrapper.properties` 中的 `distributionUrl`。
- **版本清理** — 检测并清理本地不再使用的 Gradle 发行版，释放磁盘空间。
- **状态可视化** — 通过工具窗口直观展示当前项目的 Gradle 版本、代理状态及下载进度。

## 快速上手

### 环境要求

| 要求 | 说明 |
|------|------|
| IDE | IntelliJ IDEA 2022.3+（Community / Ultimate 均可） |
| JDK | 17 及以上 |
| Gradle | 8.x |

### 安装

1. 从 [JetBrains Marketplace](https://plugins.jetbrains.com/) 搜索 **GradleHub** 并安装；
2. 或通过 IDE 菜单：`Settings` → `Plugins` → `Marketplace`，搜索 **GradleHub**，点击 `Install`；
3. 重启 IDE 使插件生效。

### 从源码构建

```bash
# 克隆项目
git clone https://github.com/Jacknic/plugin-gradle-hub.git
cd plugin-gradle-hub

# 构建插件
./gradlew buildPlugin

# 构建产物位于 build/distributions/ 目录
```

若需在开发环境中调试运行：

```bash
./gradlew runIde
```

## 使用指南

### 设置代理镜像地址

1. 打开 IDE，进入 `Settings` → `Tools` → `GradleHub`；
2. 在 **Mirror URL** 输入框中填写代理镜像地址，例如：
   - 腾讯云镜像：`https://mirrors.cloud.tencent.com/gradle/`
   - 阿里云镜像：`https://mirrors.aliyun.com/macports/distfiles/gradle/`
3. 点击 `Apply` 保存配置，插件将自动使用代理地址进行后续下载。

### 管理本地 Gradle 版本

1. 通过菜单 `View` → `Tool Windows` → `GradleHub` 打开工具窗口；
2. **查看版本**：工具窗口将列出本地已安装的所有 Gradle 版本及路径；
3. **切换版本**：选中目标版本，点击 `Switch` 按钮，插件将自动更新当前项目的 `gradle-wrapper.properties`；
4. **清理版本**：选中不再需要的版本，点击 `Clean` 按钮即可删除对应的发行版文件。

## 配置项说明

插件的所有配置项均可在 `Settings` → `Tools` → `GradleHub` 中进行设置。

| 配置项 | 键名 | 类型 | 默认值 | 说明 |
|--------|------|------|--------|------|
| 镜像代理地址 | `gradlehub.mirror.url` | String | _(空)_ | Gradle Wrapper 发行版下载的代理镜像前缀地址 |
| 依赖代理地址 | `gradlehub.repository.mirror.url` | String | _(空)_ | 项目依赖仓库的代理镜像地址 |
| 启用代理 | `gradlehub.mirror.enabled` | Boolean | `true` | 是否启用镜像代理加速功能 |
| 本地 Gradle 目录 | `gradlehub.gradle.home` | String | `~/.gradle` | 本地 Gradle 安装与缓存目录路径 |
| 保留最低版本数 | `gradlehub.keep.versions` | Integer | `2` | 执行版本清理时保留的最低版本数量 |

## 项目信息

- **插件 ID**：`com.github.jacknic.plugin.gradlehub`
- **仓库地址**：[https://github.com/Jacknic/plugin-gradle-hub](https://github.com/Jacknic/plugin-gradle-hub)
- **兼容版本**：IntelliJ IDEA 2022.3 — 2024.1.*
