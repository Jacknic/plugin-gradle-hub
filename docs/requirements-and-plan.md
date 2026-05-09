# GradleHub — 软件需求文档及开发计划

> **插件 ID**：`com.github.jacknic.plugin.gradlehub`
> **仓库地址**：https://github.com/Jacknic/plugin-gradle-hub
> **文档版本**：v1.0 | **日期**：2026-05-10

---

## 第一部分：软件需求文档

### 1. 项目背景与目标

#### 1.1 项目背景

Gradle 是 Android 与 JVM 生态中最广泛使用的构建工具，开发者通过 Gradle Wrapper 机制在各项目中统一构建版本。然而，在中国大陆等地区，由于网络环境的限制，Gradle 发行版及项目依赖的下载速度常常极其缓慢，导致构建超时、CI 流水线失败等问题。目前开发者通常需要手动修改 `gradle-wrapper.properties` 中的 `distributionUrl`，或编写 `init.gradle` 脚本来配置代理镜像，流程繁琐且容易出错。

#### 1.2 项目目标

GradleHub 旨在成为 IntelliJ IDEA 平台上一站式 Gradle 环境管理插件，核心目标如下：

| 目标 | 描述 |
|------|------|
| **加速下载** | 通过镜像代理自动替换 Gradle Wrapper 及依赖下载地址，消除手动配置的繁琐 |
| **版本管理** | 提供本地 Gradle 版本的查询、切换与清理能力，帮助开发者掌控构建环境 |
| **无缝集成** | 深度嵌入 IntelliJ IDEA 工作流，通过 Settings 与 Tool Window 提供零门槛操作体验 |
| **安全可靠** | 代理替换过程透明可审计，不引入中间人风险，支持一键回退 |

#### 1.3 当前状态

项目处于**初始化阶段**，当前代码库为 IntelliJ Platform Plugin Template 生成的脚手架，包含占位代码（`MyToolWindow`、`MyProjectService`、`MyApplicationActivationListener`），尚未实现任何业务功能。

---

### 2. 目标用户画像

| 维度 | 描述 |
|------|------|
| **角色** | JVM 后端开发者、Android 开发者、DevOps 工程师 |
| **技术水平** | 熟悉 Gradle 构建系统，日常使用 IntelliJ IDEA |
| **痛点** | ① Gradle/依赖下载慢或超时；② 本地 Gradle 版本散乱、占用大量磁盘空间；③ 手动配置代理容易遗漏或出错 |
| **典型场景** | 新项目 clone 后首次构建耗时过长；CI 环境中 Gradle Wrapper 下载失败；多项目间 Gradle 版本不一致 |
| **地域特征** | 主要面向中国大陆及网络受限地区的开发者 |
| **IDE 版本** | IntelliJ IDEA 2022.3+（Community / Ultimate） |

---

### 3. 核心功能需求

#### 3.1 功能优先级定义

| 优先级 | 标识 | 含义 |
|--------|------|------|
| P0 | 🔴 必须 | MVP 核心功能，缺失则产品无价值 |
| P1 | 🟠 重要 | 显著提升用户体验的功能 |
| P2 | 🟡 一般 | 锦上添花功能，可延后迭代 |

#### 3.2 功能需求清单

##### F1：镜像代理加速（P0）

| 编号 | 子功能 | 优先级 | 描述 |
|------|--------|--------|------|
| F1.1 | Wrapper 代理替换 | P0 | 自动将 `gradle-wrapper.properties` 中的 `distributionUrl` 替换为镜像地址，下载完成后可自动或手动回退 |
| F1.2 | 依赖仓库代理 | P0 | 自动在项目 `build.gradle` / `settings.gradle` 中注入镜像仓库地址（如阿里云 Maven 镜像），优先于原始仓库 |
| F1.3 | init.gradle 代理注入 | P1 | 生成全局 `init.gradle` 脚本，对所有 Gradle 构建生效，无需修改项目文件 |
| F1.4 | 代理开关 | P0 | 支持一键启用/禁用代理功能，禁用时自动恢复原始配置 |
| F1.5 | 代理状态提示 | P1 | 在状态栏/工具窗口中实时显示当前代理是否生效及使用的镜像地址 |

##### F2：设置管理（P0）

