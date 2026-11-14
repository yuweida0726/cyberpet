# 任务清单：第 2 课 Electron + Live2D

- 对应规格：`spec.md`（同目录）
- 项目根目录：`f:\代码\web\frontend`
- 依赖执行顺序：T1 → T2 → T3 → T4 → T5 → T6 → T7（串行，前项产物是后项前置）

---

## Task 1：重置工程依赖（package.json + lock + node_modules 重建）

- **Priority**: high
- **Status**: pending
- **父验收标准**: AC1, AC2

### 工作内容

1. 删除以下现有文件/目录：
   - `frontend/node_modules/`（整棵旧依赖树，版本完全不匹配）
   - `frontend/package-lock.json`（旧版本锁）
   - `frontend/electron/main.cjs`（旧主进程入口，课程改名为 CommonJS 形式的 main.js，且 package.json 不再声明 type:module）
2. 完整重写 `frontend/package.json`，内容**逐字**与课程文档一致（name=cyberpet、main=electron/main.js、**无** `"type": "module"`、scripts 中 dev:electron 使用课程的 concurrently + wait-on HTTP 5173、依赖与 devDeps 版本严格匹配）。
3. 在 `frontend/` 目录下执行 `npm install`，等待所有依赖落盘（Electron@38 二进制首次下载会较慢，允许超时）。

### 本地 Test Requirements

- **TR1.1（rule）**：`cat package.json` 后 `"name": "cyberpet"` 和 `"main": "electron/main.js"` 正确，且 grep 不到 `"type": "module"` 字符串。
- **TR1.2（rule）**：`npm ls electron` 输出 electron@38.8.6；`npm ls pixi.js` 输出 pixi.js@7.4.3；`npm ls vite` 输出 vite@6.2.4；`npm ls pixi-live2d-display` 输出 pixi-live2d-display@0.5.0-beta。
- **TR1.3（rule）**：`ls frontend/node_modules` 存在 electron、pixi.js、pixi-live2d-display、vue、concurrently、wait-on、@vitejs/plugin-vue、typescript、vue-tsc 九个关键目录。
- **TR1.4（rubric, 0-2, 阈值≥1）** 安装清洁度：
  - 2：0 vulnerabilities、无 WARN 级别以上输出、funding 提示不影响。
  - 1：≤ 2 个低风险漏洞且均不是 electron/pixi 核心链路。
  - 0：安装失败，或存在 ERESOLVE/ETIMEDOUT 等阻断错误未解决。
  - 证据：npm install 的退出码 + 输出最后几行。

### 完成证据
- 上述 4 项 TR 的命令输出或截图；npm install 的 summary 行（Added/Removed/Audited 统计）。

---

## Task 2：重写工程配置文件（vite/ts/index.html/main.ts/vite-env.d.ts）+ 清理旧文件

- **Priority**: high
- **Status**: pending
- **Blocked By**: T1 完成（npm install 完成后这些文件才有依赖被解析）
- **父验收标准**: AC3

### 工作内容

1. 覆盖 `frontend/vite.config.ts` 为课程最简模板：只 import vue 插件、port 5173、base:"./"；**删除** @ 别名、`vueDevTools`、`strictPort`。
2. 覆盖 `frontend/tsconfig.json` 为课程模板（ES2020/ESNext/bundler/strict/vite-client/include）；删除原 `tsconfig.app.json`、`tsconfig.node.json`（课程用单一 tsconfig，无分拆）。
3. 覆盖 `frontend/index.html`：lang=zh-CN、title 空、head 中阻塞引入 `/live2d/live2dcubismcore.min.js`，body 中只有 `#app` 和 `/src/main.ts`。
4. 覆盖 `frontend/src/main.ts` 为最简 `createApp(App).mount("#app")`（三行，不含 env.d 或其他 import）。
5. 新建/覆盖 `frontend/src/vite-env.d.ts`：reference vite/client + declare `*.vue` 模块类型。
6. 删除：`frontend/env.d.ts`（旧类型文件，功能已被 vite-env.d.ts 覆盖）。

### 本地 Test Requirements

