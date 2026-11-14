<template>
  <!-- ★ 设置窗口 -->
  <div v-if="isSettings" class="settings-window">
    <div class="settings-titlebar">
      <span>设置</span>
      <button class="titlebar-close" @click="closeSettings">X</button>
    </div>
    <div class="settings-tabs">
      <button :class="{ active: settingsTab === 'chat' }" @click="settingsTab = 'chat'">聊天记录</button>
      <button :class="{ active: settingsTab === 'shortcut' }" @click="settingsTab = 'shortcut'">快捷键</button>
    </div>

    <!-- 聊天 Tab -->
    <div v-if="settingsTab === 'chat'" class="settings-chat" ref="chatArea">
      <div v-for="(msg, i) in displayConversation" :key="i" :class="['bubble', msg.role]">
        {{ msg.text }}
      </div>
      <div v-if="loading" class="bubble pet loading-bubble">
        <span class="dots"><span>.</span><span>.</span><span>.</span></span>
      </div>
    </div>
    <div v-if="settingsTab === 'chat'" class="chat-input-bar">
      <input v-model="textInput" class="chat-text-input" placeholder="输入消息..." @keydown.enter="sendTextMessage" />
      <button class="chat-send-btn" @click="sendTextMessage" :disabled="!textInput.trim() || loading">发送</button>
    </div>

    <!-- 快捷键 Tab -->
    <div v-if="settingsTab === 'shortcut'" class="shortcut-settings">
      <div class="shortcut-current">
        当前快捷键 <span class="key-badge">{{ currentShortcut }}</span>
      </div>
      <div v-if="capturing" class="shortcut-capturing">正在监听按键...</div>
      <div v-if="capturedKey" class="shortcut-new">
        新快捷键: <span class="key-badge">{{ capturedKey }}</span>
      </div>
      <div class="shortcut-actions">
        <button v-if="!capturing" class="shortcut-btn" @click="startCapture">设置快捷键</button>
        <button v-if="capturedKey" class="shortcut-btn primary" @click="saveShortcut">保存</button>
      </div>
    </div>
  </div>

  <!-- ★ 宠物窗口 -->
  <div v-else class="pet-window">
    <div
      ref="petBodyRef"
      class="pet-body"
      :style="{ width: modelConfig.canvas.width + 'px', height: modelConfig.canvas.height + 'px' }"
      @pointerdown="startDrag"
      @pointermove="onDragMove"
      @pointerup="endDrag"
      @pointercancel="endDrag"
      :class="{ dragging: isDragging }"
    >
      <Live2DCanvas
        ref="live2dRef"
        :width="modelConfig.canvas.width"
        :height="modelConfig.canvas.height"
        :model-name="modelConfig.modelName"
        :model-file="modelConfig.modelFile"
        :scale="modelConfig.scale"
        :x-multiplier="modelConfig.position.xMultiplier"
        :y-multiplier="modelConfig.position.yMultiplier"
        @loaded="onModelLoaded"
        @hit="onHit"
      />
      <!-- ★ 对话气泡：放在 pet-body 内部，bottom:100% 贴头顶上方 -->
      <div v-if="hintText" class="speech-hint">{{ hintText }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from "vue"
import Live2DCanvas from "./components/Live2DCanvas.vue"
import { modelConfig } from "./config"

// ★ 核心：通过 URL hash 判断当前模式
const isSettings = window.location.hash === "#settings"

// Electron IPC（仅 Electron 环境可用，浏览器开发时优雅降级）
type IpcLike = {
  send?: (ch: string, ...args: any[]) => void
  on?: (ch: string, listener: (event: any, ...args: any[]) => void) => void
  sendSync?: (ch: string, ...args: any[]) => any
  removeAllListeners?: (ch: string) => void
}
const ipcRenderer: IpcLike | null =
  (typeof window !== "undefined" && (window as any).require && (window as any).require("electron")?.ipcRenderer) || null

// 后端 SSE 接口（沿用第 1 课；端口与 application.properties 同步）
const API = "http://localhost:18088/api/text-chat"

// -------- 共享状态 --------
interface Message { role: "user" | "pet"; text: string }
const conversation = ref<Message[]>([])

// 把连续的 pet 消息合并成一条，方便聊天面板显示
const displayConversation = computed(() => {
  const merged: Message[] = []
  for (const msg of conversation.value) {
    const last = merged[merged.length - 1]
    if (last && last.role === "pet" && msg.role === "pet") {
      last.text += msg.text
    } else {
      merged.push({ ...msg })
    }
  }
  return merged
})

// -------- 设置窗口状态 --------
const settingsTab = ref<"chat" | "shortcut">("chat")
const loading = ref(false)
const textInput = ref("")
const chatArea = ref<HTMLDivElement>()
const currentShortcut = ref("Ctrl+Alt+L")
const capturedKey = ref("")
const capturing = ref(false)

let sseAbort: AbortController | null = null

function scrollDown() {
  nextTick(() => {
    if (chatArea.value) chatArea.value.scrollTop = chatArea.value.scrollHeight
  })
}

// ★ 通知主进程：对话更新了（深拷贝避免引用共享导致的数据混乱）
function notifyConvUpdate() {
  if (ipcRenderer?.send) {
    ipcRenderer.send("conv-update", JSON.parse(JSON.stringify(conversation.value)))
  }
}

// ★ SSE 流式聊天 + 多气泡切分（标点或满 20 字就截断）
async function sendTextMessage() {
  let currentChunk = ""
  const text = textInput.value.trim()
  if (!text || loading.value) return
  textInput.value = ""
  loading.value = true

  // 1. 添加用户消息
  conversation.value.push({ role: "user", text })
  notifyConvUpdate()
  scrollDown()
  hintText.value = "..." // 宠物窗口显示加载指示

  try {
    sseAbort = new AbortController()
    const res = await fetch(API, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ text }),
      signal: sseAbort.signal
    })
    const reader = res.body!.getReader()
    const decoder = new TextDecoder()
    let buf = ""

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      const parts = buf.split("\n\n")
      buf = parts.pop() || ""
      for (const part of parts) {
        if (!part.startsWith("data:")) continue
        const data = part.substring(5).trim()
        if (!data || data === "[DONE]") continue
        currentChunk += data

        // ★ 多气泡切分：遇到标点或满 20 字就截断
        while (currentChunk.length > 0) {
          const punctMatch = currentChunk.match(/[。！？\n]/)
          const punctIdx = punctMatch ? punctMatch.index! + 1 : -1
          let splitAt: number
          if (punctIdx > 0 && punctIdx <= 20) {
            splitAt = punctIdx
          } else if (currentChunk.length >= 20) {
            splitAt = 20
          } else {
            break
          }
          const segment = currentChunk.substring(0, splitAt).trim()
          currentChunk = currentChunk.substring(splitAt)
          if (segment) {
            conversation.value.push({ role: "pet", text: segment })
            notifyConvUpdate() // 2. 同步每个气泡
            hintText.value = segment // 实时更新宠物气泡
            scrollDown()
          }
        }
      }
    }
    // 3. 吐出不满 20 字的残句
    if (currentChunk.trim()) {
      conversation.value.push({ role: "pet", text: currentChunk.trim() })
      notifyConvUpdate()
      hintText.value = currentChunk.trim()
      scrollDown()
    }
  } catch (e: any) {
    if (e?.name !== "AbortError") {
      // 4. 同步错误消息
      conversation.value.push({ role: "pet", text: "网络好像不太好，请稍后再试" })
      notifyConvUpdate()
    }
  } finally {
    loading.value = false
  }
}

