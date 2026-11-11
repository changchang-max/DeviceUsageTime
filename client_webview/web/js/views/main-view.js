/**
 * 概览页视图：统计卡片 + 应用使用时长表格。
 *
 * 渲染策略：以进程名为 key 复用 <tr> 节点，只更新变化的文本，
 * 从而避免每秒整表重建带来的闪烁与卡顿。
 */
import { $, formatNumber } from "../util.js";
import { api } from "../api.js";
import { showToast, showContextMenu } from "../ui.js";
import { state } from "../state.js";

const els = {};
let refreshFn = null;

// 排序方式与展示文案的映射（与 Python 端 SORT_TYPES 保持一致）
const SORT_LABELS = {
  useTime_down: "时长 从高到低",
  useTime_up: "时长 从低到高",
  windowName_up: "名称 从低到高",
  windowName_down: "名称 从高到低",
};

// 当前正在行内编辑备注的行（同一时间只允许一个）。
// 每秒轮询都会重排表格，编辑期间该行保持原位，避免被移出文档导致输入框失焦消失。
let activeRename = null;

/**
 * 初始化概览页事件绑定
 * @param {{refresh: Function}} options refresh: 立即重新拉取快照
 */
export function initMainView({ refresh }) {
  refreshFn = refresh;

  els.tbody = $("#app-tbody");
  els.empty = $("#app-empty");
  els.appCount = $("#app-count");
  els.search = $("#search-input");
  els.dropdown = $("#sort-dropdown");
  els.sortTrigger = $("#sort-trigger");
  els.sortValue = $("#sort-value");
  els.sortMenu = $("#sort-menu");
  els.statKeyboard = $("#stat-keyboard");
  els.statMouse = $("#stat-mouse");
  els.statDistance = $("#stat-distance");
  els.statDuration = $("#stat-duration");

  // 搜索：只过滤当前快照，不重新请求后端
  els.search.addEventListener("input", () => {
    state.filter = els.search.value.trim().toLowerCase();
    if (state.snapshot) renderApps(state.snapshot.apps || []);
  });

  // 排序下拉：展开/收起
  els.sortTrigger.addEventListener("click", (event) => {
    event.stopPropagation();
    toggleSortMenu();
  });

  // 选择排序方式后写回配置并立即刷新
  els.sortMenu.addEventListener("click", async (event) => {
    const item = event.target.closest(".dropdown-item");
    if (!item) return;
    event.stopPropagation();
    closeSortMenu();

    const sortType = item.dataset.value;
    if (sortType === state.snapshot?.sortType) return;

    const result = await api.setSort(sortType);
    if (!result?.ok) {
      showToast(result?.message || "排序方式切换失败", "error");
      return;
    }
    updateSortLabel(result.sortType);
    refreshFn?.();
  });

  // 点击其它区域 / Esc 收起排序菜单
  document.addEventListener("click", (event) => {
    if (!els.dropdown.contains(event.target)) closeSortMenu();
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") closeSortMenu();
  });

  // 行内右键菜单：备注名 / 屏蔽进程
  els.tbody.addEventListener("contextmenu", (event) => {
    const row = event.target.closest("tr[data-name]");
    if (!row) return;
    event.preventDefault();
    const app = findApp(row.dataset.name);
    if (!app) return;

    showContextMenu(event.clientX, event.clientY, [
      { label: "✏️ 修改备注名", onClick: () => startRename(row, app) },
      { label: "⛔ 屏蔽此进程", danger: true, onClick: () => blockApp(app) },
    ]);
  });
}

/** 展开/收起排序菜单 */
function toggleSortMenu() {
  if (els.dropdown.classList.contains("is-open")) {
    closeSortMenu();
  } else {
    els.dropdown.classList.add("is-open");
    els.sortMenu.hidden = false;
    els.sortTrigger.setAttribute("aria-expanded", "true");
  }
}

/** 收起排序菜单 */
function closeSortMenu() {
  els.dropdown.classList.remove("is-open");
  els.sortMenu.hidden = true;
  els.sortTrigger.setAttribute("aria-expanded", "false");
}

/**
 * 更新排序按钮文案与选项选中态
 * @param {string} sortType 排序方式
 */
function updateSortLabel(sortType) {
  const label = SORT_LABELS[sortType] || SORT_LABELS.useTime_down;
  els.sortValue.textContent = label;
  els.sortMenu.querySelectorAll(".dropdown-item").forEach((item) => {
    item.classList.toggle("is-selected", item.dataset.value === sortType);
    item.setAttribute("aria-selected", item.dataset.value === sortType ? "true" : "false");
  });
}

