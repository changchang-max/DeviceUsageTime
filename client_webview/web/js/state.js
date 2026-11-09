/**
 * 全局界面状态。
 *
 * 只存放“渲染需要”的轻量状态，真正的数据源在 Python 端，
 * 前端每次轮询拿到的是不可变快照，避免双份数据互相覆盖。
 */
export const state = {
  /** 当前视图：main / history / settings */
  view: "main",
  /** 概览页搜索关键字 */
  filter: "",
  /** 设置页当前分页：general / account / blocked */
  settingsPage: "general",
  /** 最近一次快照 */
  snapshot: null,
  /** 最近一次设置信息 */
  settings: null,
  /** 是否运行在 mock（纯前端预览）模式 */
  mock: false,
  /** 设置页中被选中的屏蔽项 */
  selectedBlocked: null,
  /** 上一次上传状态，用于状态切换时弹轻提示 */
  prevUploadStage: null,
};