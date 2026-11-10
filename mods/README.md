# Java 源码与已知 ASM 制作链

公开版本：**1.1.48**。`src/` 保留包路径，每个当前顶层类对应一份作者快照；`patches/` 保留实际应用过的 ASM 修改源码；`resources/` 对应当前 coremod、数据和元数据；`tools/` 是映射、链接检查和资源生成工具源码。

当前有 **294 个顶层类名**，全部有作者源码快照。源码并非全部当前方法体的独立重建，不能直接对全部 `src/` 执行 javac 来重现发行包。来源、原始/公开 SHA、当前 class 校验值以及源码证据范围见 [source-manifest.json](source-manifest.json)。外部类字节匹配也不代表所有内部类都可等价重建。

本版纳入 `ParticleFrames` 与 `ParticleSamplerPatch` 的采样复用、`OverloadDamage` 与 `OverloadPatch` 的完整伤害倍率，以及保留原 `List.subList` 注入位置的寄生者全文显示修复。`DimensionParticles`、`DimensionFieldGeometry`、`DimensionMarkGeometry`、`DimensionOverload`、`WeaponDamage` 的作者快照需结合对应后续 ASM 阅读，不能把其中旧方法当作当前二进制行为。

Forge 最低版本为 47.4.16，Minecraft 为 1.20.1。代码离线核验覆盖 TaCZ 1.2.0 与 1.1.8-hotfix2；神化诸神黄昏 8 / GunsmithLib 7 的组合要求 TaCZ 1.2.0。映射、Minecraft/Forge/TaCZ 库、原始制作环境、基线成品与外部美术素材不随源码分发。已知增量 Java/ASM 是真实制作来源，但尚未恢复完整一键构建链。

原创 arsenal 代码沿用 MIT；原 `ssjj_dark` 资产和技能代码沿用 GPL-3.0，其许可证保留于 `resources/META-INF/licenses/ssjj_dark/LICENSE`。第三方模型、动画、纹理和音频保留原权利，文本 JSON 形式不改变素材许可。完整安装资源在同版 Release。