/**
 * 用快照渲染整个概览页
 * @param {object} snapshot Python 端返回的快照
 */
export function renderMain(snapshot) {
  if (!snapshot) return;
  state.snapshot = snapshot;

  renderStats(snapshot.stats || {});
  if (snapshot.sortType) updateSortLabel(snapshot.sortType);
  renderApps(snapshot.apps || []);
}

/**
 * 渲染四张统计卡片
 * @param {object} stats 统计信息
 */
function renderStats(stats) {
  els.statKeyboard.textContent = formatNumber(stats.keyboardCount);
  els.statMouse.textContent = formatNumber(stats.mouseClickCount);
  els.statDistance.textContent = Number(stats.mouseDistance || 0).toFixed(2);
  els.statDuration.textContent = stats.totalDurationText || "00:00:00";
}

/**
 * 渲染应用表格（节点复用，避免闪烁）
 * @param {Array<object>} apps 应用列表
 */
function renderApps(apps) {
  const keyword = state.filter;
  const existing = new Map();
  Array.from(els.tbody.children).forEach((row) => existing.set(row.dataset.name, row));

  const ordered = [];
  let visible = 0;

  apps.forEach((app) => {
    const matched = !keyword || app.display.toLowerCase().includes(keyword) || app.name.toLowerCase().includes(keyword);
    if (!matched) return;

    visible += 1;
    let row = existing.get(app.name);
    if (row) {
      existing.delete(app.name);
    } else {
      row = createRow(app.name);
    }
    updateRow(row, app, visible);
    ordered.push(row);
  });

  // 只移动位置不对的行；整表重新 append 会把节点移出文档，正在编辑的输入框会因此失焦
  syncRows(ordered);

  // 剩下的都是本轮不显示的旧行，直接移除
  existing.forEach((row) => {
    // 该行正在编辑备注：先结束编辑，避免残留悬空的输入框与事件监听
    if (activeRename && activeRename.row === row) activeRename.finish(false);
    row.remove();
  });

  els.empty.hidden = visible > 0;
  els.appCount.textContent = keyword
    ? `筛选 ${visible} / ${apps.length} 个应用`
    : `${apps.length} 个应用`;
}

/**
 * 按目标顺序同步行节点，仅在位置不对时移动。
 * 正在编辑备注的行不参与重排，保持原位以防被移出文档而失焦。
 * @param {Array<HTMLTableRowElement>} ordered 目标顺序的行节点
 */
function syncRows(ordered) {
  const lockedRow = activeRename?.row || null;
  let cursor = els.tbody.firstElementChild;

  ordered.forEach((row) => {
    if (row === lockedRow) {
      // 锁定行进位后继续，后续行插到它后面
      if (cursor === row) cursor = row.nextElementSibling;
      return;
    }
    if (row === cursor) {
      cursor = cursor.nextElementSibling;
      return;
    }
    els.tbody.insertBefore(row, cursor);
  });
}

/**
 * 创建一行（只创建骨架与事件，文本由 updateRow 填充）
 * @param {string} name 真实进程名
 * @returns {HTMLTableRowElement}
 */
function createRow(name) {
  const row = document.createElement("tr");
  row.dataset.name = name;
  row.innerHTML = `
    <td class="col-index"></td>
    <td class="col-name">
      <div class="cell-name">
        <span class="app-name"></span>
        <span class="name-badge badge-alias" hidden>备注</span>
        <span class="name-badge badge-live" hidden>前台</span>
      </div>
    </td>
    <td class="col-time"><span class="time-text"></span></td>
    <td class="col-action"><button type="button" class="row-btn" title="屏蔽该进程">屏蔽</button></td>
  `;

  const blockButton = row.querySelector(".row-btn");
  blockButton.addEventListener("click", (event) => {
    event.stopPropagation();
    const app = findApp(name);
    if (app) blockApp(app);
  });

  // 双击标题进入备注编辑（与旧版一致）
  row.querySelector(".col-name").addEventListener("dblclick", () => {
    const app = findApp(name);
    if (app) startRename(row, app);
  });

  return row;
}

/**
 * 更新一行的展示内容
 * @param {HTMLTableRowElement} row 行节点
 * @param {object} app 应用数据
 * @param {number} index 序号
 */
