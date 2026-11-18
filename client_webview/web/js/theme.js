/**
 * 界面主题：切换、记忆与菜单渲染。
 *
 * 主题只改设计令牌（见 css/theme.css 的 [data-theme=...]），
 * 组件样式与业务逻辑完全不受影响；下面这份清单是唯一的数据源，
 * 顶栏的快速切换菜单和设置页的主题网格都由它生成。
 */

/** 主题清单（顺序即菜单顺序） */
export const THEMES = [
  { id: "paper", name: "素灰", note: "中性灰 × 墨绿", swatch: ["#eceef0", "#2f7a5f"] },
  { id: "mint", name: "青瓷", note: "浅青 × 深青", swatch: ["#eaf0ed", "#1f6f60"] },
  { id: "grid", name: "工程纸", note: "冷白 × 技术蓝", swatch: ["#f1f4f7", "#1f5f8b"] },
  { id: "slate", name: "岩墨", note: "中性灰 × 墨绿", swatch: ["#e6e8e7", "#2b6f59"] },
  { id: "night", name: "夜阑", note: "深墨 × 薄荷", swatch: ["#14181a", "#4ec49a"] },
  { id: "indigo", name: "靛蓝", note: "藏青 × 琥珀", swatch: ["#12161f", "#f0b354"] },
  { id: "aurora", name: "极光", note: "石墨 × 青蓝", swatch: ["#12161c", "#58b6f0"] },
  { id: "grape", name: "紫藤", note: "墨紫 × 藤灰", swatch: ["#17141c", "#b18cf2"] },
];

const STORAGE_KEY = "dut.theme";
const DEFAULT_THEME = "paper";

/** 深色主题清单：用于同步原生控件配色（color-scheme） */
const DARK_THEMES = ["night", "indigo", "aurora", "grape"];

let current = DEFAULT_THEME;
const listeners = [];

/** 读取已保存的主题；非法值回落到默认主题 */
function readSaved() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved && THEMES.some((theme) => theme.id === saved)) return saved;
  } catch (error) {
    // 隐私模式/存储被禁用时忽略，直接用默认主题
  }
  return DEFAULT_THEME;
}

/**
 * 把主题应用到 <html>
 * @param {string} id 主题标识
 */
/**
 * 顶栏触发器里的“明暗小图标”：深色主题画月牙，浅色主题画半日。
 * 用 SVG path 而不是 emoji，字形在各系统一致。
 * @param {boolean} dark 是否深色主题
 */
function triggerGlyph(dark) {
  if (dark) {
    return '<path d="M13.2 3.6a7 7 0 1 0 3.2 9.6 5.4 5.4 0 0 1-3.2-9.6z"/>';
  }
  return '<circle cx="10" cy="10" r="3.2"/><path d="M10 1.6v2M10 16.4v2M1.6 10h2M16.4 10h2M4.1 4.1l1.4 1.4M14.5 14.5l1.4 1.4M15.9 4.1l-1.4 1.4M5.5 14.5l-1.4 1.4"/>';
}

function apply(id) {
  if (!THEMES.some((theme) => theme.id === id)) id = DEFAULT_THEME;
  current = id;
  document.documentElement.dataset.theme = id;

  const meta = THEMES.find((theme) => theme.id === id);
  const dark = DARK_THEMES.includes(id);
  // color-scheme 决定原生滚动条、输入框、自动填充的明暗，必须跟着主题走
  document.documentElement.style.colorScheme = dark ? "dark" : "light";

  document.querySelectorAll("[data-theme-label]").forEach((node) => {
    node.textContent = meta.name;
  });
  // 只切状态类名，颜色一律交给 CSS（见 css/theme.css 与 app.css 的 .theme-* 规则）
  document.querySelectorAll(".theme-menu-item, .theme-tile").forEach((node) => {
    const active = node.dataset.theme === id;
    node.classList.toggle("is-active", active);
    if (node.hasAttribute("aria-checked")) {
      node.setAttribute("aria-checked", active ? "true" : "false");
    }
  });

  const switcher = document.getElementById("theme-switch");
  if (switcher) {
    switcher.dataset.current = id;
    switcher.dataset.scheme = dark ? "dark" : "light";
  }
  const glyph = document.getElementById("theme-glyph");
  if (glyph) glyph.innerHTML = triggerGlyph(dark);
  const dot = document.getElementById("theme-dot");
  if (dot) {
    dot.style.setProperty("--swatch-bg", meta.swatch[0]);
    dot.style.setProperty("--swatch-accent", meta.swatch[1]);
  }

  listeners.forEach((fn) => fn(id));
}

/**
 * 切换主题并记忆
 * @param {string} id 主题标识
 */
