# 规格说明：第 2 课 Electron 桌面化 + Live2D（含 IPC 拖拽练习重构）

- **自然语言**：简体中文
- **项目根目录**：`f:\代码\web\frontend`
- **课程文档**：`F:\qq文件\第3课：Electron 桌面化 + Live2D.md`（仅含 5.1、5.2 两节 Vue 代码）
- **素材来源**：`C:\Users\ywd13\Downloads\素材\素材`

---

## 一、问题与目标

### 1.1 背景与用户

**用户**：正在学习 Electron + Vue + Live2D 的学生，已完成第 1 课（Electron 基础集成，验证成功），现需按第 2 课完成两项练习。

### 1.2 目标（Goals）

1. **练习 1**：把课程 MD 文档中的所有操作落地——工程重建（按课程指定旧版本依赖）、Vite/Vue 配置、Electron 主进程（透明窗五要素 + 托盘 + 快捷键 + 双窗口）、Live2D 三件套集成、App.vue 双模式骨架 + Live2D 画布 + 眼球追踪 + CSS 拖拽，完整跑通"桌面显示一只 Live2D 宠物 + 眼球跟随鼠标"。
2. **练习 2**：将练习 1 中使用的 `-webkit-app-region: drag`（CSS 原生拖拽方式）**重构**为 **IPC 消息驱动的拖拽方案**，解决"拖拽覆盖层挡住 canvas 导致鼠标进入宠物身体区域时眼神追踪停止更新"的交互缺陷。

### 1.3 非目标（Non-Goals）

- **不**实现课程 MD 未提供渲染层代码的部分（聊天窗口 UI、打字发送、快捷键设置面板、录音切换按钮等）。主进程中这些部分的 IPC 接口（`conv-update`、`get/set-shortcut`、`toggle-recording`）会保留素材原样，**不**删除也**不**做对应 UI，因为缺乏老师给的参考代码；后续课程或下一节再接入。
- **不**接入 Java 后端（ChatController/VoiceChatController/BaiduSpeechService），目前仅做前端 + Electron 桌面壳闭环。
- **不**打包发布（`vite build` 后 `electron .` 的生产模式暂不验证，仅验证 `npm run dev:electron`）。
- **不**使用纳西妲或 MousseJiu 等额外模型，课程文档配置默认 LSS 模型为准。

---

## 二、功能需求（Functional Requirements）

### FR1：工程与版本（对应课程"步骤一~步骤二"）

- FR1.1 覆盖 `frontend/package.json` 为课程指定版本组合（名称 cyberpet、electron@38.8.6、vite@6.2.4、pixi.js@7.4.3、pixi-live2d-display@0.5.0-beta、vue@3.5.13 等），**移除 `"type": "module"`**（主进程为 CommonJS 的 main.js，不需要也不应声明 ESM）。
- FR1.2 `vite.config.ts` 保持课程最简（`plugins:[vue()]`、`server.port:5173`、`base:"./"`）——删除当前存在的 `vueDevTools` 插件、`@` 别名、`strictPort`（课程版本依赖组合不保证支持这些，后续若需严格端口作为加固单独加）。
- FR1.3 `tsconfig.json` 使用课程给定模板（`target:ES2020`、`module:ESNext`、`strict:true`、`include:src/**/*`）；删除原 tsconfig.app.json/node.json 分拆文件，保持与课程单一模板一致。
- FR1.4 `index.html` head 中**阻塞**引入 `/live2d/live2dcubismcore.min.js`，title 留空（透明窗不需要标题栏）。
- FR1.5 `src/main.ts` 为最简 `createApp(App).mount("#app")`，保留 `src/vite-env.d.ts` 声明 `.vue` 模块类型。
- FR1.6 删原 `package-lock.json`，执行 `npm install` 重建依赖树，确保 Electron@38、pixi@7 等正确版本落盘。

### FR2：Electron 主进程（课程"步骤三"素材 main.js 原样复制 + 修一个已知 bug）

