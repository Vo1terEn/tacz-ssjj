# [SSJJRPG] 生死狙击冒险枪包 1.1.50

Minecraft 1.20.1，建议使用 Forge 47.4.28 和 TaCZ 1.2.0。游戏内配置界面使用可选的 Cloth Config 11.1.136 Forge 版。阿瑞斯整套外观可搭配 Yes Steve Model 2.6.5。联机时客户端和服务端需更新对应版本的主模组与枪包。

## 安装

退出游戏，备份自定义配置和旧版组件。解压 `SSJJRPG-1.1.50-MC1.20.1.zip`，把其中的 `mods/SSJJRPG-1.1.50.jar` 和 `tacz/SSJJRPG-1.1.50-gunpack.zip` 放入实例对应目录，并移走本项目的旧版同类文件。枪包 ZIP 保持压缩。已有实例可继续使用自己的 `config/ssjjrpg.toml`。首次安装可使用随包提供的模板。

启动后从主菜单的「模组 → 生死狙击冒险枪包 → 配置」进入。加入远程服务器时，玩法数值由服务端管理。

## 这一版

- 修复「配置清单与当前版本不一致」，在界面补齐阿瑞斯挂饰的 40 项和黯水晶的 17 项设置。
- 修复保存时对多级 TOML 表的读取。界面保存后重新打开，可以读到新值。
- 随本版交付阿瑞斯装备与挂饰、黯水晶武器与技能，以及当前的战斗、渲染和兼容性更新。

## 下载与验证

安装包、配对文件与 SHA-256 校验值见 [GitHub Release](https://github.com/Vo1terEn/tacz-ssjj/releases/tag/v1.1.50)。仓库提供 [Java 与 ASM 增量](mods/README.md)、[枪包文本资源](tacz/README.md)和 [运行包来源与校验](runtime-manifest.json)。

本版使用已修复的本地配对成品，更新包内公开版本字段并重新打包。在 Forge 47.4.28、TaCZ 1.2.0、Cloth Config 11.1.136 的隔离客户端上验证了发布来源的界面开启、保存和重开。公开版核对了运行 class、配置清单和版本元数据；历史制作链包含多个 Java 与 ASM 增量，源码未完成全量一键构建。

源代码的授权及原素材权利见 [LICENSE](LICENSE) 和 [第三方许可](THIRD_PARTY_LICENSES.md)。