function updateRow(row, app, index) {
  row.children[0].textContent = String(index);

  const nameEl = row.querySelector(".app-name");
  nameEl.textContent = app.display;
  nameEl.title = `${app.name}（双击可改备注名）`;
  nameEl.classList.toggle("is-alias", Boolean(app.alias));
  row.querySelector(".time-text").textContent = app.text;

  const aliasBadge = row.querySelector(".badge-alias");
  const liveBadge = row.querySelector(".badge-live");
  aliasBadge.hidden = !app.alias;
  liveBadge.hidden = !app.active;
  row.classList.toggle("is-active", Boolean(app.active));
}

/**
 * 从最近快照中按进程名查找应用
 * @param {string} name 真实进程名
 * @returns {object|undefined}
 */
function findApp(name) {
  return (state.snapshot?.apps || []).find((app) => app.name === name);
}

/**
 * 双击标题行内编辑备注名
 * @param {HTMLTableRowElement} row 行节点
 * @param {object} app 应用数据
 */
function startRename(row, app) {
  const cell = row.querySelector(".cell-name");
  if (!cell || cell.querySelector(".rename-input")) return;

  const input = document.createElement("input");
  input.type = "text";
  input.className = "rename-input";
  input.value = app.alias || "";
  input.placeholder = app.name;
  input.maxLength = 40;

  const hideList = [row.querySelector(".app-name"), row.querySelector(".badge-alias"), row.querySelector(".badge-live")];
  hideList.forEach((node) => {
    if (node) node.style.visibility = "hidden";
  });
  cell.appendChild(input);

  let finished = false;
  // 编辑期间用户是否真的按下过鼠标/触摸屏。
  // 表格每秒重排会让输入框被移出再插回文档并触发 blur，这类失焦不应关闭输入框；
  // 只有“用户主动点了别处”才提交。每次点击后立刻复位，避免影响下一次判断。
  let pointerDown = false;
  let blurTimer = 0;

  const onPointerDown = () => {
    pointerDown = true;
    setTimeout(() => {
      pointerDown = false;
    }, 0);
  };

  /**
   * 结束编辑
   * @param {boolean} commit 是否提交输入内容
   */
  const finish = async (commit) => {
    if (finished) return;
    finished = true;

    document.removeEventListener("mousedown", onPointerDown, true);
    document.removeEventListener("touchstart", onPointerDown, true);
    clearTimeout(blurTimer);
    if (activeRename && activeRename.row === row) activeRename = null;

    const value = input.value.trim();
    input.remove();
    hideList.forEach((node) => {
      if (node) node.style.visibility = "";
    });

    if (!commit) return;
    if (value === (app.alias || "")) return;

    const result = await api.setAlias(app.name, value);
    if (result?.ok) {
      showToast(value ? `已设置备注名：${value}` : "已清除备注名", "success", 1600);
      refreshFn?.();
    } else {
      showToast(result?.message || "备注保存失败", "error");
    }
  };

  activeRename = { row, finish };

  document.addEventListener("mousedown", onPointerDown, true);
  document.addEventListener("touchstart", onPointerDown, true);

  // 延迟一帧再聚焦，等 dblclick 的默认行为（选词）执行完
  setTimeout(() => {
    if (finished) return;
    input.focus();
    input.select();
  }, 0);

  input.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      finish(true);
    } else if (event.key === "Escape") {
      event.preventDefault();
      finish(false);
    } else if (event.key === "Tab") {
      // 用 Tab 离开时按“点了别处”处理，提交后保留浏览器的焦点跳转
      finish(true);
    }
  });

  input.addEventListener("blur", () => {
    if (finished) return;
    // 同步记录这次失焦是否由用户点击别处引起：文档级 mousedown 先于默认的焦点转移执行
    const clickedElsewhere = pointerDown;
    // 延后一帧判断：若是 DOM 重排把输入框移出再插回导致的失焦，
    // 此时输入框仍在文档中且用户没有点别处，重新聚焦即可，绝不能关闭编辑框。
    clearTimeout(blurTimer);
    blurTimer = setTimeout(() => {
      if (finished) return;
      if (input.isConnected && !clickedElsewhere) {
        input.focus();
        return;
      }
      finish(true);
    }, 0);
  });
}

/**
 * 屏蔽进程
 * @param {object} app 应用数据
 */
async function blockApp(app) {
  const result = await api.blockProcess(app.name);
  if (result?.ok) {
    showToast(`已屏蔽：${result.display}（可在设置中解除）`, "warn", 2400);
    refreshFn?.();
  } else {
    showToast(result?.message || "屏蔽失败", "error");
  }
}