/**
 * 历史记录视图：列出 history_data 目录下的每日数据，
 * 点击“查看”把该日数据载入概览页（对应旧版的“历史数据看板”窗口）。
 */
import { $ } from "../util.js";
import { api } from "../api.js";
import { showToast } from "../ui.js";

const els = {};
let openFn = null;

/**
 * 初始化历史页事件绑定
 * @param {{onOpen: Function}} options onOpen: 打开某个历史文件的回调
 */
export function initHistoryView({ onOpen }) {
  openFn = onOpen;
  els.tbody = $("#history-tbody");
  els.empty = $("#history-empty");
  els.refreshBtn = $("#btn-refresh-history");

  els.refreshBtn.addEventListener("click", () => renderHistory(true));
}

/**
 * 拉取并渲染历史文件列表
 * @param {boolean} withToast 是否在刷新后弹提示
 */
export async function renderHistory(withToast = false) {
  const list = await api.listHistory();
  els.tbody.replaceChildren();

  if (!list || list.length === 0) {
    els.empty.hidden = false;
    if (withToast) showToast("暂无历史数据", "info");
    return;
  }
  els.empty.hidden = true;

  list.forEach((item) => {
    const row = document.createElement("tr");
    row.dataset.file = item.file;

    const dateCell = document.createElement("td");
    const dateWrap = document.createElement("div");
    dateWrap.className = "history-date";
    const dateText = document.createElement("span");
    dateText.textContent = item.date || item.file;
    dateWrap.appendChild(dateText);
    if (item.isToday) {
      const badge = document.createElement("span");
      badge.className = "history-today";
      badge.textContent = "今天";
      dateWrap.appendChild(badge);
    }
    dateCell.appendChild(dateWrap);

    const appsCell = document.createElement("td");
    appsCell.className = "col-num";
    appsCell.textContent = `${item.apps} 个`;

    const totalCell = document.createElement("td");
    totalCell.className = "col-num";
    const time = document.createElement("span");
    time.className = "time-text";
    time.textContent = item.totalText || "00:00:00";
    totalCell.appendChild(time);

    const actionCell = document.createElement("td");
    actionCell.className = "col-action";
    const button = document.createElement("button");
    button.type = "button";
    button.className = "btn btn-ghost btn-sm";
    button.textContent = "查看";
    button.addEventListener("click", (event) => {
      event.stopPropagation();
      openFn?.(item.file);
    });
    actionCell.appendChild(button);

    row.append(dateCell, appsCell, totalCell, actionCell);
    row.addEventListener("dblclick", () => openFn?.(item.file));
    els.tbody.appendChild(row);
  });

  if (withToast) showToast("历史列表已刷新", "success", 1400);
}