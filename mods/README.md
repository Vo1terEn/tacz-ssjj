# Java 源码与制作链

公开版本 **1.1.49**，当前747份class、326个顶层类均有同名作者源码快照。`src/` 按包路径浏览；`patches/` 保留已知ASM步骤；`resources/` 是本次Release中的核心脚本、数据及独有文本资源；`tools/` 包含历史工具与本轮整理的最新宙斯资源生成脚本。

这是历史作者快照与实际增量制作来源，尚未恢复完整全量构建。逐文件来源、原始/公开SHA与当前运行class族校验见[source-manifest.json](source-manifest.json)。类名覆盖或运行class校验不等于方法体全部可重建；后续ASM也可能修改未重编译的作者快照。WeaponVisuals 的皮肤尾节点修复必须结合 `patches/skin_tail_20261115/PatchTail.java` 和 `src/cn/ssjjcompat/SkinTailOrder.java` 阅读。

本轮新增模块生命周期、属性归属、渲染/Puffish兼容、宙斯之手功能与配置、皮肤末尾节点修复来源。`tools/zeus-production/` 按脚本名取最新交付快照，保留不同用途生成步骤；不是可直接运行的一键脚本集。脚本中的私有路径改成相对工作区、`dependencies/minecraft` 与 `source-assets/external` 输入别名，外部映射、原素材、基线成品、Forge/TaCZ库不随源码提供，需按原制作链配置。安装、回滚、历史QA与私人日志不入库。

原创代码MIT；原ssjj_dark代码与资产GPL-3.0，原许可保留于resources/META-INF/licenses。模型、动画、纹理和音频保留原权利。完整运行包见同版Release。
