# [SSJJRPG] 生死狙击冒险枪包 1.1.51

Minecraft 1.20.1，建议使用 Forge 47.4.28 和 TaCZ 1.2.0。游戏内配置界面使用可选的 Cloth Config 11.1.136 Forge 版。阿瑞斯整套外观可搭配 Yes Steve Model 2.6.5。联机时客户端和服务端需更新对应版本的主模组与枪包。

## 安装

退出游戏，备份自定义配置和旧版组件。解压 `SSJJRPG-1.1.51-MC1.20.1.zip`，把其中的 `mods/SSJJRPG-1.1.51.jar` 和 `tacz/SSJJRPG-1.1.51-gunpack.zip` 放入实例对应目录，并移走本项目的旧版同类文件。枪包 ZIP 保持压缩。已有实例可继续使用自己的 `config/ssjjrpg.toml`。首次安装可使用随包提供的模板。

启动后从主菜单的「模组 → 生死狙击冒险枪包 → 配置」进入。加入远程服务器时，玩法数值由服务端管理。

## 这一版

修复与 TaCZ Expands 联用时 `EntityKineticBullet` 命中事件的 Mixin 注入失败。创造强化石保留原有伤害结算。见 [#5](https://github.com/Vo1terEn/tacz-ssjj/issues/5)。

## 下载与验证

安装包、配对文件与 SHA-256 校验值见 [GitHub Release](https://github.com/Vo1terEn/tacz-ssjj/releases/tag/v1.1.51)。仓库提供 [Java 与 ASM 增量](mods/README.md)、[枪包文本资源](tacz/README.md)和 [运行包来源与校验](runtime-manifest.json)。

对 TaCZ 1.2.0、1.1.8-hotfix2 和 1.1.5 的实际字节码运行了命中事件注入点回归检查。没有启动反馈者的完整整合包。 本版基于 1.1.50 的配对成品增量编译并重新打包。

源代码的授权及原素材权利见 [LICENSE](LICENSE) 和 [第三方许可](THIRD_PARTY_LICENSES.md)。