- **TR2.1（rule）**：`grep -c "vueDevTools" vite.config.ts` 结果为 0；`grep "base:" vite.config.ts` 包含 `"./"`。
- **TR2.2（rule）**：`tsconfig.json` 的 `include` 只包含 `["src/**/*"]`，不出现 tsconfig.node.json/tsconfig.app.json 文件名。
- **TR2.3（rule）**：`index.html` 中 `<script src="/live2d/live2dcubismcore.min.js">` 出现在 `</head>` 之前（即阻塞在渲染前）。
- **TR2.4（rule）**：`src/main.ts` 仅有 3 行左右，不包含任何与 `main` 无关的 import。
- **TR2.5（rule）**：`src/vite-env.d.ts` 同时存在 `/// <reference types="vite/client" />` 和 `declare module "*.vue"` 两段。
- **TR2.6（rule）**：磁盘上已不存在 `tsconfig.app.json`、`tsconfig.node.json`、`env.d.ts` 三个旧文件。

### 完成证据
- 以上 6 条 rule 的检查结果（grep / 文件存在性）。

---

## Task 3：拷贝主进程 + 修 move-window 尺寸 bug + 拷贝托盘图标

- **Priority**: high
- **Status**: pending
- **父验收标准**: AC4, AC7

### 工作内容

1. 从素材目录 `C:\Users\ywd13\Downloads\素材\素材\electron\main.js` 复制到 `frontend/electron/main.js`（覆盖或新建）。
2. 从素材目录 `C:\Users\ywd13\Downloads\素材\素材\electron\tray-icon.png` 复制到 `frontend/electron/tray-icon.png`。
3. **修复素材已知 bug**（唯一允许的手动修改）：定位到 `ipcMain.on("move-window", ...)` 回调内部，原来写死的：
   ```js
   win.setBounds({ x: Math.round(wx + x), y: Math.round(wy + y), width: 240, height: 200 })
   ```
   改为保留当前窗口尺寸：
   ```js
   const [w, h] = win.getSize()
   win.setBounds({ x: Math.round(wx + x), y: Math.round(wy + y), width: w, height: h })
   ```
4. 确认旧的 `frontend/electron/main.cjs` 文件在 Task 1 中已删除（若是没删干净现在删）。

### 本地 Test Requirements

- **TR3.1（rule）**：逐字节对比（除修改点外），`frontend/electron/main.js` 与素材 `electron/main.js` 的 177 行结构完全对应，仅 move-window 的 setBounds 一处不同。
- **TR3.2（rule）**：`frontend/electron/tray-icon.png` 存在且文件大小 > 500 B（托盘图标最小尺寸）。
- **TR3.3（rule）**：`frontend/electron/main.cjs` 不存在于磁盘。
- **TR3.4（rule）**：`electron/main.js` 中 `createWindow` 函数里的 BrowserWindow options 五要素同时存在：`transparent:true && frame:false && alwaysOnTop:true && skipTaskbar:true && focusable:false`。
- **TR3.5（rule）**：修复后的 `move-window` 分支中，先 `const [w, h] = win.getSize()` 再 setBounds 用 w 和 h，不再出现数字 "240" 和 "200"（除了日志和其他接口）。

### 完成证据
- 修复片段 diff 截图或原文抄录；ls electron/ 目录列表；grep 五要素的结果。

---

## Task 4：拷贝 Live2D 三件套 + 类型 + index.ts + config.ts（按课程 MD 的 LSS 配置）

- **Priority**: high
- **Status**: pending
- **父验收标准**: AC5

### 工作内容

1. 将素材 `live2d-core/PixiManager.ts` 复制到 `frontend/src/live2d/PixiManager.ts`。
2. 将素材 `live2d-core/Live2DManager.ts` 复制到 `frontend/src/live2d/Live2DManager.ts`。
3. 将素材 `live2d-core/types.ts` 复制到 `frontend/src/live2d/types.ts`。
4. 将素材 `live2d-core/index.ts` 复制到 `frontend/src/live2d/index.ts`（含 types 的 export type）。
5. 将素材 `live2d-core/Live2DCanvas.vue` 复制到 `frontend/src/components/Live2DCanvas.vue`。
6. **不使用**素材中 `live2d-core/config.ts`（纳西妲模型），改为写一份课程 MD 配置的 `frontend/src/config.ts`：modelName `"LSS"`、modelFile `"LSS.model3.json"`、scale `0.18`、canvas `{ width:300, height:400 }`、position `{ xMultiplier:0.5, yMultiplier:0.5 }`。
7. 拷贝运行时与模型目录：
   - 素材 `live2d/live2dcubismcore.min.js` → `frontend/public/live2d/live2dcubismcore.min.js`
   - 素材整个 `live2d/LSS/` 目录 → `frontend/public/live2d/LSS/`

### 本地 Test Requirements