// -------- 快捷键 Tab：可视化捕获 --------
function startCapture() {
  capturing.value = true
  capturedKey.value = ""
  const onKeyDown = (e: KeyboardEvent) => {
    e.preventDefault(); e.stopPropagation()
    if (e.key === "Escape") {
      capturing.value = false
      window.removeEventListener("keydown", onKeyDown, true)
      return
    }
    // 修饰键单独按下不触发，等普通键一起
    if (["Control", "Alt", "Shift", "Meta"].includes(e.key)) return
    const parts: string[] = []
    if (e.ctrlKey) parts.push("Ctrl")
    if (e.altKey) parts.push("Alt")
    if (e.shiftKey) parts.push("Shift")
    if (e.metaKey) parts.push("Meta")
    parts.push(e.key.length === 1 ? e.key.toUpperCase() : e.key)
    capturedKey.value = parts.join("+")
    capturing.value = false
    window.removeEventListener("keydown", onKeyDown, true)
  }
  // capture 阶段拦截，防止浏览器默认行为（如 Ctrl+T 新建标签页）
  window.addEventListener("keydown", onKeyDown, true)
}

function saveShortcut() {
  if (!capturedKey.value || !ipcRenderer?.send) return
  ipcRenderer.send("set-shortcut", capturedKey.value)
  currentShortcut.value = capturedKey.value
  capturedKey.value = ""
}

