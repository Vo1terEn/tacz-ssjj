# [SSJJRPG] 生死狙击冒险枪包

当前公开版本：**1.1.49**。

Minecraft 1.20.1，建议 Forge 47.4.28、TaCZ 1.2.0。Cloth Config 11 为可选配置界面依赖；搭配 GunsmithLib 7 / 诸神黄昏8 时使用 TaCZ 1.2.0。第三方前置另装。本次修复的原生模型回归使用 TaCZ 1.2.0，并对 helper 检查了1.1.8-hotfix2路径。

[下载完整安装包、配对模组与枪包](https://github.com/Vo1terEn/tacz-ssjj/releases/tag/v1.1.49) · [Java源码与制作链](mods/README.md) · [枪包文本源码](tacz/README.md)

## 安装

退出实例并备份旧文件。解压完整安装包，把 mods/SSJJRPG-1.1.49.jar 与 tacz/SSJJRPG-1.1.49-gunpack.zip 成对放入实例对应目录，移走本项目旧版重复文件。枪包 ZIP 保持压缩；完整安装 ZIP 必须先解压。首次安装可使用 config/ 和 defaultconfigs/ 模板，已有自定义配置请保留。联机双方更新同版配对文件。

## 本版变化

- 修复天罚之眼 timetravel 等皮肤在根节点顺序改变或缺失空尾节点时的 skin_fx_end 检查崩溃：在原检查前补齐空节点或将合法空根节点移至末尾，保留其他根节点顺序与异常模型检查。
- 纳入 Puffish 技能树兼容修复，保留原 BufferUploader 调用供 Mixin 包装，并兼容 Forge 核心脚本的 Java 类访问限制。
- 同步宙斯之手各品阶、皮肤、特效、雷霆能量、宙斯降临、幻影集火/联动、感电、投影追踪、可见性与配置调整，以及模块生命周期、属性归属与渲染兼容修改。
- 宙斯之手恢复枪包/配件原生基础属性；雷球最长飞行时间默认60tick。“能量抗性穿透无视减伤”为预留开关，当前没有战斗效果。

## 来源与验证

公开版本 1.1.49 由本地已修复交付 1.1.47-skintail1 重新打包。747份class、核心脚本和运行资源逐成员保留；仅更新明确记录的公开版本、溯源字符串与两份安装说明。来源与精确差异见仓库 runtime-manifest.json。

皮肤修复的历史离线回归使用真实 TaCZ 模型与生产 installSkin 方法，覆盖四款皮肤的正常顺序、重排、缺失节点和重复调用；旧方法复现错误，修复后通过。Puffish 修复的历史回归覆盖实际 Forge 核心脚本引擎与 Mixin 调用点。这些历史结果通过相应class/coremod字节一致性继承；本次公开打包重新检查JSON/TOML、ZIP CRC、版本、配对共享资源及SHA256。

没有全量重新编译历史制作链，本次发布没有安装、启动游戏或进行完整整合包、GPU及联机实测。源码是历史作者快照、增量与ASM工具，不能宣称完整一键重建。完整素材仅在配对运行包中。

下载与源码：https://github.com/Vo1terEn/tacz-ssjj/releases/tag/v1.1.49

原创代码沿用MIT；原ssjj_dark资产及技能代码沿用GPL-3.0。模型、纹理、动画、音频等原始素材保留原权利。校验见SHA256SUMS，许可见LICENSE及第三方许可说明。
