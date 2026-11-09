/**
 * 界面通用组件：轻提示（toast）与右键菜单。
 */
import { $ } from "./util.js";

let toastContainer = null;
let contextMenu = null;

/**
 * 显示一条轻提示
 * @param {string} message 提示文案
 * @param {"info"|"success"|"error"|"warn"} type 类型
 * @param {number} duration 显示时长（毫秒）
 */
export function showToast(message, type = "info", duration = 2400) {
  if (!toastContainer) toastContainer = $("#toast-container");
  if (!toastContainer) return;

  const toast = document.createElement("div");
  toast.className = "toast";
  toast.dataset.type = type;
  toast.textContent = message;
  toastContainer.appendChild(toast);

  setTimeout(() => {
    toast.classList.add("is-leaving");
    setTimeout(() => toast.remove(), 200);
  }, duration);
}

/**
 * 在指定坐标弹出右键菜单
 * @param {number} x 视口横坐标
 * @param {number} y 视口纵坐标
 * @param {Array<{label: string, danger?: boolean, onClick: Function}>} items 菜单项
 */
export function showContextMenu(x, y, items) {
  if (!contextMenu) contextMenu = $("#context-menu");
  if (!contextMenu) return;

  contextMenu.innerHTML = "";
  items.forEach((item) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = `ctx-item${item.danger ? " ctx-danger" : ""}`;
    button.textContent = item.label;
    button.addEventListener("click", (event) => {
      event.stopPropagation();
      hideContextMenu();
      item.onClick?.();
    });
    contextMenu.appendChild(button);
  });

  contextMenu.hidden = false;

  // 先显示再测量宽高，保证菜单不会超出窗口边界
  const rect = contextMenu.getBoundingClientRect();
  const margin = 8;
  const left = Math.min(x, window.innerWidth - rect.width - margin);
  const top = Math.min(y, window.innerHeight - rect.height - margin);
  contextMenu.style.left = `${Math.max(margin, left)}px`;
  contextMenu.style.top = `${Math.max(margin, top)}px`;
}

/** 隐藏右键菜单 */
export function hideContextMenu() {
  if (!contextMenu) contextMenu = $("#context-menu");
  if (contextMenu) contextMenu.hidden = true;
}

// 点击其它区域 / 滚动时收起菜单
document.addEventListener("click", hideContextMenu);
document.addEventListener("contextmenu", (event) => {
  // 表格行会自行阻止默认行为，其余区域统一收起菜单
  if (!event.target.closest(".app-table tbody tr")) hideContextMenu();
});
window.addEventListener("blur", hideContextMenu);
window.addEventListener("resize", hideContextMenu);