- FR2.1 将素材 `electron/main.js` + `tray-icon.png` 复制到 `frontend/electron/`。**删除旧的 main.cjs**（不存在的入口引用必须清干净）。
- FR2.2 主进程必须具备**五面旗帜**（透明窗成立条件）：`transparent:true`、`frame:false`、`alwaysOnTop:true`、`skipTaskbar:true`、`focusable:false`。
- FR2.3 **修复素材已知 bug**：`ipcMain.on("move-window", ...)` 中 `setBounds` 硬编码了 `width:240, height:200`，改为 `getSize()` 取当前窗口尺寸——否则拖动宠物时窗口会被压扁，导致和 canvas 尺寸不一致。
- FR2.4 保留素材中其他 IPC 接口原状：`close-window`、`resize-pet-window`、`get/set-shortcut`、`conv-update`、`get-conversation`（作为后续功能接入保留）。
- FR2.5 双窗口管理：设置窗口 `width:420, height:580`，关闭时 `preventDefault + hide()`（非销毁）；主窗为 280×280 透明工具条窗，`setAlwaysOnTop("screen-saver")`，不在任务栏显示。
- FR2.6 托盘图标右键菜单："打开设置""退出"，双击托盘打开设置；全局快捷键默认 `Ctrl+Alt+L`，发送 `toggle-recording` 到渲染进程（当前 UI 无监听，无妨）。

### FR3：Live2D 三件套 + 模型 + 运行时（课程"步骤四"）

- FR3.1 素材 `PixiManager.ts` → `src/live2d/PixiManager.ts`；`Live2DManager.ts` → `src/live2d/Live2DManager.ts`。
- FR3.2 素材 `Live2DCanvas.vue` → `src/components/Live2DCanvas.vue`。
- FR3.3 `src/live2d/index.ts` 用素材版本（除了 PixiManager、Live2DManager 还要 `export type` types.ts 中的接口）。
- FR3.4 `src/config.ts` 按**课程文档**配置——`modelName:"LSS"`、`modelFile:"LSS.model3.json"`、`scale:0.18`、`canvas:300x400`、`position:0.5/0.5`，不用素材里自带的纳西妲配置（和练习 1 要求的文档一致）。
- FR3.5 `public/live2d/` 放入：`live2dcubismcore.min.js`（约 200KB C++ → WASM 胶水层，全局就绪前置条件）和整个 `LSS/` 模型目录（含 .model3.json、.moc3、texture 贴图等）。
- FR3.6 `src/live2d/types.ts` 复制素材（Live2DState / MotionGroup 类型声明）。

### FR4：App.vue 练习 1 — 文档 5.1 + 5.2 完整实现（CSS 拖拽方案，**留作对比基线后再在练习 2 替换**）

- FR4.1 双模式分流：`window.location.hash === "#settings"` 渲染设置窗；否则渲染宠物窗。一个 Vue 文件，两套 UI。
- FR4.2 全局样式：`html/body/#app = width/height:100%, transparent, overflow:hidden`；`settings-window` 深色 `#1a1a2e` 风格占位 `<h2>设置</h2>`；`pet-window` flex 居中，`user-select:none`。
- FR4.3 宠物窗 DOM 结构：`.pet-body`（relative, cursor:grab/grabbing, flex-shrink:0）下嵌套 `.pet-drag-region`（`position:absolute; inset:0; z-index:1; -webkit-app-region: drag`）+ `Live2DCanvas`（绑定 ref，传入 config 的画布尺寸、模型、scale、位置倍率）。
- FR4.4 眼球追踪：`document.addEventListener("mousemove", onMouseMove)`，回调中 `live2dRef.value?.live2d?.lookAt(e.clientX, e.clientY)`；onUnmounted 解绑。
- FR4.5 窗口尺寸同步：渲染完后用 `ipcRenderer.send("resize-pet-window", canvas.width, canvas.height)` 把主进程创建的 280×280 窗口改成和模型画布一致的 300×400；ipcRenderer 用 `(window as any).require?.("electron")` 方式优雅降级，纯浏览器开发时不报错。
- FR4.6 ipcRenderer 必须在 `!isSettings`（只在宠物窗）才调用 resize / 注册拖拽 IPC。

### FR5：App.vue 练习 2 — IPC 拖拽重构（**替换掉 FR4 中的 CSS 原生拖拽方案**）