// 关闭设置窗口：依赖主进程 close 事件 preventDefault + hide()
function closeSettings() {
  window.close()
}

// -------- 宠物窗口状态 --------
const live2dRef = ref<any>()
const hintText = ref("") // ★ 气泡文字
const isDragging = ref(false)
const lastClientX = ref(0)
const lastClientY = ref(0)
const capturedPointerId = ref<number | null>(null)
const petBodyRef = ref<HTMLDivElement>()
let currentPassthrough = true // 当前是否处于鼠标穿透状态（true=穿透，false=可交互）

function onModelLoaded(name: string) {
  console.log("[pet] model loaded:", name)
}
function onHit(areas: string[]) {
  if (areas.length > 0) live2dRef.value?.live2d?.randomMotion()
}

// 眼球追踪：全局鼠标移动 → lookAt() + 按需穿透切换
function onGlobalPointerMove(e: PointerEvent) {
  if (e.pointerType !== "mouse") return
  live2dRef.value?.live2d?.lookAt(e.clientX, e.clientY)
  // ★ 按需穿透：鼠标在 pet-body 内 → 关穿透（能拖拽）；在外 → 开穿透（透给下层应用）
  if (!isSettings && ipcRenderer?.send && petBodyRef.value && !isDragging.value) {
    const rect = petBodyRef.value.getBoundingClientRect()
    const inside = e.clientX >= rect.left && e.clientX <= rect.right &&
                   e.clientY >= rect.top && e.clientY <= rect.bottom
    if (inside === currentPassthrough) {
      currentPassthrough = !inside
      ipcRenderer.send("set-mouse-passthrough", currentPassthrough)
    }
  }
}

// IPC 拖拽：pointer events + move-window，避开 -webkit-app-region: drag 阻塞
function startDrag(e: PointerEvent) {
  if (e.pointerType !== "mouse" || e.button !== 0) return
  isDragging.value = true
  // ★ 用屏幕坐标：窗口移动不影响 screenX/Y，避免 client 反馈循环导致抖动
  lastClientX.value = e.screenX
  lastClientY.value = e.screenY
  // ★ 拖拽期间必须关穿透，否则 pointermove 会丢失
  if (!isSettings && ipcRenderer?.send && currentPassthrough) {
    currentPassthrough = false
    ipcRenderer.send("set-mouse-passthrough", false)
  }
  try {
    ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
    capturedPointerId.value = e.pointerId
  } catch {
    capturedPointerId.value = null
  }
}
function onDragMove(e: PointerEvent) {
  live2dRef.value?.live2d?.lookAt(e.clientX, e.clientY)
  if (!isDragging.value) return
  // ★ screenX/Y 是屏幕绝对坐标，不受窗口移动影响
  const dx = e.screenX - lastClientX.value
  const dy = e.screenY - lastClientY.value
  if ((dx !== 0 || dy !== 0) && ipcRenderer?.send) {
    ipcRenderer.send("move-window", Math.round(dx), Math.round(dy))
  }
  lastClientX.value = e.screenX
  lastClientY.value = e.screenY
}
function endDrag(e: PointerEvent) {
  if (!isDragging.value) return
  isDragging.value = false
  if (capturedPointerId.value !== null) {
    try {
      ;(e.currentTarget as HTMLElement).releasePointerCapture(capturedPointerId.value)
    } catch { /* ignore */ }
    capturedPointerId.value = null
  }
}

// ★ 气泡播放队列：按文字类型估算停留时长（中文~170ms/字，英文/数字~50ms/字，标点不计），依次播放
let bubbleQueue: string[] = []
let bubbleTimer: number | null = null

function showNextBubble() {
  if (bubbleQueue.length === 0) {
    hintText.value = ""
    bubbleTimer = null
    return
  }
  const text = bubbleQueue.shift()!
  hintText.value = text
  const cjk = (text.match(/[\u4e00-\u9fff]/g) || []).length
  const eng = (text.match(/[a-zA-Z0-9]/g) || []).length
  const ms = Math.max(1500, Math.min(8000, cjk * 170 + eng * 50))
  bubbleTimer = window.setTimeout(showNextBubble, ms)
}