| 编号 | 子功能 | 优先级 | 描述 |
|------|--------|--------|------|
| F2.1 | Settings 页面 | P0 | 在 `Settings → Tools → GradleHub` 中提供配置界面 |
| F2.2 | 镜像地址配置 | P0 | 支持配置 Wrapper 镜像地址（如腾讯云、阿里云）及依赖仓库镜像地址 |
| F2.3 | 预置镜像模板 | P1 | 提供常用镜像地址的快速选择列表（腾讯云、阿里云、华为云等） |
| F2.4 | 配置持久化 | P0 | 所有配置通过 IntelliJ PersistentStateComponent 持久化，重启 IDE 后保留 |

##### F3：本地版本管理（P1）

| 编号 | 子功能 | 优先级 | 描述 |
|------|--------|--------|------|
| F3.1 | 版本扫描 | P1 | 扫描本地 `~/.gradle/wrapper/dists/` 目录，列出所有已安装的 Gradle 版本及路径 |
| F3.2 | 版本展示 | P1 | 在工具窗口以列表/树形展示版本信息，包含版本号、安装路径、大小、最后使用时间 |
| F3.3 | 版本切换 | P1 | 选中目标版本后，一键更新当前项目的 `gradle-wrapper.properties` 中的 `distributionUrl` |
| F3.4 | 版本清理 | P1 | 支持选择单个或批量删除本地 Gradle 发行版，释放磁盘空间 |
| F3.5 | 保留策略 | P2 | 执行清理时支持配置最低保留版本数，避免误删正在使用的版本 |
| F3.6 | 手动下载指定版本 | P1 | 支持用户输入或选择目标 Gradle 版本号，通过镜像地址下载对应发行版到本地 `~/.gradle/wrapper/dists/`，下载过程展示进度，完成后可一键切换 |

##### F4：工具窗口（P0）

| 编号 | 子功能 | 优先级 | 描述 |
|------|--------|--------|------|
| F4.1 | 状态面板 | P0 | 展示当前项目的 Gradle 版本、代理状态（启用/禁用、镜像地址） |
| F4.2 | 操作面板 | P1 | 提供快捷操作按钮：启用/禁用代理、切换版本、清理版本 |
| F4.3 | 下载进度 | P1 | 当 Gradle Wrapper 下载时，展示下载进度条及预估剩余时间 |

---

### 4. 非功能需求

#### 4.1 性能

| 编号 | 需求 | 验收标准 |
|------|------|----------|
| NFR-P1 | 版本扫描速度 | 扫描 `~/.gradle/wrapper/dists/` 并展示结果 < 2s（100 个版本以内） |
| NFR-P2 | 代理替换响应 | 修改 `gradle-wrapper.properties` 并生效 < 500ms |
| NFR-P3 | IDE 启动影响 | 插件加载对 IDE 启动时间增量 < 200ms |
| NFR-P4 | 内存占用 | 插件常驻内存占用 < 50MB |

#### 4.2 安全性

| 编号 | 需求 | 验收标准 |
|------|------|----------|
| NFR-S1 | 配置备份 | 替换 `gradle-wrapper.properties` 前自动创建 `.bak` 备份 |
| NFR-S2 | 回退机制 | 禁用代理或卸载插件时，自动恢复所有被修改的文件至原始状态 |
| NFR-S3 | 无中间人 | 代理替换仅修改 URL 前缀，不拦截或转发流量，确保端到端 HTTPS |
| NFR-S4 | 权限最小化 | 插件仅申请必要的文件系统读写权限，不访问网络 |

#### 4.3 可靠性

| 编号 | 需求 | 验收标准 |
|------|------|----------|
| NFR-R1 | 异常恢复 | 文件操作失败时回滚至操作前状态，不留下中间态 |
| NFR-R2 | 并发安全 | 多项目同时操作时配置不冲突，使用 IntelliJ 应用级持久化组件 |
| NFR-R3 | 日志审计 | 关键操作（代理替换、版本切换、版本清理）均记录到 IDE 日志 |

#### 4.4 兼容性

