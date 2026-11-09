# -*- coding: utf-8 -*-
"""后台监控服务。

把 PyQt5 版本散落在 main.py 里的三个后台线程收敛到一个服务类中：
    - 进程使用时长采集（每秒扫描一次用户进程）
    - 全局键盘/鼠标输入统计（pynput 监听器）
    - 每分钟自动保存历史数据
    - 已登录时每秒向服务器上传增量数据

界面层（前端）不直接接触这些字典，而是通过 WebApi.snapshot() 拿到只读快照，
从而做到“数据在生产线程，展示在主线程”，避免旧版本里表格与字典的竞争问题。
"""
import logging
import math
import threading
import time

import psutil

import mytools
import network
from store import SORT_TYPES

# 过滤系统路径
SYSTEM_PATHS = (
    r"C:\Windows",
    r"C:\Program Files\WindowsApps",
)

# 过滤系统用户
SYSTEM_USERS = (
    "SYSTEM",
    "LOCAL SERVICE",
    "NETWORK SERVICE",
)

# 明显的系统进程名（小写），快速跳过，避免调用昂贵的 exe()/username()
SYSTEM_PROC_NAMES = frozenset({
    "system idle process", "system", "registry", "smss.exe", "csrss.exe",
    "wininit.exe", "services.exe", "lsass.exe", "svchost.exe", "winlogon.exe",
    "dwm.exe", "spoolsv.exe", "fontdrvhost.exe", "memory compression",
    "secure system", "audiodg.exe",
})

# 1 像素 ≈ 0.000140625 米（按 96dpi 近似）
PIXEL_TO_METER = 0.000140625
# 每个最小滚动单位（1 格）等效像素数（近似）
SCROLL_PX_PER_DELTA = 3.0
# 连续上传失败达到该次数后，才把状态改为“上传失败”，避免网络抖动频繁打扰用户
MAX_FAIL_BEFORE_NOTIFY = 5
# 自动保存间隔（秒）
AUTO_SAVE_INTERVAL = 60


def is_user_app(proc: psutil.Process) -> bool:
    """判断一个进程是否属于“用户应用”（用于过滤系统进程）

    参数:
        proc: psutil 进程对象
    返回:
        bool: 属于用户应用返回 True
    """
    try:
        exe = proc.exe()
        username = proc.username()

        if not exe:
            return False

        exe = exe.lower()

        # 排除系统路径
        for path in SYSTEM_PATHS:
            if exe.startswith(path.lower()):
                return False

        # 排除系统用户
        for user in SYSTEM_USERS:
            if user.lower() in username.lower():
                return False

        return True
    except (psutil.AccessDenied, psutil.NoSuchProcess):
        return False


def get_user_app_pids() -> dict:
    """扫描当前运行的用户应用进程

    返回:
        dict: {进程名: {"pid": pid, "title": 进程名, "use_time": 0}}
    """
    result = {}
    user_procs = []
    pid_map = {}

    for proc in psutil.process_iter(['pid', 'name']):
        try:
            pname = proc.name()
        except (psutil.AccessDenied, psutil.NoSuchProcess):
            continue

        if not pname or pname.lower() in SYSTEM_PROC_NAMES:
            continue

        if is_user_app(proc):
            user_procs.append(proc)
            pid_map[proc.pid] = proc

    # 过滤子进程：仅过滤与父进程同名的子进程（如 Electron renderer），
    # 避免误杀被其他应用启动的进程（如 uTools 启动的 DSH Desktop）
    for proc in user_procs:
        try:
            ppid = proc.ppid()
            name = proc.name()
            if ppid not in pid_map:
                result[name] = {"pid": proc.pid, "title": name, "use_time": 0}
            else:
                parent = pid_map[ppid]
                if parent.name() != name:
                    result[name] = {"pid": proc.pid, "title": name, "use_time": 0}
        except (psutil.AccessDenied, psutil.NoSuchProcess):
            continue

    return result