- FR5.1 **删除** `.pet-drag-region` 覆盖层 DOM 和对应的 `.pet-drag-region` 样式（包括 `-webkit-app-region: drag`）。
- FR5.2 在 `.pet-body` 上添加 `@pointerdown="startDrag"` 事件监听。
- FR5.3 拖拽逻辑：
  - `startDrag(e)`：设置 `isDragging.value = true`；记录 `lastClientX = e.clientX, lastClientY = e.clientY`；调用 `(e.target as HTMLElement).setPointerCapture(e.pointerId)` 保证指针离开窗口区域仍然持续收到 move/up 事件。
  - 在 window 或 document 级别监听 `pointermove`（拖拽中）：若 `isDragging`，计算 `dx = clientX - lastClientX`、`dy = clientY - lastClientY`；更新 lastClientX/Y；调用 `ipcRenderer.send("move-window", Math.round(dx), Math.round(dy))`。
  - 在 pointerup / pointercancel：`isDragging = false`，releasePointerCapture。
- FR5.4 **验证眼神追踪恢复生效**：由于不再有 `z-index:1` 的拖拽覆盖层 + `-webkit-app-region: drag` 吃掉事件，鼠标进入 `.pet-body` 区域时 document.mousemove 能正常触发，眼球/头部会平滑跟随鼠标移动——这就是练习 2 要解决的 bug 修复效果。
- FR5.5 保留 cursor:grab/grabbing 视觉反馈（在 `.pet-body:active` / 或根据 isDragging 动态绑定 class），让用户知道"可拖拽、正在拖拽"。

---

## 三、非功能需求（Non-Functional Requirements）

- NR1 **依赖版本一致性**：package.json 版本号必须严格与课程 MD 一致（用户选了"完全按课程版本"）。不得遗留 electron@43 或 vite@8 等版本不匹配包，`package-lock.json` 必须是新 npm install 生成的。
- NR2 **路径与协议适配**：`vite.config.ts` 中 `base: "./"` + `index.html` 的 Cubism core `<script src="/live2d/...">` 走 Vite public 目录静态服务；开发态走 HTTP 5173，生产态 main.js 已经写了 `file://${__dirname}/../dist/index.html`（保留素材逻辑即可）。
- NR3 **模块系统正确性**：package.json **不能有** `"type": "module"`。主进程 `electron/main.js` 是 CommonJS（require/module.exports 语义），和课程一致。渲染进程仍用 ESM `<script type="module">`（Vite 驱动，不冲突）。
- NR4 **素材来源与产物不脱离**：PixiManager、Live2DManager、Live2DCanvas、main.js 必须**直接从素材目录复制**，原封不动（除了 FR2.3 指定的一个 bug 修复）。任何重写都会引入课程没测过的偏差。
- NR5 **启动可重复性**：`npm run dev:electron` 在空依赖状态下可一次性成功启动——先 concurrently 跑 vite，然后 wait-on 等 HTTP 200 返回（不是 TCP，上一轮已经改对），然后 electron 带 `--dev` 启动。
- NR6 **TypeScript 编译通过**：`vue-tsc --noEmit` 不报错，`.vue` 组件的类型和 `config.ts`、`live2d/*.ts` 都无红色波浪线。

---

## 四、约束与依赖（Constraints & Dependencies）

### 4.1 约束

