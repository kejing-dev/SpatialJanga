# 叠木之间 · Tower Wood

PICO Shared Space 单人叠木游戏首版。原生 Kotlin / PICO Spatial SDK 6.1.9，使用 PICO Spatial UI 和随包 Noto Sans SC（400 字重）。

## 交付内容

- `TowerWood-debug.apk`：可安装调试版。
- `TowerWood-source.zip`：Android 工程、资源、规则测试及模拟器集成测试。
- `tower-wood/`：Blender 源文件、54 块木塔 GLB、单块 GLB、底座、原创木纹、渲染图、生成脚本与字体许可。
- `Tower-Wood-Block-Stacking-Game-PRD.md`：PRD，包含当前实现的物理模型说明。
- Figma：<https://www.figma.com/design/JfpAG3K8uxFkc7H2p59hFf/>。包含 28 个状态画面和 UX 分支图；属于可编辑设计稿，不是已接线的点击原型。应用使用 Noto Sans SC；设计稿主要文本已统一为 Noto Sans SC。

## 首版功能

54 块 / 18 层木塔；单块捏取、限速直接跟随的拖动、手腕旋转；最高完整层保护；塔顶半透明放置提示；稳定放置后计分；单块回合锁定；暂停、失焦和手势取消冻结；重新抓取同一块继续；倒塔结算；重新开始确认；音效及提示开关；个人最高纪录与最近完成回合保存。恢复局不更新正式纪录。

空间内容位于一个 Volumetric WindowContainer，系统 Shared Space 提供环境。木塔为独立 Entity，面板使用 AttachmentPanel。容器尺寸 0.7 × 0.6 × 0.4 m。

物理采用游戏化回合休眠：静止时木块保留碰撞盒、不挂动态刚体；抓取木块由手势目标限速驱动，上方木块使用动态刚体；松手后为所选块启用刚体，成功放稳后恢复静态。抽出前只允许沿木块长轴移动。该模型不是全塔连续刚体仿真；抓持期间尚无通用扫掠碰撞约束，极端移动和旋转仍需完善。

## 构建与运行

需要 JDK 21、Android SDK 和可访问的 PICO Maven 仓库。工程目录执行：

```sh
./gradlew testDebugUnitTest assembleDebug
pico-cli app install app/build/outputs/apk/debug/app-debug.apk --device emulator-5554 --replace
pico-cli app launch com.kejing.towerwood --device emulator-5554
```

模拟器测试：

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
```

集成测试调用实际场景的抓取 / 拖动 / 松手处理器，验证刚体位置和状态流转；它不等于真实手部追踪或 UI 射线命中验收。

## 已执行验证

构建、安装启动、架构检查和 Spatial UI 样式检查通过；20 项单元测试通过（含 19 项游戏规则与状态测试、1 项脚手架示例测试）。样式检查保留一条建议：使用 collectAsStateWithLifecycle。

模拟器集成测试 2 项通过（含 1 项完整游戏回合、1 项包名测试），其中游戏测试实际执行：加载 54 块 → 无输入保持 5 秒 → 暂停并验证冻结 → 抽出第 49 号木块 → 暂停并恢复 → 抬高并移至塔顶 → 释放 → 稳定计分为 1。该样本不代表 PRD 中连续 10 局和 60 秒物理验收均已通过。

## 截图说明与待验收项

`app-verification/panels/` 是模拟器内 Android View 的控件渲染导出，用于检查字体、文字、按钮和布局。`dropped.png` 与 `reset.png` 是本次修复后的实际控件渲染，其他编号控件图保留首版记录；控件图不包含 3D 木塔。

最终使用 CLI `capture screenshot` 成功捕获 Shared Space 中的木塔、底座和首页面板，见 `app-verification/01-shared-space-home.png`。前几次只捕获环境背景的结果不作为验收图。面板按 Figma 使用深绿不透明背景，保证明亮环境下文字对比度。

仍需在可访问的模拟器窗口 / PICO 设备上验收：真实捏取命中、手腕转向手感、追踪丢失与重获、连续多回合塔身响应、Shared Space 窗口移动与裁剪。首版的文字教程尚未替换为 Figma 中的逐步空间演示。

## 掉落恢复修复

游戏中可随时选择“重新开始”；暂停与结算中同样保留入口。木块落在塔外时会进入结算，不再停在等待调整状态。

“撤销上一步”恢复到最近一次抓取前的全部木块位置、朝向、分数和用时；支持撤销未完成的抓取、掉落以及已完成的叠放。连续撤销按抓取历史回退。撤销后进入练习模式，不更新正式纪录；重新开始会清空撤销历史。

已移除界面的“单人 · 共享空间”说明文字。

本次回归已通过掉落结算、撤销已完成叠放、撤销掉落、抓取中重新开始与撤销历史清空。

## 四套木块配色

设置中用图片选择原木棕、胡桃木、黑白、淡彩虹，选中即应用并保存。暂停时也可打开“配色与设置”。切换外观保持木块位置、分数及撤销记录。

淡彩虹包含淡红、蜜桃、奶黄、薄荷、奶蓝、淡紫，使用低饱和不透明材质（alpha=1）；黑白为炭黑和暖白。新资源、Blender 工程、预览与校验清单位于 tower-wood/themes/，每块仍为 188 个三角面。