class InputCounter:
    """键盘敲击、鼠标点击与鼠标移动距离统计。

    pynput 回调运行在独立线程，所有读写都加锁，
    跨天时由监控线程调用 reset() 清零。
    """

    def __init__(self):
        self._lock = threading.Lock()
        self.keyboard_count = 0
        self.mouse_click_count = 0
        self.mouse_distance_px = 0.0
        self._last_pos = None

    # ---------- pynput 回调 ----------
    def on_key_press(self, key):
        """键盘按键按下时累计计数（所有按键均计入）"""
        with self._lock:
            self.keyboard_count += 1

    def on_mouse_click(self, x, y, button, pressed):
        """鼠标按键按下时累计计数（只在按下时计入）"""
        if pressed:
            with self._lock:
                self.mouse_click_count += 1

    def on_mouse_move(self, x, y):
        """鼠标移动时累计平面位移（像素）"""
        with self._lock:
            if self._last_pos is not None:
                dx = x - self._last_pos[0]
                dy = y - self._last_pos[1]
                self.mouse_distance_px += math.sqrt(dx * dx + dy * dy)
            self._last_pos = (x, y)

    def on_mouse_scroll(self, x, y, dx, dy):
        """滚轮滚动时累计等效像素距离"""
        with self._lock:
            self.mouse_distance_px += (abs(dx) + abs(dy)) * SCROLL_PX_PER_DELTA

    # ---------- 启停与读写 ----------
    def start_listeners(self):
        """启动全局键盘/鼠标监听器（守护线程，随主线程退出自动销毁）"""
        try:
            from pynput import keyboard as _kb, mouse as _ms

            kb_listener = _kb.Listener(on_press=self.on_key_press)
            ms_listener = _ms.Listener(
                on_click=self.on_mouse_click,
                on_move=self.on_mouse_move,
                on_scroll=self.on_mouse_scroll,
            )
            kb_listener.daemon = True
            ms_listener.daemon = True
            kb_listener.start()
            ms_listener.start()
            logging.info("pynput 输入监听器已启动")
        except Exception as e:
            logging.error(f"pynput 输入监听器启动失败: {e}")

    def snapshot(self) -> dict:
        """返回当前计数快照（鼠标距离已换算为米，保留两位小数）"""
        with self._lock:
            return {
                "keyboardCount": self.keyboard_count,
                "mouseClickCount": self.mouse_click_count,
                "mouseDistance": round(self.mouse_distance_px * PIXEL_TO_METER, 2),
                "mouseDistancePx": self.mouse_distance_px,
            }

    def reset(self):
        """清零全部计数器（跨天时调用）"""
        with self._lock:
            self.keyboard_count = 0
            self.mouse_click_count = 0
            self.mouse_distance_px = 0.0
            self._last_pos = None

    def restore(self, keyboard_count: int, mouse_click_count: int, mouse_distance_meters: float):
        """从历史数据文件恢复计数器（启动时调用）"""
        with self._lock:
            self.keyboard_count = int(keyboard_count or 0)
            self.mouse_click_count = int(mouse_click_count or 0)
            self.mouse_distance_px = float(mouse_distance_meters or 0.0) / PIXEL_TO_METER
            self._last_pos = None


def sort_app_items(items: list, sort_type: str) -> list:
    """按排序方式对界面展示用的应用列表排序

    参数:
        items: [{"name": .., "display": .., "seconds": ..}, ...]
        sort_type: windowName_up / windowName_down / useTime_up / useTime_down
    返回:
        list: 排序后的新列表
    """
    if sort_type not in SORT_TYPES:
        sort_type = "useTime_down"

    if sort_type == "windowName_up":
        return sorted(items, key=lambda item: item["display"].lower())
    if sort_type == "windowName_down":
        return sorted(items, key=lambda item: item["display"].lower(), reverse=True)
    if sort_type == "useTime_up":
        return sorted(items, key=lambda item: item["seconds"])
    return sorted(items, key=lambda item: item["seconds"], reverse=True)