1. **硬版本绑定**：用户明确选定"完全按课程版本"，意味着所有依赖版本要和课程文档完全一致，不得擅自升级。
2. **不能修改给代码的素材文件内容**：除 FR2.3（move-window 尺寸 bug）这一个明确问题可修，其他素材内容保持原样，避免和老师课堂代码不一致。
3. **不能伪造 Live2D 模型/运行时**：模型二进制和 Cubism core 必须原样从素材目录复制，禁止写假文件占位。
4. **工作目录**：所有操作在 `f:\代码\web\frontend\` 及其子目录下进行，父目录不再生成任何临时文件。

### 4.2 依赖

| 依赖项 | 来源 | 作用 |
|---|---|---|
| electron@38.8.6 + @38 manifest | npm 安装（课程指定版本） | 桌面壳 + 主进程运行时 |
| pixi.js@7.4.3, @pixi/core@7.4.3, @pixi/display@7.4.3 | npm | WebGL 渲染引擎 |
| pixi-live2d-display@0.5.0-beta | npm | Live2D Cubism4 桥接到 PIXI 舞台 |
| vue@3.5.13 | npm | UI 框架 |
| vite@6.2.4 + @vitejs/plugin-vue@5.2.3 | npm | 构建 + .vue 编译 |
| typescript@5.8.3 + vue-tsc@2.2.8 | npm | 类型检查 |
| concurrently@9.1.2 + wait-on@8.0.3 | npm | 并行启动 + HTTP 200 等待 |
| electron/main.js + tray-icon.png | 素材目录 electron/ | 主进程 177 行完整代码 + 托盘图标 |
| PixiManager.ts / Live2DManager.ts / Live2DCanvas.vue / types.ts | 素材目录 live2d-core/ | Live2D 渲染层三件套 + 类型 |
| live2dcubismcore.min.js | 素材目录 live2d/ | Cubism 4 运行时 JS 胶水层 |
| LSS/ 模型目录 | 素材目录 live2d/LSS/ | LSS 角色模型（.model3.json/.moc3/texture） |
| index.ts 导出模板 + config.ts 示例 | 素材目录 live2d-core/ + 课程文档 MD | 导出入口 + 模型配置 |

### 4.3 假设

1. Windows 环境下路径含中文（"代码" / "素材" / "qq文件"）不影响 npm 与 Electron。
2. 5173 端口在启动时不被系统或其他软件占用；若占用需先 kill，不再使用 strictPort 自动报错的 Vite 8 特性。
3. pixi-live2d-display@0.5.0-beta 对 Cubism Core 4 的 LSS 模型格式（.model3.json + .moc3）完全兼容。

### 4.4 开放问题（Open Questions）

**无**。所有阻塞点（素材可用性、版本方案）均已向用户确认并得到明确答复。课程 MD 涵盖范围之外的聊天/快捷键 UI 已显式列为非目标。

---

## 五、验收标准（Acceptance Criteria）

验收标准类型仅使用 `rule`（可客观判定的二元条件）与 `rubric`（0-2 质量分维度）。

### 5.1 Rule 类 AC

- **AC1（工程与依赖）**：`frontend/package.json` 中 `name` 为 `cyberpet`、`main` 为 `electron/main.js`、无 `"type": "module"` 字段；依赖版本与课程文档 JSON 中声明的逐字匹配（含 electron@38.8.6、vite@6.2.4、pixi.js@7.4.3、pixi-live2d-display@0.5.0-beta、vue@3.5.13、以及 devDependencies 各版本）。
- **AC2（依赖落盘）**：删除旧 lock 后重新 `npm install` 成功（0 vulnerabilities 或允许少数可忽略 funding 信息）；`frontend/node_modules/electron/dist/version` 内容为 38.x；`node_modules/pixi.js/package.json` version 为 7.4.3。
- **AC3（配置文件）**：`vite.config.ts` 只声明 vue 插件、port 5173、base:"./"；`tsconfig.json` 符合课程模板；`index.html` 中 `<head>` 存在阻塞式 `<script src="/live2d/live2dcubismcore.min.js">`。
- **AC4（主进程）**：`frontend/electron/main.js` 存在且与素材 `electron/main.js` 逐字节一致，**除了** `move-window` IPC 处理中 `setBounds` 的 width/height 改为 `win.getSize()` 当前尺寸的修复补丁；`tray-icon.png` 同路径存在；旧 `main.cjs` 文件已删除。
- **AC5（Live2D 资源）**：
  - `src/live2d/PixiManager.ts`、`Live2DManager.ts`、`index.ts`、`types.ts` 均存在且内容等于素材对应文件。
  - `src/components/Live2DCanvas.vue` 存在且内容等于素材对应文件。
  - `src/config.ts` 的 `modelName` 为 `"LSS"`、`modelFile` 为 `"LSS.model3.json"`、`scale` 为 0.18、`canvas.width`=300 `canvas.height`=400、`position` 0.5/0.5。
  - `public/live2d/live2dcubismcore.min.js` 存在且 ≥100KB。
  - `public/live2d/LSS/LSS.model3.json`、`LSS.moc3`、`LSS.4096/texture_00.png` 均存在。
- **AC6（App.vue 双模式分流）**：访问 `http://localhost:5173#settings` 渲染出文本"设置"的 h2 标签 + 深色容器样式；无 hash 访问时渲染宠物窗口结构。
- **AC7（主窗口运行后窗口特征）**：运行 `npm run dev:electron`，宠物窗口（主窗）满足以下：1) 背景完全透明（看到桌面而不是灰色底）；2) 无系统边框与标题栏按钮；3) 始终置顶于其他应用之上（不被普通窗口覆盖）；4) Windows 任务栏按钮区不出现该窗口的图标；5) 点击其他窗口时宠物窗**不会抢走焦点**（闪烁或把焦点拉走）。5 个条件缺一不可。
- **AC8（托盘）**：系统托盘区出现 `CyberPet` 图标，右键显示菜单含"打开设置""退出"两项；点击"打开设置"弹出 420×580 的设置窗口并显示"设置"占位内容；点击退出应用整体退出；双击托盘同样打开设置窗口。
- **AC9（Live2D 成功渲染）**：宠物窗口视觉上出现动漫角色而不是空白，canvas 没有黑色或其他 debug 背景色（PixiManager 的 debugBg 默认 false，因此背景必须全透明）。
- **AC10（眼球追踪 — 练习 2 重构核心验收）**：
  - 将鼠标从屏幕外部缓慢移入角色身体区域（原本被 `.pet-drag-region` 覆盖的范围），角色的眼球位置（ParamEyeBallX/Y）与头部朝向（ParamAngleX/Y/Z）**持续平滑更新**并跟随指针方向，不会出现"鼠标一进入宠物范围眼神就冻住不动"的现象。
  - 将鼠标在角色身体内部快速来回移动，参数仍持续响应更新，没有断点。
