/**
 * 设置页视图：通用 / 账号与上传 / 进程屏蔽。
 *
 * 对应旧版 PyQt5 的 three 个设置分页：
 *   1. 通用        开机自启动、数据目录、版本
 *   2. 账号与上传  服务器地址、邮箱、密码、登录/退出、实时上传状态
 *   3. 进程屏蔽    被屏蔽进程列表与解除屏蔽
 */
import { $, $$ } from "../util.js";
import { api } from "../api.js";
import { showToast } from "../ui.js";
import { state } from "../state.js";

const els = {};
let refreshFn = null;
// 登录/退出请求进行中标记：避免重复提交，同时避免轮询把状态文案覆盖掉
let busy = false;

/**
 * 初始化设置页事件绑定
 * @param {{refresh: Function}} options refresh: 立即重新拉取监控快照
 */
export function initSettingsView({ refresh }) {
  refreshFn = refresh;

  els.navButtons = $$("#settings-nav .settings-nav-btn");
  els.pages = {
    general: $("#page-general"),
    account: $("#page-account"),
    blocked: $("#page-blocked"),
  };

  els.toggleStartup = $("#toggle-auto-startup");
  els.startupStatus = $("#auto-startup-status");
  els.dataDirText = $("#data-dir-text");
  els.appVersion = $("#app-version");
  els.openDirBtn = $("#btn-open-dir");

  els.server = $("#input-server");
  els.email = $("#input-email");
  els.password = $("#input-password");
  els.loginForm = $("#login-form");
  els.loginBtn = $("#btn-login");
  els.logoutBtn = $("#btn-logout");
  els.statusCard = $("#account-status");
  els.statusText = $("#account-status-text");

  els.blockedList = $("#blocked-list");
  els.unblockBtn = $("#btn-unblock");
  els.refreshBlockedBtn = $("#btn-refresh-blocked");

  // 分页切换
  els.navButtons.forEach((button) => {
    button.addEventListener("click", () => switchSettingsPage(button.dataset.page));
  });

  // 开机自启动开关
  els.toggleStartup.addEventListener("change", async () => {
    const enabled = els.toggleStartup.checked;
    els.toggleStartup.disabled = true;
    try {
      const result = await api.setAutoStartup(enabled);
      if (result?.ok) {
        els.startupStatus.textContent = enabled ? "已开启" : "已关闭";
        showToast(result.message || (enabled ? "已开启开机自启动" : "已关闭开机自启动"), "success", 1800);
      } else {
        els.toggleStartup.checked = !enabled;
        showToast(result?.message || "设置失败", "error");
        els.startupStatus.textContent = els.toggleStartup.checked ? "已开启" : "已关闭";
      }
    } finally {
      els.toggleStartup.disabled = false;
    }
  });

  // 打开数据目录
  els.openDirBtn.addEventListener("click", async () => {
    const result = await api.openDataDir();
    if (!result?.ok) showToast(result?.message || "打开目录失败", "error");
  });

  // 记住用户改动过的输入框，避免轮询用配置值覆盖正在编辑的内容
  [els.server, els.email].forEach((input) => {
    input.addEventListener("input", () => {
      input.dataset.touched = "1";
    });
  });

  // 登录表单
  els.loginForm.addEventListener("submit", handleLogin);
  els.logoutBtn.addEventListener("click", handleLogout);

  // 屏蔽列表
  els.unblockBtn.addEventListener("click", handleUnblock);
  els.refreshBlockedBtn.addEventListener("click", () => renderBlockedList());
}

/**
 * 切换设置页分页
 * @param {"general"|"account"|"blocked"} page 分页标识
 */
export function switchSettingsPage(page) {
  if (!els.pages[page]) return;
  state.settingsPage = page;
  els.navButtons.forEach((button) => {
    button.classList.toggle("is-active", button.dataset.page === page);
  });
  Object.entries(els.pages).forEach(([name, node]) => {
    node.hidden = name !== page;
  });
  if (page === "blocked") renderBlockedList();
}

/**
 * 拉取并渲染全部设置信息
 */
export async function renderSettings() {
  const settings = await api.getSettings();
  if (!settings) return;
  state.settings = settings;

  els.appVersion.textContent = settings.appVersion || "-";
  els.dataDirText.textContent = settings.dataDir || "-";

  els.startupStatus.textContent = settings.autoStartup ? "已开启" : "已关闭";
  els.toggleStartup.checked = Boolean(settings.autoStartup);

  if (!els.server.dataset.touched) els.server.value = settings.serverUrl || "";
  if (!els.email.dataset.touched) els.email.value = settings.userEmail || "";
  els.server.placeholder = settings.defaultServerUrl || "http://localhost:8080";

  els.loginBtn.disabled = Boolean(settings.loggedIn) || busy;
  els.logoutBtn.disabled = !settings.loggedIn || busy;

  if (state.settingsPage === "blocked") renderBlockedList();
}

/**
 * 根据快照中的上传状态刷新“账号与上传”卡片（轮询每秒调用）
 * @param {object} upload 快照里的 upload 字段
 */
