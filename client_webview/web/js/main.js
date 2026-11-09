/**
 * 应用入口：桥接初始化、视图路由、每秒轮询与退出确认。
 *
 * 数据流：
 *   Python 监控线程 → WebApi.get_snapshot() → 前端每秒拉取 → 各视图渲染
 * 前端不保存业务数据，只保存渲染状态（见 state.js）。
 */
import { api, initBridge } from "./api.js";
import { state } from "./state.js";
import { $, $$ } from "./util.js";
import { showToast } from "./ui.js";
import { initMainView, renderMain } from "./views/main-view.js";
import {
  initSettingsView,
  renderSettings,
  renderUpload,
  switchSettingsPage,
} from "./views/settings-view.js";
import { initHistoryView, renderHistory } from "./views/history-view.js";

// 轮询间隔（毫秒）：与 Python 端每秒采集一次对齐
const POLL_INTERVAL = 1000;

let pollTimer = null;
let polling = false;

/* ============================================================
   轮询与渲染
   ============================================================ */

/**
 * 拉取一次快照并渲染
 */
async function refreshSnapshot() {
  if (polling) return;
  polling = true;
  try {
    const snapshot = await api.getSnapshot();
    if (snapshot) applySnapshot(snapshot);
  } catch (error) {
    console.error("[app] 获取快照失败:", error);
  } finally {
    polling = false;
  }
}

/**
 * 应用一份快照到界面
 * @param {object} snapshot Python 端快照
 */
function applySnapshot(snapshot) {
  renderMain(snapshot);
  renderUpload(snapshot.upload);
  updateUploadPill(snapshot.upload);
  updateHistoryBanner(snapshot);
  notifyUploadStage(snapshot.upload);
}

/**
 * 更新顶栏的上传状态胶囊
 * @param {object} upload 快照里的 upload 字段
 */
function updateUploadPill(upload) {
  if (!upload) return;

  const pill = $("#upload-pill");
  const text = pill.querySelector(".pill-text");
  const stage = upload.stage || "not_login";
  const loggedIn = Boolean(upload.loggedIn);

  let label = "未登录";
  let pillStage = "not_login";

  if (stage === "invalid") {
    label = "登录已失效";
    pillStage = "invalid";
  } else if (loggedIn && stage === "ok") {
    label = "上传正常";
    pillStage = "ok";
  } else if (loggedIn && stage === "error") {
    label = "上传失败";
    pillStage = "error";
  } else if (loggedIn) {
    label = "准备上传";
    pillStage = "not_login";
  }

  pill.dataset.stage = pillStage;
  text.textContent = label;
  pill.title = loggedIn
    ? `已登录：${upload.email || ""}\n${upload.text || ""}${upload.error ? `\n${upload.error}` : ""}`
    : "未登录（登录后每秒上传数据）";
}

/**
 * 根据模式显示/隐藏顶部历史提示条
 * @param {object} snapshot 快照
 */
function updateHistoryBanner(snapshot) {
  const banner = $("#history-banner");
  const text = $("#history-banner-text");
  if (snapshot.mode === "history") {
    banner.hidden = false;
    text.textContent = `正在查看 ${snapshot.viewDate || ""} 的历史数据（${snapshot.historyFile}）`;
  } else {
    banner.hidden = true;
  }
}

/**
 * 上传状态发生变化时给一次轻提示，避免每秒刷屏
 * @param {object} upload 上传状态
 */
function notifyUploadStage(upload) {
  if (!upload) return;
  const stage = upload.stage || "not_login";
  const prev = state.prevUploadStage;
  state.prevUploadStage = stage;

  if (prev === null || prev === stage) return;

  if (stage === "invalid") {
    showToast("登录已失效，请打开设置重新登录", "warn", 3200);
  } else if (stage === "error" && (prev === "ok" || prev === "not_login")) {
    showToast(`数据上传失败：${upload.error || "网络异常"}`, "error", 3200);
  } else if (stage === "ok" && prev === "error") {
    showToast("网络已恢复，继续上传数据", "success", 2400);
  }
}

/**
 * 启动每秒轮询
 */
function startPolling() {
  if (pollTimer) clearInterval(pollTimer);
  pollTimer = setInterval(() => {
    // 窗口最小化/隐藏时不做无意义的渲染，恢复时立即补一次
    if (document.hidden) return;
    refreshSnapshot();
  }, POLL_INTERVAL);
}

/* ============================================================
   视图路由
   ============================================================ */

/**
 * 解析地址栏 hash，支持 #settings/account 这样的二级路由
 * @returns {{view: string, sub: string}}
 */