export function setTheme(id) {
  apply(id);
  try {
    localStorage.setItem(STORAGE_KEY, current);
  } catch (error) {
    // 存不下也不影响本次切换
  }
}

/** 当前主题标识 */
export function getTheme() {
  return current;
}

/**
 * 订阅主题变化
 * @param {(id: string) => void} fn 回调
 */
export function onThemeChange(fn) {
  listeners.push(fn);
}

/** 生成一个色块预览（两个主题色 + 底色） */
function swatchMarkup(theme) {
  return `<span class="theme-swatch" style="--swatch-bg:${theme.swatch[0]};--swatch-accent:${theme.swatch[1]}"></span>`;
}

/**
 * 渲染顶栏快速切换菜单
 * @param {HTMLElement} menu 菜单容器
 */
function renderMenu(menu) {
  menu.innerHTML = THEMES.map(
    (theme) => `
      <button type="button" class="theme-menu-item" data-theme="${theme.id}" data-picker-neutral="1" role="option" aria-selected="false">
        ${swatchMarkup(theme)}
        <span class="theme-menu-name">${theme.name}</span>
        <span class="theme-menu-note">${theme.note}</span>
        <span class="theme-check" aria-hidden="true">✓</span>
      </button>`
  ).join("");

  menu.addEventListener("click", (event) => {
    const item = event.target.closest(".theme-menu-item");
    if (!item) return;
    event.stopPropagation();
    setTheme(item.dataset.theme);
    closeMenu(menu);
  });
}

/**
 * 渲染设置页的主题网格
 * @param {HTMLElement} grid 网格容器
 */
function renderGrid(grid) {
  grid.innerHTML = THEMES.map(
    (theme) => `
      <button type="button" class="theme-tile" data-theme="${theme.id}" data-picker-neutral="1" role="radio" aria-checked="false">
        ${swatchMarkup(theme)}
        <span class="theme-tile-text">
          <span class="theme-tile-name">${theme.name}</span>
          <span class="theme-tile-note">${theme.note}</span>
        </span>
        <span class="theme-check" aria-hidden="true">✓</span>
      </button>`
  ).join("");

  grid.addEventListener("click", (event) => {
    const tile = event.target.closest(".theme-tile");
    if (!tile) return;
    setTheme(tile.dataset.theme);
  });
}

/** 收起顶栏菜单 */
function closeMenu(menu) {
  const root = document.getElementById("theme-switch");
  root?.classList.remove("is-open");
  menu.hidden = true;
  document.getElementById("theme-trigger")?.setAttribute("aria-expanded", "false");
}

/** 展开顶栏菜单 */
function openMenu(menu) {
  const root = document.getElementById("theme-switch");
  const trigger = document.getElementById("theme-trigger");
  root?.classList.add("is-open");
  menu.hidden = false;
  trigger?.setAttribute("aria-expanded", "true");

  // 顶栏在 overflow:hidden 的 .app 里，菜单改成 fixed 定位后需要按触发按钮实际位置摆位
  if (trigger) {
    const rect = trigger.getBoundingClientRect();
    const margin = 8;
    // 右对齐到触发按钮：菜单恒为右对齐，left 用 auto，避免测量宽度时把菜单挤回左侧
    menu.style.right = `${Math.max(margin, window.innerWidth - rect.right)}px`;
    menu.style.left = "auto";
    menu.style.top = `${rect.bottom + 6}px`;
  }
}

/** 初始化：套用主题、生成菜单与设置页网格、绑定交互 */
export function initTheme() {
  const menu = document.getElementById("theme-menu");
  const grid = document.getElementById("theme-grid");
  const trigger = document.getElementById("theme-trigger");

  if (menu) renderMenu(menu);
  if (grid) renderGrid(grid);

  // 菜单挂在 body 下并 fixed 定位：顶栏嵌在 overflow:hidden 的 .app 里，
  // 挂在顶栏内部会被裁掉，但视觉上仍然从触发按钮下方展开
  if (menu && menu.parentElement !== document.body) document.body.appendChild(menu);

  trigger?.addEventListener("click", (event) => {
    event.stopPropagation();
    if (menu.hidden) openMenu(menu);
    else closeMenu(menu);
  });

  // 点别处 / Esc / 窗口尺寸变化都收起菜单，避免菜单漂在错误位置上
  document.addEventListener("click", (event) => {
    if (menu && !menu.hidden && !event.target.closest("#theme-switch") && !event.target.closest("#theme-menu")) {
      closeMenu(menu);
    }
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && menu && !menu.hidden) closeMenu(menu);
  });
  window.addEventListener("resize", () => {
    if (menu && !menu.hidden) closeMenu(menu);
  });

  apply(readSaved());
}