class MonitorService:
    """监控服务：所有后台采集与上传逻辑的宿主。"""

    def __init__(self, config, alias_store, blocked_store, history_store):
        """
        参数:
            config: store.ConfigFile 实例
            alias_store: store.AliasStore 实例
            blocked_store: store.BlockedStore 实例
            history_store: store.HistoryStore 实例
        """
        self.config = config
        self.alias_store = alias_store
        self.blocked_store = blocked_store
        self.history_store = history_store

        self.stop_event = threading.Event()
        self.lock = threading.RLock()

        # 当天数据：{进程名: {"pid":.., "title":.., "use_time":..}}
        self.app_dict = {}
        # 查看历史时的数据（结构同上）
        self.history_dict = {}
        # 历史文件对应的统计信息（键盘/鼠标/总时长）
        self.history_stats = {}

        # 展示模式：live=实时 / history=历史
        self.mode = "live"
        self.history_file = ""
        self.current_date = time.strftime("%Y-%m-%d")

        # 客户端程序今日运行累计时长（秒）
        self.run_duration = 0
        # 当前仍在运行的用户应用名集合（供上传线程判断）
        self.running_apps = set()
        # 前台窗口信息（用于标记 isActive 与界面高亮）
        self.foreground_name = None
        self.foreground_title = None

        # 输入统计
        self.inputs = InputCounter()

        # 上传状态（供界面展示）
        self._upload_lock = threading.Lock()
        self.upload_stage = "not_login"
        self.upload_status_text = "未登录"
        self.upload_last_error = ""
        self.upload_fail_count = 0
        # 上传专用客户端实例（登录 worker 会另建实例，避免交叉使用）
        self.api_client = network.ApiClient()

        self._threads = []
        # 启动时读取当天已有数据，实现重启续算
        self._load_today()

    # ==================== 数据装载 ====================

    def _load_today(self):
        """若当天数据文件存在则读取，恢复使用时长与输入统计"""
        file_name = f"data_{self.current_date}.json"
        if not self.history_store.exists(self.current_date):
            return
        simple_data = self.history_store.load(file_name)
        if not isinstance(simple_data, dict):
            return

        # 还原输入统计计数器（兼容没有 _statistics 键的旧文件）
        stats = simple_data.pop("_statistics", {}) or {}
        self.inputs.restore(
            stats.get("keyboardCount", 0),
            stats.get("mouseClickCount", 0),
            stats.get("mouseDistance", 0.0),
        )
        self.run_duration = int(stats.get("totalDuration", 0) or 0)

        restored = {}
        for title, use_time in simple_data.items():
            if not isinstance(use_time, (int, float)):
                continue
            # 由于保存时只保存了 title 和 use_time，pid 需要重新获取，历史记录里设为 0
            restored[title] = {"pid": 0, "title": title, "use_time": int(use_time)}
        self.app_dict = restored

    # ==================== 上传状态 ====================

    def set_upload_stage(self, stage: str, text: str, error: str = ""):
        """更新上传状态，供界面展示（上传线程调用）

        参数:
            stage: not_login / ok / error / invalid
            text: 展示文案
            error: 详细错误信息
        """
        with self._upload_lock:
            self.upload_stage = stage
            self.upload_status_text = text
            self.upload_last_error = error

    def get_upload_state(self) -> dict:
        """返回上传状态快照"""
        with self._upload_lock:
            return {
                "stage": self.upload_stage,
                "text": self.upload_status_text,
                "error": self.upload_last_error,
                "loggedIn": bool(self.config.get_token()),
                "email": self.config.get_user_email(),
            }

    # ==================== 启停 ====================

    def start(self):
        """启动监控、自动保存、数据上传三个后台线程与输入监听器"""
        self.stop_event.clear()

        for target, name in (
            (self._monitor_loop, "monitor"),
            (self._auto_save_loop, "autosave"),
            (self._upload_loop, "upload"),
        ):
            thread = threading.Thread(target=target, name=name, daemon=True)
            thread.start()
            self._threads.append(thread)

        self.inputs.start_listeners()
        logging.info("监控服务已启动")

    def stop(self, save: bool = True):
        """停止全部后台线程，并在退出前保存一次数据"""
        self.stop_event.set()
        for thread in self._threads:
            if thread.is_alive():
                thread.join(timeout=2)
        self._threads.clear()
        if save:
            self.save_now()
        logging.info("监控服务已停止")

    # ==================== 监控主循环 ====================

    def _monitor_loop(self):
        """每秒扫描一次进程，累加使用时长，跨天时重置数据"""
        while not self.stop_event.is_set():
            try:
                self._tick()
            except Exception as e:
                logging.error(f"监控循环异常: {e}")
            # 每秒捕获一次
            self.stop_event.wait(1)

    def _tick(self):
        """执行一次采集：跨天检测 → 扫描进程 → 累加时长 → 记录前台窗口"""
        new_date = time.strftime("%Y-%m-%d", time.localtime())
        if new_date != self.current_date:
            # 跨天：清空当天数据与输入统计
            with self.lock:
                self.app_dict.clear()
                self.current_date = new_date
                self.run_duration = 0
            self.inputs.reset()

        # 慢速的 psutil 进程枚举放在锁外，避免 I/O 阻塞其它线程
        current_procs = get_user_app_pids()
        foreground_name, foreground_title = mytools.get_foreground_window_info()
        blocked_set = set(self.blocked_store.get_all_blocked())

        with self.lock:
            # 同步“当前正在运行的应用”集合（排除被屏蔽的进程）
            self.running_apps.clear()
            self.running_apps.update({name for name in current_procs if name not in blocked_set})
            self.foreground_name = foreground_name
            self.foreground_title = foreground_title

            # 1. 对所有正在运行的进程 use_time +1
            for title in list(self.app_dict.keys()):
                if title in current_procs:
                    self.app_dict[title]["use_time"] += 1

            # 2. 添加新进程（被屏蔽的也加入字典，确保时间持续累计）
            for title, proc_info in current_procs.items():
                if title not in self.app_dict:
                    self.app_dict[title] = {
                        "pid": proc_info["pid"],
                        "title": title,
                        "use_time": 1,
                    }

            # 客户端程序自身今日运行时长 +1（与是否有应用在运行无关）
            self.run_duration += 1

    # ==================== 保存 ====================

    def _statistics_snapshot(self) -> dict:
        """组装要写入 _statistics 的统计信息"""
        stats = self.inputs.snapshot()
        return {
            "keyboardCount": stats["keyboardCount"],
            "mouseClickCount": stats["mouseClickCount"],
            "mouseDistance": stats["mouseDistance"],
            "totalDuration": self.run_duration,
        }

    def save_now(self):
        """把当天数据保存到 history_data/data_日期.json"""
        with self.lock:
            save_date = self.current_date
            app_dict = {name: dict(info) for name, info in self.app_dict.items()}
            statistics = self._statistics_snapshot()

        # 防御：字典为空但当天文件有实质内容时，跳过本次保存，避免竞态覆盖有效数据
        if len(app_dict) == 0 and self.history_store.size(save_date) > 50:
            return

        try:
            self.history_store.save(save_date, app_dict, statistics)
        except Exception as e:
            logging.error(f"保存历史数据失败: {e}")

    def _auto_save_loop(self):
        """每 60 秒保存一次数据"""
        while not self.stop_event.is_set():
            self.stop_event.wait(AUTO_SAVE_INTERVAL)
            if self.stop_event.is_set():
                break
            self.save_now()

    # ==================== 上传 ====================

    def _upload_loop(self):
        """已登录时每秒向服务器上传一次增量数据"""
        while not self.stop_event.is_set():
            start_at = time.time()
            try:
                self._upload_once()
            except Exception as e:
                logging.error(f"数据上传线程异常: {e}")
            # 按 1 秒节拍运行（上传耗时也从间隔中扣除）
            remain = 1 - (time.time() - start_at)
            if remain > 0:
                self.stop_event.wait(remain)

    def _upload_once(self):
        """执行一次上传；未登录时仅更新状态"""
        token = self.config.get_token()
        user_email = self.config.get_user_email()

        if not token or not user_email:
            self.upload_fail_count = 0
            self.set_upload_stage("not_login", "未登录", "")
            return

        # 每次上传前同步服务器地址与 token（设置页可随时修改）
        self.api_client.base_url = self.config.get_server_url()
        self.api_client.token = token

        try:
            with self.lock:
                snapshot = {name: dict(info) for name, info in self.app_dict.items()}
                running_apps = set(self.running_apps)
                running_seconds = self.run_duration
                foreground_name = self.foreground_name
                foreground_title = self.foreground_title

            # 过滤掉已被屏蔽的进程（不显示也不上传）
            blocked_set = set(self.blocked_store.get_all_blocked())
            snapshot = {k: v for k, v in snapshot.items() if k not in blocked_set}
            running_apps = {a for a in running_apps if a not in blocked_set}

            stats = self.inputs.snapshot()
            alias_map = self.alias_store.snapshot()

            payload = network.build_upload_payload(
                snapshot, running_apps, foreground_name, foreground_title, user_email,
                stats["keyboardCount"], stats["mouseClickCount"], stats["mouseDistance"],
                running_seconds, alias_map)
            self.api_client.upload(payload)
            self.upload_fail_count = 0
            self.set_upload_stage("ok", f"已连接，上次上传 {mytools.hour()}", "")
        except network.ApiException as e:
            if e.code in (401, 403):
                # token 已失效：清空本地 token，等待用户重新登录
                logging.error(f"数据上传失败，token 失效: {e}")
                self.config.set_token("")
                self.upload_fail_count = 0
                self.set_upload_stage("invalid", "登录已失效，请重新登录", str(e))
            else:
                self.upload_fail_count += 1
                if self.upload_fail_count >= MAX_FAIL_BEFORE_NOTIFY:
                    self.set_upload_stage("error", "上传失败", str(e))
        except Exception as e:
            logging.error(f"数据上传异常: {e}")
            self.upload_fail_count += 1
            if self.upload_fail_count >= MAX_FAIL_BEFORE_NOTIFY:
                self.set_upload_stage("error", "上传失败", f"网络异常: {e}")

    # ==================== 历史查看 ====================

    def load_history(self, file_name: str) -> dict:
        """加载指定历史文件并在界面切换到历史模式

        参数:
            file_name: 形如 data_2026-01-12.json
        返回:
            dict: {"ok": bool, "message": str}
        """
        if not file_name.startswith("data_") or not file_name.endswith(".json"):
            return {"ok": False, "message": "非法的历史文件名"}

        simple_data = self.history_store.load(file_name)
        if not isinstance(simple_data, dict) or not simple_data:
            return {"ok": False, "message": "历史文件为空或不存在"}

        # 点击“今天”的文件时等价于返回实时视图
        today_file = f"data_{self.current_date}.json"
        if file_name == today_file:
            self.back_to_today()
            return {"ok": True, "message": "已切换到实时数据"}

        stats = simple_data.pop("_statistics", {}) or {}
        restored = {}
        for title, use_time in simple_data.items():
            if not isinstance(use_time, (int, float)):
                continue
            restored[title] = {"pid": 0, "title": title, "use_time": int(use_time)}

        with self.lock:
            self.history_dict.clear()
            self.history_dict.update(restored)
            self.history_stats = stats
            self.mode = "history"
            self.history_file = file_name
        return {"ok": True, "message": f"已切换到历史数据：{file_name}"}

    def back_to_today(self):
        """从历史模式切回实时模式"""
        with self.lock:
            self.mode = "live"
            self.history_file = ""
            self.history_dict.clear()
            self.history_stats = {}

    # ==================== 界面快照 ====================

    def snapshot(self) -> dict:
        """返回前端渲染所需的完整数据快照（每 1 秒被调用一次）

        返回:
            dict: 包含当前模式、应用列表、统计信息、上传状态等
        """
        with self.lock:
            mode = self.mode
            history_file = self.history_file
            source = self.app_dict if mode == "live" else self.history_dict
            foreground_name = self.foreground_name
            run_duration = self.run_duration
            # 在锁内把条目复制成列表，避免锁外迭代时字典被监控线程改写
            raw_items = list(source.items())
            blocked_set = set(self.blocked_store.get_all_blocked())
            # 历史模式下使用文件中的统计信息
            history_stats = dict(self.history_stats)

        items = []
        for name, info in raw_items:
            if name in blocked_set:
                continue
            display = self.alias_store.get_alias(name)
            seconds = int(info.get("use_time", 0))
            items.append({
                "name": name,
                "display": display,
                "alias": self.alias_store.get_raw_alias(name),
                "seconds": seconds,
                "text": mytools.get_strtime(seconds),
                "running": (name in self.running_apps) if mode == "live" else False,
                "active": (name == foreground_name) if mode == "live" else False,
            })

        sort_type = self.config.get_sort_type()
        items = sort_app_items(items, sort_type)

        # 统计卡片：实时模式用当前计数器，历史模式用文件内的 _statistics
        if mode == "live":
            stats = self.inputs.snapshot()
            stats["totalDuration"] = run_duration
            stats["mouseDistance"] = stats["mouseDistance"]
        else:
            stats = {
                "keyboardCount": int(history_stats.get("keyboardCount", 0) or 0),
                "mouseClickCount": int(history_stats.get("mouseClickCount", 0) or 0),
                "mouseDistance": float(history_stats.get("mouseDistance", 0.0) or 0.0),
                "totalDuration": int(history_stats.get("totalDuration", 0) or 0),
            }

        return {
            "mode": mode,
            "date": self.current_date,
            "historyFile": history_file,
            "viewDate": self._file_to_date(history_file) if mode == "history" else self.current_date,
            "apps": items,
            "appCount": len(items),
            "blockedCount": len(blocked_set),
            "sortType": sort_type,
            "stats": {
                "keyboardCount": int(stats.get("keyboardCount", 0)),
                "mouseClickCount": int(stats.get("mouseClickCount", 0)),
                "mouseDistance": round(float(stats.get("mouseDistance", 0.0)), 2),
                "totalDuration": int(stats.get("totalDuration", 0)),
                "totalDurationText": mytools.get_strtime(stats.get("totalDuration", 0)),
            },
            "upload": self.get_upload_state(),
            "now": mytools.hour(),
        }

    @staticmethod
    def _file_to_date(file_name: str) -> str:
        """把 data_2026-01-12.json 转成 2026-01-12，失败时返回空串"""
        if not file_name:
            return ""
        name = file_name
        if name.startswith("data_"):
            name = name[5:]
        if name.endswith(".json"):
            name = name[:-5]
        return name

    def history_overview(self) -> list:
        """汇总历史文件，供历史面板展示

        返回:
            list: [{"file":.., "date":.., "apps":.., "total":.., "totalText":.., "isToday":..}]
        """
        result = []
        today_file = f"data_{self.current_date}.json"
        for file_name in self.history_store.list_files():
            data = self.history_store.load(file_name)
            if not isinstance(data, dict):
                data = {}
            stats = data.get("_statistics", {}) or {}
            app_count = len([k for k in data.keys() if k != "_statistics"])
            total = int(stats.get("totalDuration", 0) or 0)
            # 没有记录总时长的旧文件，退化为使用时长之和
            if total == 0:
                total = int(sum(v for k, v in data.items() if k != "_statistics" and isinstance(v, (int, float))))
            result.append({
                "file": file_name,
                "date": self._file_to_date(file_name),
                "apps": app_count,
                "total": total,
                "totalText": mytools.get_strtime(total),
                "isToday": file_name == today_file,
            })
        return result