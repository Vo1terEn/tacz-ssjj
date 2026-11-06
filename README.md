# [SSJJRPG] 生死狙击冒险枪包

当前版本：**1.1.45-removesway1**。运行环境：Minecraft **1.20.1**、Forge **47.4.28**、TaCZ **1.1.8**。

本仓库公开当前交付相关的代码、配置和安装包，仅保留安装与修改所需内容。

- [config/ssjjrpg.toml](config/ssjjrpg.toml)：全局配置模板。
- [Java 源码与 ASM 补丁](https://github.com/Vo1terEn/tacz-ssjj/blob/main/mods/SSJJRPG-1.1.45-removesway1-java-source.zip)：按原制作工程区分基线、增量源码和字节码补丁，附来源及 SHA256 清单。
- [枪包文本源码](https://github.com/Vo1terEn/tacz-ssjj/blob/main/tacz/SSJJRPG-1.1.45-removesway1-gunpack-source.zip)：当前正式枪包的 JSON、Lua、shader 与 mcmeta，保留原目录，共 327 个文件；少量溯源元数据中的本机路径已规范为相对路径。
- [完整安装下载](https://github.com/Vo1terEn/tacz-ssjj/releases/tag/v1.1.45-removesway1)：精简为 `config/`、`mods/`、`tacz/`，不含工作目录、旧备份或历史核验材料。

源码以压缩包保存，便于通过 GitHub 网页完整上传；解压后可查看和修改。Java 沿用原工程的增量编译、映射和 ASM 修改方式，当前未整理成可独立一键重建的 Gradle 工程。枪包文本源码需配合同版 Release 的图片、音频等资源使用。源码包内的 `source-manifest.json` 记录来源与校验值。

安装步骤：

1. 退出游戏或停止服务器，备份原模组、枪包和配置。
2. 将同一版本的模组 JAR 放入游戏实例的 `mods/`，将枪包 ZIP 放入 `tacz/`，成对更新并移走旧版文件。完整安装包需先解压，不要直接放进 `tacz/`。
3. 首次安装可使用 `config/ssjjrpg.toml` 配置模板；已有该文件时保留原配置及自定义值，不要用模板覆盖。启动前确认已安装上述依赖。

运行文件的 SHA256 见 [SHA256SUMS](SHA256SUMS)，安装包和源码包另附 `.sha256` 文件。

本版撤掉 1.1.44 新增的裁决之炎整体左右摆动，将涉及的 8 个动画、模型及 Lua 资源恢复为 1.1.43 原始内容。裁决充能条开关 `render.fire_charge_bar`、寄生者第一／第三人称独立冲击波及其他既有功能保留。

交付核验已通过 ZIP 完整性、SHA256、双包资源一致性检查；已有专项离线测试通过 **1766 条断言**，发布流水线通过 **4 项测试场景**。本版没有新增完整 Minecraft 客户端画面、实战或联机测试，离线结果不等同于这些场景的验收。

原项目代码沿用模组元数据声明的 [MIT 许可](LICENSE)。原始美术、音频、模型、动画及其他第三方素材保留原权利人的权利，不适用本仓库的代码许可。已有第三方代码及其声明继续遵循原许可。