export function renderUpload(upload) {
  if (!upload || busy) return;

  const loggedIn = Boolean(upload.loggedIn);
  els.loginBtn.disabled = loggedIn;
  els.logoutBtn.disabled = !loggedIn;

  let text;
  let stage = upload.stage || "not_login";

  if (loggedIn) {
    const email = upload.email || "未知邮箱";
    if (stage === "ok") {
      text = `已登录：${email}\n${upload.text}`;
    } else if (stage === "error") {
      text = `已登录：${email}\n上传失败：${upload.error}`;
    } else if (stage === "not_login") {
      text = `已登录：${email}\n正在启动上传…`;
    } else {
      text = `已登录：${email}\n当前状态：${upload.text}`;
    }
  } else if (stage === "invalid") {
    text = `登录已失效，请重新登录\n${upload.error || ""}`;
  } else {
    text = "未登录（登录后每秒自动上传数据）";
  }

  setStatus(stage, text);
}

/**
 * 更新账号状态卡片
 * @param {string} stage 状态阶段
 * @param {string} text 展示文案
 */
function setStatus(stage, text) {
  els.statusCard.dataset.stage = stage;
  els.statusText.textContent = text;
}

/**
 * 提交登录表单
 * @param {SubmitEvent} event 表单提交事件
 */
async function handleLogin(event) {
  event.preventDefault();
  if (busy) return;

  const serverUrl = els.server.value.trim() || els.server.placeholder || "http://localhost:8080";
  const email = els.email.value.trim();
  const password = els.password.value;

  if (!email || !email.includes("@")) {
    showToast("请输入正确的邮箱", "warn");
    els.email.focus();
    return;
  }
  if (!password) {
    showToast("请输入登录密码", "warn");
    els.password.focus();
    return;
  }

  busy = true;
  els.loginBtn.disabled = true;
  els.logoutBtn.disabled = true;
  els.loginBtn.textContent = "正在登录…";
  setStatus("not_login", "正在登录，请稍候…");

  try {
    const result = await api.login(serverUrl, email, password);
    if (result?.ok) {
      els.password.value = "";
      showToast(result.message || "登录成功", "success");
      els.server.dataset.touched = "";
      els.email.dataset.touched = "";
      await renderSettings();
      refreshFn?.();
    } else {
      setStatus("error", `登录失败：${result?.message || "未知错误"}`);
      showToast(result?.message || "登录失败", "error", 3200);
    }
  } catch (error) {
    setStatus("error", `登录失败：${error.message}`);
    showToast(`登录失败：${error.message}`, "error", 3200);
  } finally {
    busy = false;
    els.loginBtn.textContent = "登录并开始上传";
    // 重新按当前登录状态启用按钮
    const loggedIn = Boolean(state.snapshot?.upload?.loggedIn);
    els.loginBtn.disabled = loggedIn;
    els.logoutBtn.disabled = !loggedIn;
  }
}

/**
 * 退出登录
 */
async function handleLogout() {
  if (busy) return;
  busy = true;
  els.loginBtn.disabled = true;
  els.logoutBtn.disabled = true;
  setStatus("not_login", "正在退出登录…");

  try {
    const result = await api.logout();
    showToast(result?.message || "已退出登录", result?.ok ? "success" : "error");
    els.server.dataset.touched = "";
    els.email.dataset.touched = "";
    await renderSettings();
    refreshFn?.();
  } catch (error) {
    showToast(`退出登录失败：${error.message}`, "error");
  } finally {
    busy = false;
    els.loginBtn.disabled = false;
    els.logoutBtn.disabled = true;
  }
}

/**
 * 渲染被屏蔽的进程列表
 */
export async function renderBlockedList() {
  const list = await api.getBlockedList();
  state.selectedBlocked = null;
  els.unblockBtn.disabled = true;
  els.blockedList.replaceChildren();

  if (!list || list.length === 0) {
    const empty = document.createElement("div");
    empty.className = "blocked-empty";
    empty.textContent = "当前没有被屏蔽的进程";
    els.blockedList.appendChild(empty);
    return;
  }

  list.forEach((item) => {
    const row = document.createElement("div");
    row.className = "blocked-item";
    row.dataset.name = item.name;

    const info = document.createElement("div");
    const name = document.createElement("div");
    name.className = "blocked-name";
    name.textContent = item.display;
    info.appendChild(name);

    // 备注名与真实进程名不同时才额外显示原始名称，避免重复
    if (item.display !== item.name) {
      const raw = document.createElement("div");
      raw.className = "blocked-raw";
      raw.textContent = item.name;
      info.appendChild(raw);
    }

    const hint = document.createElement("span");
    hint.className = "blocked-hint";
    hint.textContent = "点击选中";

    row.append(info, hint);
    row.addEventListener("click", () => selectBlockedRow(row));
    els.blockedList.appendChild(row);
  });
}

/**
 * 选中/取消选中某个屏蔽项
 * @param {HTMLElement} row 列表项
 */
function selectBlockedRow(row) {
  const alreadySelected = row.classList.contains("is-selected");
  Array.from(els.blockedList.querySelectorAll(".blocked-item")).forEach((node) => {
    node.classList.remove("is-selected");
  });
  if (alreadySelected) {
    state.selectedBlocked = null;
    els.unblockBtn.disabled = true;
    return;
  }
  row.classList.add("is-selected");
  state.selectedBlocked = row.dataset.name;
  els.unblockBtn.disabled = false;
}

/**
 * 解除选中进程的屏蔽
 */
async function handleUnblock() {
  const name = state.selectedBlocked;
  if (!name) {
    showToast("请先点击选中要解除的进程", "warn");
    return;
  }
  const result = await api.unblockProcess(name);
  if (result?.ok) {
    showToast(`已解除屏蔽：${result.display}`, "success");
    await renderBlockedList();
    refreshFn?.();
  } else {
    showToast(result?.message || "解除屏蔽失败", "error");
  }
}