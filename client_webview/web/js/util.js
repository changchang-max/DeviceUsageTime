/**
 * 通用工具函数：DOM 选取与数字格式化。
 */

/**
 * 选取单个元素
 * @param {string} selector CSS 选择器
 * @param {ParentNode} scope 查找范围，默认 document
 * @returns {HTMLElement|null}
 */
export const $ = (selector, scope = document) => scope.querySelector(selector);

/**
 * 选取全部元素并转成数组
 * @param {string} selector CSS 选择器
 * @param {ParentNode} scope 查找范围，默认 document
 * @returns {HTMLElement[]}
 */
export const $$ = (selector, scope = document) => Array.from(scope.querySelectorAll(selector));

/**
 * 数字千分位格式化
 * @param {number} value 数值
 * @returns {string} 例如 12345 -> "12,345"
 */
export function formatNumber(value) {
  const number = Number(value);
  if (!Number.isFinite(number)) return "0";
  return number.toLocaleString("zh-CN");
}