- **TR4.1（rule）**：磁盘存在 `src/live2d/PixiManager.ts`、`Live2DManager.ts`、`index.ts`、`types.ts`；`src/components/Live2DCanvas.vue`。
- **TR4.2（rule）**：`src/config.ts` 的 modelName 精确等于 `"LSS"`，scale=0.18，canvas.width=300, canvas.height=400。
- **TR4.3（rule）**：`public/live2d/live2dcubismcore.min.js` 文件存在且大小 ≥ 100 KB。
- **TR4.4（rule）**：`public/live2d/LSS/LSS.model3.json`、`LSS.moc3`、`LSS.4096/texture_00.png` 均存在。
- **TR4.5（rule）**：`PixiManager.ts` 首行 `import * as PIXI from "pixi.js"` 正确；`Live2DManager.ts` 首行正确 import pixi 和 `pixi-live2d-display/cubism4`。

### 完成证据
- 目录树清单（`src/live2d/`、`src/components/`、`public/live2d/` 三个子目录的 ls -R 或等价）；cubismcore 文件大小命令输出。

---

## Task 5：编写 App.vue（练习 1 基线 + 练习 2 IPC 拖拽重构一起落地，因为练习 2 就是替换掉 5.2 的 CSS 拖拽方案，无必要保留中间态）

- **Priority**: high
- **Status**: pending
- **Blocked By**: T2, T3, T4（组件、IPC 接口、config 均需就绪）
- **父验收标准**: AC6, AC7, AC9, AC10, AC11, AC12, AC13

### 工作内容

> 设计决策：虽然课程 MD 5.2 教学是写 CSS 拖拽（覆盖层 + -webkit-app-region:drag），但用户练习 2 就是把它替换成 IPC 拖拽。若先写一版 CSS 再删掉完全是重复劳动，所以本任务一步到位写成"5.1 双模式 + 5.2（不含 CSS 拖拽，直接换成 IPC 拖拽）"的最终版；代码中注释清楚"**这里原本是 CSS 拖拽方案，按练习 2 要求改为 IPC 拖拽，解决眼神追踪在覆盖区被吃掉的问题**"作为教学对比说明。

1. **Template 部分**：
   - `v-if="isSettings"`：渲染 `.settings-window` 容器，内含 `<h2>设置</h2>` 占位。
   - `v-else`（宠物窗）：
     - `.pet-window` flex 居中容器。
     - 嵌套 `.pet-body`（尺寸绑定 config.canvas 宽高，@pointerdown="startDrag"，动态 :class="{dragging: isDragging}"）。
     - **不创建** `.pet-drag-region` 覆盖层 div（这是练习 2 的核心替换点）。
     - `<Live2DCanvas ref="live2dRef" ...>` 传 config 的所有参数（宽高、modelName、modelFile、scale、位置倍率）。
2. **Script 部分**：
   - `const isSettings = window.location.hash === "#settings"`
   - `const live2dRef = ref<any>()`
   - ipcRenderer 解构：`const { ipcRenderer } = (window as any).require?.("electron") ?? ({} as any)`
   - 眼球追踪：`onMouseMove(e: MouseEvent)` 中 `live2dRef.value?.live2d?.lookAt(e.clientX, e.clientY)`；`onMounted` 绑 `document.addEventListener("mousemove", onMouseMove)`；`onUnmounted` 解绑。
   - 尺寸同步：`onMounted` 内 `if (!isSettings && ipcRenderer?.send) ipcRenderer.send("resize-pet-window", 300, 400)`。
   - IPC 拖拽（练习 2）：
     - `const isDragging = ref(false)`、`const lastClientX = ref(0)`、`const lastClientY = ref(0)`
     - `function startDrag(e: PointerEvent)`：`isDragging.value = true; lastClientX.value = e.clientX; lastClientY.value = e.clientY; (e.target as HTMLElement).setPointerCapture(e.pointerId);`
     - 拖拽过程的 pointermove：在 onMounted 内绑定 window 或 document 的 pointermove（因为 setPointerCapture 后事件仍会打到原 target，绑 document 最稳）：
       ```
       function onDragMove(e: PointerEvent) {
         if (!isDragging.value) return
         const dx = e.clientX - lastClientX.value
         const dy = e.clientY - lastClientY.value
         lastClientX.value = e.clientX
         lastClientY.value = e.clientY
         ipcRenderer?.send?.("move-window", Math.round(dx), Math.round(dy))
       }
       ```
     - pointerup / pointercancel：`function stopDrag(e: PointerEvent) { if (isDragging.value) { isDragging.value = false; try { (e.target as HTMLElement).releasePointerCapture(e.pointerId) } catch {} } }`
     - onMounted 内注册 pointermove、pointerup、pointercancel（绑在 document 上保证离开窗口也收到）；onUnmounted 解绑。
     - 在上述所有 ipcRenderer 调用入口前加一层 `ipcRenderer?.send` 存在性判断，保证纯浏览器开发也不崩。