- **AC11（IPC 拖拽）**：在宠物身体区域按下鼠标左键拖动，窗口位置随鼠标移动平滑位移，不出现掉帧、拖不动或突然位移几百像素的情况；松开左键后停止移动；拖动过程中窗口尺寸（宽高）保持不变（修复了素材 move-window 尺寸 bug 的证据）。
- **AC12（窗口尺寸同步）**：宠物窗尺寸为 300×400（和 `config.ts.canvas` 一致），而不是主进程默认的 280×280；这证明渲染端 ipcRenderer.send("resize-pet-window", …) 生效。
- **AC13（无 CSS 拖拽残留）**：App.vue 模板中不存在 `.pet-drag-region` 元素，CSS 中不存在 `-webkit-app-region: drag` 声明——这是练习 2 已完成替换的证据。
- **AC14（TypeScript 编译无错）**：在 `frontend/` 下执行 `npx vue-tsc --noEmit` 退出码为 0，无 TS 类型报错。

### 5.2 Rubric 类 AC

- **R1 · 与课程文档一致性（0-2）**：
  - 2 分：所有文件内容、文件名、位置、依赖版本、步骤顺序完全遵循课程 MD，和老师课堂跑起来的产物不可区分；学生交作业可以直接被老师视为"正确完成"。
  - 1 分：基本结构和课程一致，但存在 1-2 处无关紧要的额外项（如多余注释、工具命令差异），不影响老师批阅。
  - 0 分：目录结构或依赖偏离课程规范、或存在擅自修改的"给代码"文件，老师一看就判定"和我课堂写的不一样"。
  - **及格阈值：≥ 2**
- **R2 · 拖拽方式正确性与眼神追踪修复的清晰度（0-2）**：
  - 2 分：完全去除 `-webkit-app-region` 方案，指针事件 100% 交由 JS 转发 IPC；鼠标在任意屏幕位置（包括角色身体上、角色身上 100% 覆盖区域内）移动，眼球都能平滑跟随，肉眼不可察觉"进入宠物区眼神停住"现象；拖拽响应顺滑无毛刺；代码注释清晰解释"为什么换成 IPC 拖拽"。
  - 1 分：方案是 IPC 拖拽但视觉上偶有微小抖动；或边缘区域有极少数像素眼神仍略有延迟，但主要 bug（身体区完全不更新）已消除。
  - 0 分：仍残留 `-webkit-app-region`；或身体区眼神还是不动；或 IPC 拖拽实现方向错了（比如把绝对坐标发给主进程然后窗口跳飞）。
  - **及格阈值：≥ 2**
- **R3 · 启动链路稳定性（0-2）**：
  - 2 分：`npm run dev:electron` 首次等待下载后再次运行（无需清缓存）100% 能稳定出现：Vite HTTP 5173 就绪 → Electron 启动 → 宠物窗口透明角色显示，无需二次重启或手动刷新。
  - 1 分：偶发 Electron 先启动导致白屏（wait-on 失效），但通常重试或加几秒 sleep 就能跑。
  - 0 分：经常白屏或闪退或端口冲突无法启动。
  - **及格阈值：≥ 1**