| 编号 | 需求 | 验收标准 |
|------|------|----------|
| NFR-C1 | IDE 兼容 | IntelliJ IDEA 2022.3 — 2024.1.*（Community / Ultimate） |
| NFR-C2 | JDK 兼容 | JDK 17+ |
| NFR-C3 | Gradle 兼容 | Gradle 7.x — 8.x |
| NFR-C4 | 操作系统 | Windows / macOS / Linux |

#### 4.5 可维护性

| 编号 | 需求 | 验收标准 |
|------|------|----------|
| NFR-M1 | 单元测试覆盖 | 核心逻辑单元测试覆盖率 ≥ 80% |
| NFR-M2 | 国际化 | 所有用户可见文本通过 ResourceBundle 管理，支持中英文 |
| NFR-M3 | 代码规范 | 通过 Qodana 静态分析，无严重级别警告 |

---

### 5. 预期交付物

| 交付物 | 描述 |
|--------|------|
| IntelliJ 插件包 | `.zip` / `.jar` 格式，可通过 JetBrains Marketplace 安装 |
| 源代码 | GitHub 仓库，含完整构建脚本 |
| 用户文档 | README.md 中的使用指南及配置说明 |
| 变更日志 | CHANGELOG.md 遵循 Keep a Changelog 规范 |
| 测试报告 | 单元测试 + Kover 覆盖率报告 |

---

## 第二部分：开发计划

### 6. 技术栈选型

| 类别 | 选型 | 理由 |
|------|------|------|
| **开发语言** | Kotlin (JVM 17) | IntelliJ 平台首选语言，与平台 API 无缝集成 |
| **构建工具** | Gradle 8.6 + Gradle IntelliJ Plugin | 官方推荐，提供 `runIde`、`buildPlugin`、`publishPlugin` 等任务 |
| **UI 框架** | IntelliJ Platform SDK (Swing/JB组件) | 原生集成，使用 `JBPanel`、`JBLabel`、`TreeTable` 等 JetBrains 组件 |
| **配置持久化** | PersistentStateComponent | IntelliJ 平台标准机制，自动序列化至 XML |
| **设置界面** | Configurable + DialogWrapper | 平台标准 Settings API |
| **文件操作** | IntelliJ VFS + PsiManager | 利用平台虚拟文件系统，确保文件变更被 IDE 正确感知 |
| **代码分析** | Qodana + Kover | 静态分析与测试覆盖率 |
| **版本管理** | Git + SemVer | 语义化版本控制 |
| **CI/CD** | GitHub Actions | 自动构建、测试、发布到 Marketplace |

---

### 7. 里程碑划分

#### M1：基础架构与代理核心（第 1–3 周）

**目标**：完成插件基础架构搭建，实现镜像代理加速核心功能，可进行内部演示。

**交付物**：
- [ ] 清除模板脚手架代码，建立项目包结构
- [ ] `GradleHubSettings`（PersistentStateComponent）— 配置持久化
- [ ] `GradleHubConfigurable` — Settings 页面
- [ ] `WrapperProxyService` — `gradle-wrapper.properties` 代理替换/回退
- [ ] 工具窗口状态面板（展示当前项目 Gradle 版本及代理状态）
- [ ] 核心逻辑单元测试

#### M2：依赖代理与 init.gradle（第 4–5 周）

**目标**：完善代理功能，支持依赖仓库代理与全局 init.gradle 注入。

**交付物**：
- [ ] `RepositoryProxyService` — 依赖仓库镜像注入
- [ ] `InitGradleService` — 全局 `init.gradle` 代理脚本生成
- [ ] 代理一键启用/禁用及状态恢复
- [ ] Settings 页面完善（预置镜像模板选择）
- [ ] 相关功能单元测试

#### M3：本地版本管理（第 6–8 周）

**目标**：实现本地 Gradle 版本的扫描、展示、切换与清理。

**交付物**：
- [ ] `GradleVersionService` — 本地版本扫描与管理
- [ ] 工具窗口版本列表面板（树形/表格展示）
- [ ] 版本切换功能（更新 `gradle-wrapper.properties`）
- [ ] 版本清理功能（单选/批量删除）
- [ ] 保留策略配置（最低保留版本数）
- [ ] 手动下载指定版本功能（版本选择 + 镜像下载 + 进度展示）
- [ ] 相关功能单元测试

#### M4：打磨、测试与发布（第 9–10 周）