3. **Style 部分**（全局 `<style>`，不能 scoped）：
   - `* { margin:0; padding:0; box-sizing:border-box; }`
   - `html, body, #app { width:100%; height:100%; overflow:hidden; background:transparent; }`
   - `body { font-family:"Microsoft YaHei",sans-serif; }`
   - `.settings-window { width:100vw; height:100vh; color:#c8d0d8; display:flex; flex-direction:column; padding:20px; }`（加上 padding 使设置文字不贴边）
   - `.pet-window { width:100vw; height:100vh; display:flex; align-items:center; justify-content:center; user-select:none; }`
   - `.pet-body { position:relative; cursor:grab; flex-shrink:0; }`
   - `.pet-body.dragging, .pet-body:active { cursor:grabbing; }`（拖拽中抓着手型反馈）
   - 禁止出现任何含 `-webkit-app-region: drag` 的 CSS 行（AC13 检查）。

### 本地 Test Requirements

- **TR5.1（rule）**：App.vue 模板中无 `pet-drag-region` 字样；grep `webkit-app-region` App.vue 得到 0 行。
- **TR5.2（rule）**：Script 中存在 `ipcRenderer.send("move-window", Math.round(dx), Math.round(dy))` 字符串；存在 `ipcRenderer.send("resize-pet-window", 300, 400)` 字符串。
- **TR5.3（rule）**：Script 中存在 `lookAt(e.clientX, e.clientY)` 调用，且监听事件是 `document.addEventListener("mousemove", onMouseMove)`（不是某个子元素）。
- **TR5.4（rule）**：`isSettings` 基于 `window.location.hash === "#settings"` 判断。
- **TR5.5（rule）**：style 块中 `html,body,#app` 设置 `background: transparent`。
- **TR5.6（rubric, 0-2, 阈值≥2）** IPC 拖拽实现正确性：
  - 2 分：使用 pointerdown/pointermove/pointerup + pointerCapture 完整事件链；dx/dy 按 client 坐标差计算并发 move-window；边界指针离开窗口仍能收到事件（已 setPointerCapture）；窗口释放时正常停止。
  - 1 分：事件模型不完整（用 mousedown/mousemove/mouseup 而不是 PointerEvents），或没有 pointerCapture。
  - 0 分：用绝对坐标或 setPosition 替代增量，或完全没写。
  - 证据：代码片段抄录。
- **TR5.7（rubric, 0-2, 阈值≥2）** 注释完整性：
  - 2 分：在删除 CSS 拖拽方案处、IPC 拖拽入口处、眼球追踪 document 级别监听处分别有中文注释解释"为什么这样写（相对课程 5.2 方案做了什么替换和优化）"。
  - 1 分：只在入口有注释。
  - 0 分：无任何中文解释为什么不用 webkit-app-region。
  - 证据：对应注释行。

### 完成证据
- App.vue 全文件内容；以上 rule 的 grep 结果。

---

## Task 6：运行验证（npm run dev:electron）并独立检验 7 项可视结果

- **Priority**: high
- **Status**: pending
- **Blocked By**: T1~T5 全部 completed
- **父验收标准**: AC7, AC8, AC9, AC10, AC11, AC12

### 工作内容