// 主进程广播的对话更新（宠物窗口接收）
function onConvUpdate(_e: any, msgs: Message[]) {
  const prevLen = conversation.value.length
  conversation.value = Array.isArray(msgs) ? msgs : []
  // 仅在宠物窗口把新增的 pet 消息塞进气泡队列
  if (!isSettings) {
    for (let i = prevLen; i < msgs.length; i++) {
      const msg = msgs[i]
      if (msg && msg.role === "pet") bubbleQueue.push(msg.text)
    }
    if (!bubbleTimer && bubbleQueue.length > 0) showNextBubble()
  }
}

onMounted(() => {
  // 全局眼球追踪（宠物窗口始终需要）
  window.addEventListener("pointermove", onGlobalPointerMove, { passive: true })

  if (isSettings) {
    // 设置窗口：拉取当前快捷键 + 历史对话 + 订阅广播
    try {
      if (ipcRenderer?.sendSync) {
        currentShortcut.value = ipcRenderer.sendSync("get-shortcut") || "Ctrl+Alt+L"
        const got = ipcRenderer.sendSync("get-conversation")
        if (Array.isArray(got)) conversation.value = got
      }
    } catch { /* ignore */ }
    ipcRenderer?.on?.("conv-update", onConvUpdate)
    return
  }

  // 宠物窗口：拉历史 + 订阅广播
  try {
    if (ipcRenderer?.sendSync) {
      const got = ipcRenderer.sendSync("get-conversation")
      if (Array.isArray(got)) conversation.value = got
    }
  } catch { /* ignore */ }
  ipcRenderer?.on?.("conv-update", onConvUpdate)

  // 通知主进程把窗口尺寸调整到和画布一致
  if (ipcRenderer?.send) {
    ipcRenderer.send("resize-pet-window", modelConfig.canvas.width, modelConfig.canvas.height)
    // ★ 初始穿透：透明区透给下层应用，forward:true 仍转发 move 做眼球追踪
    ipcRenderer.send("set-mouse-passthrough", true)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener("pointermove", onGlobalPointerMove)
  ipcRenderer?.removeAllListeners?.("conv-update")
  if (bubbleTimer !== null) {
    window.clearTimeout(bubbleTimer)
    bubbleTimer = null
  }
})
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body, #app {
  width: 100%; height: 100%; overflow: hidden;
  background: transparent;
}
body { font-family: "Microsoft YaHei", sans-serif; }
</style>

