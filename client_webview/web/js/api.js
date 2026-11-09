/**
 * pywebview 桥接封装。
 *
 * pywebview 会把 Python 端 WebApi 的公开方法注入到 window.pywebview.api，
 * 这里统一做三件事：
 *   1. 等待桥接通道就绪（页面加载完成与 JS API 注入之间存在时间差）；
 *   2. 统一异常处理，返回 Promise；
 *   3. 在普通浏览器里打开时自动降级为 mock 数据，方便单独调试前端。
 */

const BRIDGE_TIMEOUT = 8000;

let bridgePromise = null;
let mockMode = false;

/** 是否处于 mock（纯前端预览）模式 */
export const isMock = () => mockMode;

/**
 * 等待 window.pywebview.api 就绪
 * @returns {Promise<object>} pywebview 注入的 api 对象
 */
function waitForBridge() {
  if (bridgePromise) return bridgePromise;

  bridgePromise = new Promise((resolve, reject) => {
    if (window.pywebview && window.pywebview.api) {
      resolve(window.pywebview.api);
      return;
    }

    const startedAt = Date.now();
    const timer = setInterval(() => {
      if (window.pywebview && window.pywebview.api) {
        clearInterval(timer);
        resolve(window.pywebview.api);
      } else if (Date.now() - startedAt > BRIDGE_TIMEOUT) {
        clearInterval(timer);
        reject(new Error("桥接通道未就绪，请通过 python main.py 启动"));
      }
    }, 40);

    window.addEventListener(
      "pywebviewready",
      () => {
        clearInterval(timer);
        if (window.pywebview && window.pywebview.api) resolve(window.pywebview.api);
      },
      { once: true }
    );
  });

  return bridgePromise;
}

/**
 * 调用 Python 端方法
 * @param {string} method 方法名（与 WebApi 中的 snake_case 名称一致）
 * @param {...any} args 参数
 * @returns {Promise<any>}
 */
export function call(method, ...args) {
  if (mockMode) return mockCall(method, ...args);

  return waitForBridge().then((bridge) => {
    const fn = bridge[method];
    if (typeof fn !== "function") {
      throw new Error(`接口不存在: ${method}`);
    }
    return fn(...args);
  });
}

/* ============================================================
   mock 数据（仅在浏览器中直接打开页面时使用）
   ============================================================ */

const mockState = {
  sortType: "useTime_down",
  mode: "live",
  historyFile: "",
  autoStartup: false,
  loggedIn: false,
  userEmail: "demo@example.com",
  serverUrl: "http://localhost:8080",
  blocked: new Set(["vlc.exe"]),
  alias: { "chrome.exe": "Chrome 浏览器", "Code.exe": "VS Code" },
  apps: [
    { name: "chrome.exe", seconds: 5231, running: true },
    { name: "Code.exe", seconds: 3620, running: true },
    { name: "WeChat.exe", seconds: 2145, running: true },
    { name: "mstsc.exe", seconds: 1680, running: false },
    { name: "vlc.exe", seconds: 940, running: false },
    { name: "explorer.exe", seconds: 620, running: true },
    { name: "TIM.exe", seconds: 480, running: false },
  ],
  stats: { keyboardCount: 8421, mouseClickCount: 2145, mouseDistance: 312.45, totalDuration: 9630 },
  activeName: "Code.exe",
};

// 模拟每秒上涨
setInterval(() => {
  if (mockState.mode !== "live") return;
  mockState.apps.forEach((app) => {
    if (app.running) app.seconds += 1;
  });
  mockState.stats.totalDuration += 1;
}, 1000);