**目标**：全面测试、修复缺陷、优化体验，发布 v1.0.0。

**交付物**：
- [ ] 下载进度展示
- [ ] 国际化（中英文）
- [ ] 全面测试 + 覆盖率达标（≥ 80%）
- [ ] Qodana 扫描通过
- [ ] 文档完善（README、CHANGELOG）
- [ ] 提交至 JetBrains Marketplace 审核

---

### 8. 任务拆解与排期

#### M1：基础架构与代理核心（3 周）

| 周次 | 任务 | 工时(h) | 依赖 |
|------|------|---------|------|
| W1 | T1.1 清除模板脚手架代码，重构包结构 | 4 | — |
| W1 | T1.2 定义 `GradleHubSettings` 数据类与 `PersistentStateComponent` | 4 | T1.1 |
| W1 | T1.3 定义 `GradleHubState` 配置项（mirrorUrl, repositoryMirrorUrl, enabled, gradleHome, keepVersions） | 2 | T1.2 |
| W2 | T1.4 实现 `GradleHubConfigurable` Settings 页面 UI | 8 | T1.3 |
| W2 | T1.5 实现 `WrapperProxyService`：解析 `gradle-wrapper.properties`，替换 `distributionUrl` | 12 | T1.3 |
| W2 | T1.6 实现代理回退：备份原始 URL，禁用时恢复 | 4 | T1.5 |
| W3 | T1.7 重构 `MyToolWindowFactory` → `GradleHubToolWindowFactory`，实现状态面板 | 8 | T1.5 |
| W3 | T1.8 编写 `WrapperProxyService` 单元测试 | 6 | T1.5 |
| W3 | T1.9 编写 `GradleHubSettings` 持久化测试 | 4 | T1.3 |
| W3 | T1.10 更新 `plugin.xml`（移除模板 listener，注册新扩展） | 2 | T1.1 |

#### M2：依赖代理与 init.gradle（2 周）

| 周次 | 任务 | 工时(h) | 依赖 |
|------|------|---------|------|
| W4 | T2.1 实现 `RepositoryProxyService`：解析 Gradle 脚本，注入镜像仓库 | 12 | T1.3 |
| W4 | T2.2 实现 `InitGradleService`：生成全局 `init.gradle` 代理脚本 | 8 | T1.3 |
| W4 | T2.3 实现代理一键启用/禁用逻辑（合并 Wrapper + Repository + InitGradle） | 6 | T2.1, T2.2 |
| W5 | T2.4 Settings 页面增加预置镜像模板选择 | 4 | T1.4 |
| W5 | T2.5 编写 `RepositoryProxyService` 单元测试 | 6 | T2.1 |
| W5 | T2.6 编写 `InitGradleService` 单元测试 | 4 | T2.2 |

#### M3：本地版本管理（3 周）

| 周次 | 任务 | 工时(h) | 依赖 |
|------|------|---------|------|
| W6 | T3.1 实现 `GradleVersionService`：扫描 `~/.gradle/wrapper/dists/` 目录 | 8 | T1.3 |
| W6 | T3.2 设计版本数据模型（版本号、路径、大小、最后使用时间） | 4 | T3.1 |
| W7 | T3.3 实现工具窗口版本列表面板（JBTable / TreeTable） | 12 | T3.2 |
| W7 | T3.4 实现版本切换：更新 `gradle-wrapper.properties` | 6 | T3.1 |
| W7 | T3.5 实现版本清理：单选/批量删除发行版文件 | 8 | T3.1 |
| W7 | T3.6 实现手动下载指定版本：获取远程版本列表、选择版本、通过镜像下载到本地 | 10 | T3.1, T1.3 |
| W8 | T3.7 实现保留策略（最低保留版本数） | 4 | T3.5 |
| W8 | T3.8 编写 `GradleVersionService` 单元测试 | 6 | T3.1 |
| W8 | T3.9 编写手动下载功能单元测试 | 4 | T3.6 |
| W8 | T3.10 版本操作集成测试 | 6 | T3.4, T3.5, T3.6 |

#### M4：打磨、测试与发布（2 周）