function parseHash() {
  const raw = location.hash.replace(/^#/, "");
  const [view, sub = ""] = raw.split("/");
  const allowed = ["main", "history", "settings"];
  return { view: allowed.includes(view) ? view : "main", sub };
}

/**
 * 切换主视图
 * @param {"main"|"history"|"settings"} view 视图名
 * @param {string} sub 二级路由（仅设置页使用：general / account / blocked）
 */
function setView(view, sub = "") {
  if (!["main", "history", "settings"].includes(view)) view = "main";
  state.view = view;

  // 同步到地址栏 hash，便于刷新/深链后停留在同一页
  const targetHash = view === "main" ? "" : `#${view}${sub ? `/${sub}` : ""}`;
  if (location.hash !== targetHash) {
    history.replaceState(null, "", `${location.pathname}${location.search}${targetHash}`);
  }

  $$("#view-nav .nav-btn").forEach((button) => {
    button.classList.toggle("is-active", button.dataset.view === view);
  });
  ["main", "history", "settings"].forEach((name) => {
    const node = document.getElementById(`view-${name}`);
    if (node) node.hidden = name !== view;
  });

  if (view === "settings") {
    renderSettings();
    if (sub) switchSettingsPage(sub);
  }
  if (view === "history") renderHistory();
  if (view === "main") refreshSnapshot();
}

/**
 * 打开某个历史文件并回到概览页
 * @param {string} file 形如 data_2026-01-12.json
 */
async function openHistoryFile(file) {
  const result = await api.loadHistory(file);
  if (!result?.ok) {
    showToast(result?.message || "打开历史数据失败", "error");
    return;
  }
  if (result.snapshot) applySnapshot(result.snapshot);
  showToast(result.message || "已切换到历史数据", "success");
  setView("main");
}

/**
 * 返回今天的实时数据
 */
async function backToToday() {
  const result = await api.backToToday();
  if (result?.snapshot) applySnapshot(result.snapshot);
  showToast("已返回今天的实时数据", "success");
  setView("main");
}

/* ============================================================
   退出确认
   ============================================================ */

/**
 * 绑定退出确认对话框。
 * 点击窗口右上角 X 时，Python 端会拦截关闭事件并调用 window.APP.showExitConfirm()。
 */
function bindExitModal() {
  const modal = $("#exit-modal");
  const cancelBtn = $("#btn-exit-cancel");
  const confirmBtn = $("#btn-exit-confirm");

  const hide = () => {
    modal.hidden = true;
  };

  cancelBtn.addEventListener("click", hide);
  modal.addEventListener("click", (event) => {
    if (event.target === modal) hide();
  });

  confirmBtn.addEventListener("click", async () => {
    confirmBtn.disabled = true;
    try {
      await api.confirmExit(true);
      hide();
      showToast("正在退出，正在保存数据…", "info", 1500);
    } catch (error) {
      confirmBtn.disabled = false;
      showToast(`退出失败：${error.message}`, "error");
    }
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !modal.hidden) hide();
  });

  // 供 Python 端 evaluate_js 调用（关闭确认框、托盘菜单等）
  window.APP = {
    showExitConfirm() {
      modal.hidden = false;
      confirmBtn.disabled = false;
      cancelBtn.focus();
    },
    openSettings() {
      setView("settings");
    },
    showMain() {
      setView("main");
    },
  };
}

/* ============================================================
   启动
   ============================================================ */

/**
 * 引导启动：绑定事件 → 初始化桥接 → 首屏渲染 → 开启轮询
 */
async function bootstrap() {
  bindExitModal();

  initMainView({ refresh: refreshSnapshot });
  initSettingsView({ refresh: refreshSnapshot });
  initHistoryView({ onOpen: openHistoryFile });

  // 顶部导航
  $$("#view-nav .nav-btn").forEach((button) => {
    button.addEventListener("click", () => setView(button.dataset.view));
  });

  // 设置页二级导航同步到 hash，方便刷新后停留在同一分页
  $$("#settings-nav .settings-nav-btn").forEach((button) => {
    button.addEventListener("click", () => {
      history.replaceState(null, "", `${location.pathname}${location.search}#settings/${button.dataset.page}`);
    });
  });

  // 手动改 hash（或从外部深链进入）时跟随切换
  window.addEventListener("hashchange", () => {
    const { view, sub } = parseHash();
    setView(view, sub);
  });

  // 历史模式提示条上的“返回今天”
  $("#btn-back-today").addEventListener("click", backToToday);

  // 初始化与 Python 的桥接通道（浏览器中会自动降级为 mock）
  state.mock = await initBridge();
  if (state.mock) {
    showToast("演示模式：未连接 Python 后端，数据为模拟值", "warn", 3600);
  }

  // 首屏数据
  await refreshSnapshot();
  await renderSettings();
  switchSettingsPage("general");

  // 支持通过 hash 直接打开某个视图，如 index.html#settings/account
  const { view, sub } = parseHash();
  setView(view, sub);

  // 回到窗口时立即刷新一次，避免最小化期间界面停留在旧数据
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) refreshSnapshot();
  });

  startPolling();
}

bootstrap().catch((error) => {
  console.error("[app] 启动失败:", error);
  showToast(`界面启动失败：${error.message}`, "error", 4000);
});