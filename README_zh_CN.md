## [[English](README.md)/中文]。
# AutoSeamBlend

> 自动为 Minecraft 方块生成连接纹理——无需方块清单，也不用手动做贴图。
> 直接在游戏内预览、绘制和导出。

## 这是什么？

在 Minecraft 里把同样的方块挨着摆放时，两块表面之间常常会看到一条明显的
接缝，玻璃板是最典型的例子。“连接纹理”会让相邻的表面自然衔接，整面墙看
起来像一整块连续的方块，而不是许多块拼在一起。

AutoSeamBlend 帮你自动完成这件事：

- 自动找出适合的方块（不需要你填写任何清单）；
- 自动为每个方块选择正确的连接方式；
- 自动生成缺失的纹理状态。

它是一款连接纹理引擎的附属模组：安装下面任意一个引擎，AutoSeamBlend 就
能直接工作。

## 功能亮点

- **自动发现** —— AutoSeamBlend 根据每个方块实际的模型与渲染方式自动挑
  选合适的候选。
- **游戏内GUI绘制** —— 输入 `/autoseamblend` 打开编辑器：查看哪些方块已启
  用连接纹理、自己添加方块。
- **真实 3D 预览** —— 实时查看连接效果，可添加相邻方块、旋转和缩放。所
  见即游戏实际渲染结果。
- **像素绘制** —— 用内置像素画笔（画笔、橡皮、取色、填充、撤销/重做）直
  接修改方块六个面的贴图。
- **原生属性编辑** —— 按你所用引擎的原生格式调整连接属性，不会破坏已有
  的资源包。
- **一键导出** —— 导出为开箱即用的 “baked” 材质包；即使没有安装
  AutoSeamBlend 也能加载（仍需要对应的连接纹理引擎）。
- **尊重已有资源** —— 绝不覆盖现有的贴图和作者内容，只补齐缺失的部分。

## 快速开始

1. 为你的 Minecraft 版本和加载器安装 AutoSeamBlend。
2. 安装 **Fzzy Config**（必需）和以下**任意一个**引擎：

    - Fabric：[Continuity](https://www.curseforge.com/minecraft/mc-mods/continuity)
    - Forge 1.20.1：任选
      [Continuity 3.0.0+1.20.1.forge](https://modrinth.com/mod/continuity/version/3.0.0%2B1.20.1.forge)
      （需要 Sinytra Connector 与 Forgified Fabric API）或
      [Constancy](https://github.com/ThinkingStudios/Constancy)（原生 Forge 分支）
    - [ConnectedTexturesMod](https://www.curseforge.com/minecraft/mc-mods/ctm) / [CTM Lib](https://www.curseforge.com/minecraft/mc-mods/ctm-lib)
    - [Fusion (Connected Textures)](https://www.curseforge.com/minecraft/mc-mods/fusion-connected-textures)
    - [Athena](https://www.curseforge.com/minecraft/mc-mods/athena)

   可用引擎随版本和加载器略有不同（例如部分 Fabric 目标没有 CTM 适配）。
   Fabric 产物内置 UILib 与 Architectury。Forge 1.20.1 需要另外安装 UILib 0.3.6、
   Architectury API 9.2.14、Kotlin for Forge 4.x 和 Fzzy Config。Forge 玩家可在两个受支持的
   Continuity 家族实现中任选其一，但不要同时安装 Continuity 与 Constancy。

3. 进入游戏，输入 `/autoseamblend` 打开GUI界面，或 `/autoseamblend export`
   导出 baked 材质包。

## 配置方式与优先级

AutoSeamBlend 有三种配置入口，生效优先级从高到低为：

1. **第三方材质包** —— 任意资源包中，用你当前引擎的原生格式写好的连接规则
   （例如 Continuity/OptiFine 风格的材质包，以及 CTM、Fusion、Athena 规则）。
   这是优先级最高的来源，AutoSeamBlend 不会覆盖材质包作者写好的内容。
2. **内置材质包** —— 在游戏内 GUI（`/autoseamblend`）里添加方块并保存后生成，
   位于 `resourcepacks/AutoSeamBlend Managed/`。它只补充第三方材质包没有覆盖
   的部分。
3. **配置文件** —— `config/autoseamblend/autoseamblend.json5`（Fzzy Config）。
   适合手动添加或排除方块，优先级最低，只在上面两层没有匹配时生效。

优先级规则：**第三方材质包 > 内置材质包 > 配置文件**。高优先级内容命中时，
低优先级不会覆盖它；没有高优先级内容时，低优先级会完整生效，只补齐缺失的
纹理状态。

配置文件常用字段：

- `automaticDiscovery`：自动发现开关，默认 `true`；
- `targets`：按方法桶添加目标方块，例如在 `targets.auto.non-compatibility`
  中加入 `"minecraft:stone"`，或用 `"#minecraft:stone"` 让整个 tag 组成连接组；
- `excludedTargets`：显式排除不需要处理的方块。

修改配置文件后，重新加载游戏资源即可生效（例如按 F3+T，或重启游戏）。

## 需求
- 纯客户端模组 —— 单人游戏和任何服务器都可用，服务端无需安装。
- 两端都必需：Fzzy Config。
- Forge 1.20.1 还必需：UILib 0.3.6、Architectury API 9.2.14、Kotlin for Forge 4.x。
- 至少安装一个受支持的连接纹理引擎。
- 同时安装多个引擎时，AutoSeamBlend 会自动选择正确的引擎。

## 常见问题

**游戏里没有变化？**
请确认至少安装并启用了其中一个引擎。没有引擎时，AutoSeamBlend 只会显示
诊断界面。

**需要装到服务器上吗？**
不需要，它是纯客户端模组。

**会修改我的世界或资源包吗？**
不会。保存时它只写入自己的 “AutoSeamBlend Managed” 资源包，导出的材质包
会放到你指定的独立文件夹。

**为什么某些方块没有连接效果？**
自动发现基于方块真实的模型与贴图行为；使用特殊动态渲染的方块可能不会被
自动识别，但你可以在GUI或者配置文件里手动添加并编辑。

**应该选哪个引擎？**
列表中任意一个都可以。它们内部使用的格式不同，但编辑和导出流程完全一样。

**能把成果分享给别人吗？**
可以。导出的 baked 包就是普通材质包，只要对方安装了同一个引擎，即使没有
AutoSeamBlend 也能使用。

**它能兼容其他mod吗？**
当然,它就是为此创作的!

## 许可与致谢
- 作者：kltyton
- 许可证：LGPL-3.0-or-later
- AutoSeamBlend 基于 Continuity/NeoContinuity、CTM、Fusion、Athena、Fzzy
  Config 与 UILib 的公开 API 构建，感谢这些项目。