| 周次 | 任务 | 工时(h) | 依赖 |
|------|------|---------|------|
| W9 | T4.1 实现下载进度展示 | 8 | T1.7 |
| W9 | T4.2 国际化：提取所有硬编码字符串至 ResourceBundle | 6 | — |
| W9 | T4.3 全面功能测试 + Bug 修复 | 12 | M1–M3 |
| W10 | T4.4 Qodana 扫描 + 修复 | 4 | — |
| W10 | T4.5 覆盖率检查（目标 ≥ 80%） | 4 | — |
| W10 | T4.6 文档更新（README、CHANGELOG） | 4 | — |
| W10 | T4.7 提交 JetBrains Marketplace 审核 | 2 | T4.3–T4.6 |

---

### 9. 资源分配

| 角色 | 人员 | 职责 | 投入比例 |
|------|------|------|----------|
| 开发负责人 | @Jacknic | 架构设计、核心功能开发、Code Review | 100% |
| 测试 | @Jacknic（兼任） | 单元测试、集成测试、手工验证 | 30%（后期提升） |
| 产品/设计 | @Jacknic（兼任） | 需求细化、UI 原型设计 | 10% |

> **说明**：当前为个人开源项目，所有角色由 @Jacknic 承担。建议在 M2 阶段招募社区贡献者参与版本管理功能的开发。

---

### 10. 风险评估与应对策略

| 编号 | 风险描述 | 概率 | 影响 | 应对策略 |
|------|----------|------|------|----------|
| R1 | IntelliJ Platform API 变更导致兼容性问题 | 中 | 高 | 锁定 `pluginSinceBuild`/`pluginUntilBuild` 范围；关注平台版本发布说明；使用 `@ApiStatus.Experimental` 注解时做好降级方案 |
| R2 | Gradle Wrapper 文件格式变化 | 低 | 高 | 对 `gradle-wrapper.properties` 解析做防御性编程；支持多种 `distributionUrl` 格式；添加格式兼容性测试用例 |
| R3 | 镜像地址失效或变更 | 中 | 中 | 提供自定义镜像地址输入（不依赖硬编码）；预置模板可在线更新；用户可随时修改 |
| R4 | 多项目并发修改 `gradle-wrapper.properties` | 中 | 中 | 使用 IntelliJ WriteAction + CommandProcessor 保证原子性；操作前获取文件锁 |
| R5 | 用户环境差异（自定义 GRADLE_USER_HOME） | 中 | 低 | 支持自定义 `gradleHome` 配置；扫描时读取 `GRADLE_USER_HOME` 环境变量 |
| R6 | JetBrains Marketplace 审核不通过 | 低 | 高 | 提前对照[插件开发规范](https://plugins.jetbrains.com/docs/marketplace/plugin-overview.html)自查；确保无冗余线程、资源泄漏；使用平台标准 API |
| R7 | 磁盘清理误删正在使用的版本 | 中 | 高 | 清理前检测当前项目及所有打开项目使用的版本，标记为"受保护"；执行前弹出确认对话框；保留最低版本数策略 |
| R8 | 开发周期延误 | 中 | 中 | M1–M2 为核心路径，优先保障；M3 版本管理可独立迭代；每周进行进度回顾 |

---

## 附录

### A. 配置项清单

| 配置项 | 键名 | 类型 | 默认值 |
|--------|------|------|--------|
| 镜像代理地址 | `gradlehub.mirror.url` | String | _(空)_ |
| 依赖代理地址 | `gradlehub.repository.mirror.url` | String | _(空)_ |
| 启用代理 | `gradlehub.mirror.enabled` | Boolean | `true` |
| 本地 Gradle 目录 | `gradlehub.gradle.home` | String | `~/.gradle` |
| 保留最低版本数 | `gradlehub.keep.versions` | Integer | `2` |

### B. 预置镜像模板

| 名称 | Wrapper 镜像地址 | 依赖仓库镜像地址 |
|------|-----------------|-----------------|
| 腾讯云 | `https://mirrors.cloud.tencent.com/gradle/` | `https://mirrors.cloud.tencent.com/nexus/repository/maven-public/` |
| 阿里云 | `https://mirrors.aliyun.com/macports/distfiles/gradle/` | `https://maven.aliyun.com/repository/public` |
| 华为云 | `https://repo.huaweicloud.com/gradle/` | `https://repo.huaweicloud.com/repository/maven/` |