1. 执行 `npm run dev:electron` 并等待。
2. **手动/工具**观察以下现象依次记录证据：
   a. Vite 5173 HTTP 服务启动（不报错 Port is in use；若被占先 kill 再试）。
   b. wait-on HTTP 200 之后 Electron 弹出主窗口。
   c. **五要素可视化检验**（AC7）：背景透明（透过宠物窗看到桌面/终端）；无边框与最小化/最大化/关闭按钮；宠物窗始终在最上层（打开记事本，移动记事本到宠物上，宠物仍然可见）；任务栏上不出现宠物对应任务条按钮；切换回记事本（点击记事本）后宠物窗不会抢焦点（光标仍停在记事本或活动窗口是记事本）。
   d. **托盘检验**（AC8）：托盘 CyberPet 图标、右键菜单"打开设置""退出"、打开设置窗口可见 `<h2>设置</h2>` 占位内容。
   e. **Live2D 渲染**（AC9）：出现角色形象、无 debug 色块背景（透明），无大片黑色矩形。
   f. **眼球追踪+身体区域有效**（AC10）：鼠标移到角色身体上任意位置，肉眼可见眼珠和头部方向发生平滑变化；在角色身体区域内快速左右来回移动，眼神会持续响应更新，不会"冻住"。对比基准：如果鼠标在远离角色（比如任务栏区域，屏幕边缘）移动，则角色眼神应该看向对应方向，回到身体内部也继续连续响应。
   g. **拖拽**（AC11）：按住宠物身体区域拖动，位置平滑跟随，窗口尺寸不变（用截图对比拖前拖后宽高）。
   h. **窗口尺寸**（AC12）：宠物窗尺寸是 300×400，用截图工具测量或 devTools 里看 window.innerWidth 300、innerHeight 400。
3. 执行 `npx vue-tsc --noEmit`，检查类型。
4. 按 Ctrl+C 退出，确认 concurrently 的 -k 行为把 vite 和 electron 同时杀干净。

### 本地 Test Requirements

- **TR6.1（rule）**：五要素 5 条全部通过，每条有证据。
- **TR6.2（rule）**：托盘 3 个交互（打开设置、退出、双击托盘打开设置）全部通过。
- **TR6.3（rule）**：角色正常显示，没有黑底。
- **TR6.4（rule）**：眼球追踪在角色身体内部持续响应（鼠标在内部 2 秒来回移动，视频或连续截图显示 eye 角度变化）。
- **TR6.5（rule）**：IPC 拖拽连续移动 ≥ 100px 距离后窗口实际位移且不改变宽高（宽高对比截图，误差 ≤ 1 像素）。
- **TR6.6（rule）**：宠物窗实际尺寸为 300×400（±2 像素）。
- **TR6.7（rule）**：`npx vue-tsc --noEmit` 退出码 0。
- **TR6.8（rubric, 0-2, 阈值≥1）** 启动稳定性：
  - 2：启动一次成功，中途没有任何 crash 或异常日志。
  - 1：中间有轻微 restart 或 download 延迟，但最终正确显示。
  - 0：启动失败或 crash，无法出现宠物窗。
  - 证据：终端日志。

### 完成证据
- 每个检验点的截图或视频或终端日志片段；tsc 退出码。

---

## Task 7：运行后清理 + 依赖包磁盘占用检查（可选加固）

- **Priority**: low
- **Status**: pending
- **Blocked By**: T6 completed（仅验证成功后再做，若 T6 有 fail 不优先）

### 工作内容

1. 检查 `frontend/node_modules` 总体大小（课程版本可能和原版本占用不同，仅供信息）。
2. 确认父目录 `f:\代码\web` 目录下没有 package.json、package-lock.json、node_modules（上一轮已删除，防止用户再跑错目录时重复生成）。

### 本地 Test Requirements

- **TR7.1（rule）**：`F:\代码\web\package.json`、`package-lock.json`、`node_modules/` 均不存在。

### 完成证据
- 父目录 ls 结果（只包含 frontend/.gitignore/README.md/.trae 等目录，无 node 生态残留）。

---

## 附录：依赖与父验收标准的覆盖矩阵

| 父验收标准 | 覆盖任务 |
|---|---|
| AC1 (package.json 版本匹配) | T1 |
| AC2 (依赖落盘) | T1 |
| AC3 (5 个配置文件) | T2 |
| AC4 (main.js + tray + bugfix) | T3 |
| AC5 (所有 Live2D 相关文件 + 运行时 + 模型) | T4 |
| AC6 (双模式 hash 分流) | T5, T6 |
| AC7 (透明窗五要素) | T3, T6 |
| AC8 (托盘菜单 + 打开设置) | T3, T6 |
| AC9 (Live2D 成功渲染) | T4, T5, T6 |
| AC10 (眼神追踪在宠物身体区不被阻断) | T5, T6 |
| AC11 (IPC 拖拽 + 尺寸不变) | T3 (bugfix), T5 (impl), T6 (verify) |
| AC12 (resize-pet-window, 300x400) | T5, T6 |
| AC13 (无 webkit-app-region 残留) | T5 |
| AC14 (vue-tsc 通过) | T6 |
| R1 (课程一致性) | T1–T5 总和 |
| R2 (拖拽质量 + 眼神修复) | T5, T6 |
| R3 (启动链路稳定) | T6 |