<style scoped>
/* ═══ 设置窗口 ═══ */
.settings-window {
  width: 100vw; height: 100vh;
  background: #1a1a2e;
  color: #c8d0d8;
  display: flex; flex-direction: column;
  overflow: hidden;
}
.settings-titlebar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 6px 12px;
  flex-shrink: 0;
  -webkit-app-region: drag;
  border-bottom: 1px solid rgba(100, 150, 255, 0.12);
}
.settings-titlebar span { font-size: 13px; color: #aaaacc; }
.titlebar-close {
  -webkit-app-region: no-drag;
  background: none; border: none;
  color: #7777aa; font-size: 14px;
  cursor: pointer; padding: 2px 6px;
  border-radius: 4px; line-height: 1;
}
.titlebar-close:hover { background: rgba(255, 80, 80, 0.25); color: #ff6666; }

.settings-tabs {
  display: flex;
  border-bottom: 1px solid rgba(100, 150, 255, 0.15);
  flex-shrink: 0;
}
.settings-tabs button {
  flex: 1; padding: 10px 0;
  background: transparent; border: none;
  color: #7777aa; font-size: 14px;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all 0.15s;
}
.settings-tabs button.active {
  color: #7cc8ff;
  border-bottom-color: #7cc8ff;
}

/* ═══ 聊天区域 ═══ */
.settings-chat {
  flex: 1; overflow-y: auto;
  padding: 8px 12px;
  display: flex; flex-direction: column;
  gap: 6px;
}
.settings-chat::-webkit-scrollbar { width: 4px; }
.settings-chat::-webkit-scrollbar-thumb {
  background: #444466; border-radius: 2px;
}
.chat-input-bar {
  display: flex; gap: 6px;
  padding: 8px 12px;
  border-top: 1px solid rgba(100, 150, 255, 0.15);
  flex-shrink: 0; background: #16162a;
}
.chat-text-input {
  flex: 1; padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid rgba(100, 150, 255, 0.2);
  background: #0d0d1a; color: #c8d0d8;
  font-size: 13px; outline: none;
  font-family: inherit;
}
.chat-text-input:focus { border-color: rgba(100, 150, 255, 0.4); }
.chat-send-btn {
  padding: 8px 16px; border-radius: 8px;
  border: none;
  background: rgba(100, 150, 255, 0.2);
  color: #c0c0ee; cursor: pointer;
  font-size: 13px; white-space: nowrap;
  transition: all 0.15s;
}
.chat-send-btn:hover { background: rgba(100, 150, 255, 0.35); }
.chat-send-btn:disabled { opacity: 0.4; cursor: default; }

/* ═══ 气泡 ═══ */
.bubble {
  max-width: 85%; padding: 7px 12px;
  border-radius: 14px;
  font-size: 13px; line-height: 1.5;
  word-break: break-word; flex-shrink: 0;
}
.bubble.user {
  align-self: flex-end;
  background: #2979ff; color: white;
  border-bottom-right-radius: 4px;
}
.bubble.pet {
  align-self: flex-start;
  background: #2e3440; color: #c8d0d8;
  border-bottom-left-radius: 4px;
}
.loading-bubble { padding: 12px 16px; }
.dots span {
  display: inline-block;
  width: 6px; height: 6px;
  background: #7cc8ff; border-radius: 50%;
  margin: 0 3px;
  animation: dot-bounce 1.2s infinite;
}
.dots span:nth-child(2) { animation-delay: 0.2s; }
.dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes dot-bounce {
  0%, 100% { transform: translateY(0); opacity: 0.3; }
  50%      { transform: translateY(-6px); opacity: 1; }
}

/* ═══ 快捷键 Tab ═══ */
.shortcut-settings {
  padding: 24px 16px;
  display: flex; flex-direction: column;
  align-items: center; gap: 12px;
}
.shortcut-current { font-size: 14px; color: #aaaacc; margin-bottom: 16px; }
.shortcut-capturing {
  font-size: 14px; color: #ffaa66; margin-bottom: 12px;
  animation: pulse-text 1s infinite;
}
.shortcut-new { font-size: 13px; color: #aacccc; margin-bottom: 16px; }
.shortcut-actions { display: flex; gap: 8px; justify-content: center; }
.shortcut-btn {
  padding: 8px 18px; border-radius: 8px;
  border: 1px solid rgba(100, 150, 255, 0.25);
  background: rgba(100, 150, 255, 0.1);
  color: #c0c0ee; cursor: pointer; font-size: 13px;
}
.shortcut-btn:hover { background: rgba(100, 150, 255, 0.2); }
.shortcut-btn.primary {
  background: rgba(33, 150, 243, 0.3);
  border-color: rgba(33, 150, 243, 0.4); color: white;
}
@keyframes pulse-text {
  0%, 100% { opacity: 0.6; }
  50%      { opacity: 1; }
}
.key-badge {
  display: inline-block;
  background: rgba(100, 150, 255, 0.2);
  color: #7cc8ff; padding: 2px 8px;
  border-radius: 6px;
  font-family: monospace; font-size: 13px;
}

/* ═══ 宠物窗口 ═══ */
.pet-window {
  width: 100vw; height: 100vh;
  display: flex; align-items: center; justify-content: center;
  user-select: none;
}
.pet-body {
  position: relative; cursor: grab;
  flex-shrink: 0; touch-action: none;
}
.pet-body.dragging { cursor: grabbing; }

/* ═══ 对话气泡（贴 pet-body 头顶上方）═══ */
.speech-hint {
  position: absolute;
  bottom: calc(100% + 8px); /* 头顶上方 8px */
  left: 50%;
  transform: translateX(-50%);
  min-width: 80px; max-width: 340px;
  background: rgba(20, 25, 50, 0.95);
  border: 1px solid rgba(100, 160, 255, 0.35);
  border-radius: 12px 12px 4px 12px;
  padding: 8px 14px;
  font-size: 12px; line-height: 1.6;
  color: #c8d2e8; z-index: 10;
  pointer-events: none;
  animation: popIn 0.25s ease-out;
  text-align: center;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 240px; overflow-y: auto;
}
@keyframes popIn {
  from { opacity: 0; transform: translateX(-50%) translateY(8px) scale(0.9); }
  to   { opacity: 1; transform: translateX(-50%) translateY(0) scale(1); }
}
</style>