/** 生成 mock 的空值校验（保证前端代码路径一致） */
function mockNow() {
  const now = new Date();
  const pad = (n) => String(n).padStart(2, "0");
  return `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;
}

/** mock 快照 */
function mockSnapshot() {
  const sortType = mockState.sortType;
  const items = mockState.apps
    .filter((app) => !mockState.blocked.has(app.name))
    .map((app) => ({
      name: app.name,
      display: mockState.alias[app.name] || app.name,
      alias: mockState.alias[app.name] || "",
      seconds: app.seconds,
      text: formatSeconds(app.seconds),
      running: app.running,
      active: app.name === mockState.activeName,
    }));

  const sorters = {
    windowName_up: (a, b) => a.display.toLowerCase().localeCompare(b.display.toLowerCase()),
    windowName_down: (a, b) => b.display.toLowerCase().localeCompare(a.display.toLowerCase()),
    useTime_up: (a, b) => a.seconds - b.seconds,
    useTime_down: (a, b) => b.seconds - a.seconds,
  };
  items.sort(sorters[sortType] || sorters.useTime_down);

  return {
    mode: mockState.mode,
    date: new Date().toISOString().slice(0, 10),
    historyFile: mockState.historyFile,
    viewDate: mockState.mode === "history" ? mockState.historyFile.replace(/^data_/, "").replace(/\.json$/, "") : new Date().toISOString().slice(0, 10),
    apps: items,
    appCount: items.length,
    blockedCount: mockState.blocked.size,
    sortType,
    stats: {
      keyboardCount: mockState.stats.keyboardCount,
      mouseClickCount: mockState.stats.mouseClickCount,
      mouseDistance: mockState.stats.mouseDistance,
      totalDuration: mockState.stats.totalDuration,
      totalDurationText: formatSeconds(mockState.stats.totalDuration),
    },
    upload: {
      stage: mockState.loggedIn ? "ok" : "not_login",
      text: mockState.loggedIn ? `已连接，上次上传 ${mockNow()}` : "未登录",
      error: "",
      loggedIn: mockState.loggedIn,
      email: mockState.userEmail,
    },
    now: mockNow(),
  };
}

function formatSeconds(seconds) {
  const total = Math.max(0, Math.floor(Number(seconds) || 0));
  const pad = (n) => String(n).padStart(2, "0");
  return `${pad(Math.floor(total / 3600))}:${pad(Math.floor((total % 3600) / 60))}:${pad(total % 60)}`;
}

/** mock 版接口实现 */
async function mockCall(method, ...args) {
  const respond = (data) => new Promise((resolve) => setTimeout(() => resolve(data), 30));

  switch (method) {
    case "ping":
      return respond({ ok: true, version: "mock", title: "屏幕视奸器" });
    case "get_snapshot":
      return respond(mockSnapshot());
    case "get_settings":
      return respond({
        appName: "屏幕视奸器",
        appVersion: "2.0.0-webview (mock)",
        serverUrl: mockState.serverUrl,
        userEmail: mockState.userEmail,
        loggedIn: mockState.loggedIn,
        autoStartup: mockState.autoStartup,
        sortType: mockState.sortType,
        hasSavedPassword: false,
        savedPasswordEmail: "",
        dataDir: "D:\\Code\\Projects\\DeviceUsageTime\\client_webview",
        defaultServerUrl: "http://localhost:8080",
        blockedCount: mockState.blocked.size,
      });
    case "set_sort":
      mockState.sortType = args[0];
      return respond({ ok: true, sortType: mockState.sortType });
    case "set_alias": {
      const [name, alias] = args;
      if (alias && alias.trim() && alias.trim() !== name) mockState.alias[name] = alias.trim();
      else delete mockState.alias[name];
      return respond({ ok: true, display: mockState.alias[name] || name });
    }
    case "block_process":
      mockState.blocked.add(args[0]);
      return respond({ ok: true });
    case "unblock_process":
      mockState.blocked.delete(args[0]);
      return respond({ ok: true });
    case "get_blocked_list":
      return respond(
        Array.from(mockState.blocked).map((name) => ({
          name,
          display: mockState.alias[name] || name,
        }))
      );
    case "login":
      mockState.loggedIn = true;
      mockState.userEmail = args[1];
      mockState.serverUrl = args[0];
      return respond({ ok: true, message: "登录成功（mock），开始每秒上传数据" });
    case "logout":
      mockState.loggedIn = false;
      return respond({ ok: true, message: "已退出登录，停止上传数据" });
    case "set_auto_startup":
      mockState.autoStartup = Boolean(args[0]);
      return respond({ ok: true, enabled: mockState.autoStartup, message: "mock 模式仅演示状态" });
    case "list_history":
      return respond([
        { file: `data_${todayStr()}.json`, date: todayStr(), apps: 7, total: 9630, totalText: formatSeconds(9630), isToday: true },
        { file: "data_2026-09-11.json", date: "2026-09-11", apps: 12, total: 15220, totalText: formatSeconds(15220), isToday: false },
        { file: "data_2026-09-09.json", date: "2026-09-09", apps: 9, total: 8410, totalText: formatSeconds(8410), isToday: false },
        { file: "data_2026-09-07.json", date: "2026-09-07", apps: 6, total: 5120, totalText: formatSeconds(5120), isToday: false },
      ]);
    case "load_history":
      mockState.mode = "history";
      mockState.historyFile = args[0];
      return respond({ ok: true, message: `已切换到历史数据：${args[0]}`, snapshot: mockSnapshot() });
    case "back_to_today":
      mockState.mode = "live";
      mockState.historyFile = "";
      return respond({ ok: true, snapshot: mockSnapshot() });
    case "open_data_dir":
      return respond({ ok: true });
    case "confirm_exit":
      return respond({ ok: true, exited: true });
    default:
      throw new Error(`mock 未实现接口: ${method}`);
  }
}

function todayStr() {
  return new Date().toISOString().slice(0, 10);
}

/**
 * 初始化桥接：显式 ?mock=1 或长时间拿不到桥接时降级为 mock
 * @returns {Promise<boolean>} true 表示处于 mock 模式
 */
export async function initBridge() {
  const params = new URLSearchParams(location.search);
  if (params.has("mock")) {
    mockMode = true;
    console.info("[api] 使用 mock 数据预览模式");
    return true;
  }

  try {
    const bridge = await waitForBridge();
    await bridge.ping();
    mockMode = false;
    return false;
  } catch (error) {
    // 直接在浏览器打开时没有 pywebview 注入，降级为 mock 方便调试
    console.warn("[api] 未检测到 pywebview，降级为 mock 模式：", error.message);
    mockMode = true;
    return true;
  }
}

/* ============================================================
   业务接口（与 Python WebApi 一一对应）
   ============================================================ */

export const api = {
  ping: () => call("ping"),
  getSnapshot: () => call("get_snapshot"),
  getSettings: () => call("get_settings"),
  setSort: (sortType) => call("set_sort", sortType),
  setAlias: (name, alias) => call("set_alias", name, alias),
  blockProcess: (name) => call("block_process", name),
  unblockProcess: (name) => call("unblock_process", name),
  getBlockedList: () => call("get_blocked_list"),
  login: (serverUrl, email, password) => call("login", serverUrl, email, password),
  logout: () => call("logout"),
  setAutoStartup: (enabled) => call("set_auto_startup", enabled),
  listHistory: () => call("list_history"),
  loadHistory: (file) => call("load_history", file),
  backToToday: () => call("back_to_today"),
  openDataDir: () => call("open_data_dir"),
  confirmExit: (ok) => call("confirm_exit", ok),
};