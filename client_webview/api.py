# -*- coding: utf-8 -*-
"""前端与 Python 之间的桥接层。

pywebview 会把 WebApi 的公开方法注入到页面的 window.pywebview.api 上，
前端用 `pywebview.api.get_snapshot()` 这样的形式调用，返回 Promise。

约定：
    - 所有返回值必须是可 JSON 序列化的 dict/list/str/int/bool；
    - 失败不抛异常给前端，统一返回 {"ok": False, "message": "..."}，
      便于界面直接展示错误文案。
"""
import logging
import os
import threading
import time

import mytools
import network
from store import (
    APP_DIR,
    SORT_TYPES,
    clear_encrypted_password,
    load_encrypted_password,
    save_encrypted_password,
)

# 程序标识（开机自启动注册表项名称 & 窗口标题）
APP_NAME = "DeviceUsageTime"
APP_TITLE = "屏幕视奸器"
APP_VERSION = "2.0.0-webview"


class WebApi:
    """暴露给前端的全部操作接口。"""

    def __init__(self, monitor, config, alias_store, blocked_store, history_store):
        """
        参数:
            monitor: monitor.MonitorService 实例
            config: store.ConfigFile 实例
            alias_store: store.AliasStore 实例
            blocked_store: store.BlockedStore 实例
            history_store: store.HistoryStore 实例
        """
        self.monitor = monitor
        self.config = config
        self.alias_store = alias_store
        self.blocked_store = blocked_store
        self.history_store = history_store

        self._window = None
        # 是否已经确认退出（确认后允许窗口真正关闭）
        self._exit_confirmed = False
        # 登录/退出进行中标记，避免重复点击造成多个并发请求
        self._login_in_progress = False
        self._logout_in_progress = False
        self._login_lock = threading.Lock()

    def __dir__(self):
        """只向 pywebview 暴露公开方法。

        pywebview 会用 dir(js_api) 递归自省所有属性来生成 JS 侧的函数表，
        一旦把窗口对象、线程锁等句柄暴露出去，自省会递归进 .NET 对象直至崩溃，
        因此这里只返回本类的公开方法名。
        """
        names = set()
        for cls in type(self).__mro__:
            for name, value in vars(cls).items():
                if not name.startswith("_") and callable(value):
                    names.add(name)
        return sorted(names)

    # ==================== 生命周期 ====================

    def attach_window(self, window):
        """保存窗口引用（用于主动调用前端 JS 与销毁窗口）

        参数:
            window: pywebview 窗口对象
        """
        self._window = window

    def start_background(self):
        """由 webview.start(func=...) 在 GUI 线程启动后调用：拉起监控并尝试自动登录"""
        self.monitor.start()
        threading.Thread(target=self._auto_login, name="auto-login", daemon=True).start()

    def _auto_login(self):
        """读取本地加密密码，若存在则尝试自动登录（失败静默，不影响使用）"""
        try:
            # 稍等界面加载完成，避免启动瞬间抢占资源
            time.sleep(0.8)
            email, password = load_encrypted_password()
            if not email or not password:
                return
            logging.info("检测到本地加密密码，尝试自动登录…")
            server_url = self.config.get_server_url()
            client = network.ApiClient(base_url=server_url)
            token = client.login(email, password)
            self.config.set_server_url(server_url)
            self.config.set_user_email(email)
            self.config.set_token(token)
            self.monitor.upload_fail_count = 0
            self.monitor.set_upload_stage("ok", f"已连接，上次上传 {mytools.hour()}", "")
            logging.info("自动登录成功")
        except Exception as e:
            # 自动登录失败不打扰用户，可在设置页手动登录
            logging.info(f"自动登录失败: {e}")

    def on_window_closing(self) -> bool:
        """窗口关闭回调：拦截系统关闭，改为让前端弹出确认框

        返回:
            bool: False 表示取消本次关闭
        """
        if self._exit_confirmed:
            return True
        threading.Thread(target=self._request_exit_confirm, name="exit-confirm", daemon=True).start()
        return False

    def _request_exit_confirm(self):
        """在独立线程里通知前端弹出退出确认框（避免 GUI 线程死锁）"""
        time.sleep(0.05)
        try:
            if self._window:
                self._window.evaluate_js("window.APP && window.APP.showExitConfirm();")
        except Exception as e:
            logging.error(f"弹出退出确认框失败: {e}")

    def confirm_exit(self, ok: bool) -> dict:
        """前端退出确认框的回调

        参数:
            ok: True 表示确认退出
        返回:
            dict: {"ok": True}
        """
        if not ok:
            return {"ok": True, "exited": False}

        self._exit_confirmed = True
        # 先停线程并保存数据，再销毁窗口
        try:
            self.monitor.stop(save=True)
        except Exception as e:
            logging.error(f"退出前停止监控失败: {e}")

        threading.Thread(target=self._destroy_window, name="destroy", daemon=True).start()
        return {"ok": True, "exited": True}

    def _destroy_window(self):
        """销毁窗口（调用后 webview 事件循环会退出）"""
        time.sleep(0.15)
        try:
            if self._window:
                self._window.destroy()
        except Exception as e:
            logging.error(f"销毁窗口失败: {e}")

    # ==================== 数据快照 ====================

    def get_snapshot(self) -> dict:
        """返回界面渲染所需的完整快照（前端每秒轮询一次）"""
        try:
            return self.monitor.snapshot()
        except Exception as e:
            logging.error(f"获取快照失败: {e}")
            return {"apps": [], "stats": {}, "upload": {}, "mode": "live", "error": str(e)}

    def get_settings(self) -> dict:
        """返回设置页所需的配置信息"""
        email, _ = load_encrypted_password()
        return {
            "appName": APP_TITLE,
            "appVersion": APP_VERSION,
            "serverUrl": self.config.get_server_url(),
            "userEmail": self.config.get_user_email(),
            "loggedIn": bool(self.config.get_token()),
            "autoStartup": mytools.is_startup_enabled(APP_NAME),
            "sortType": self.config.get_sort_type(),
            "savedPasswordEmail": email or "",
            "hasSavedPassword": bool(email),
            "dataDir": str(APP_DIR),
            "defaultServerUrl": network.DEFAULT_BASE_URL,
            "blockedCount": self.blocked_store.count(),
        }

    def ping(self) -> dict:
        """前端启动握手，确认桥接通道已就绪"""
        return {"ok": True, "version": APP_VERSION, "title": APP_TITLE}

    # ==================== 主界面操作 ====================

    def set_sort(self, sort_type: str) -> dict:
        """切换排序方式

        参数:
            sort_type: windowName_up / windowName_down / useTime_up / useTime_down
        返回:
            dict: {"ok": True, "sortType": 实际生效的排序方式}
        """
        if sort_type not in SORT_TYPES:
            return {"ok": False, "message": "未知的排序方式", "sortType": self.config.get_sort_type()}
        self.config.set_sort_type(sort_type)
        return {"ok": True, "sortType": sort_type}

    def set_alias(self, proc_name: str, alias: str) -> dict:
        """设置/清除某进程的备注名

        参数:
            proc_name: 真实进程名
            alias: 备注名，留空表示清除备注
        返回:
            dict: {"ok": True, "display": 保存后的展示名称}
        """
        if not proc_name:
            return {"ok": False, "message": "进程名不能为空"}
        self.alias_store.set_alias(proc_name, alias or "")
        return {"ok": True, "display": self.alias_store.get_alias(proc_name)}

    def block_process(self, proc_name: str) -> dict:
        """屏蔽指定进程（不再显示、不再上传，本地仍继续计时）

        参数:
            proc_name: 真实进程名
        返回:
            dict: {"ok": True, "display": 展示名称}
        """
        if not proc_name:
            return {"ok": False, "message": "进程名不能为空"}
        self.blocked_store.block(proc_name)
        return {"ok": True, "display": self.alias_store.get_alias(proc_name)}

    def unblock_process(self, proc_name: str) -> dict:
        """解除指定进程的屏蔽"""
        if not proc_name:
            return {"ok": False, "message": "进程名不能为空"}
        self.blocked_store.unblock(proc_name)
        return {"ok": True, "display": self.alias_store.get_alias(proc_name)}

    def get_blocked_list(self) -> list:
        """返回被屏蔽的进程列表

        返回:
            list: [{"name": 真实进程名, "display": 展示名称}]
        """
        return [
            {"name": name, "display": self.alias_store.get_alias(name)}
            for name in self.blocked_store.get_all_blocked()
        ]

    # ==================== 账号与上传 ====================

    def login(self, server_url: str, user_email: str, user_password: str) -> dict:
        """登录并开始每秒上传数据

        参数:
            server_url: 后端地址，如 http://localhost:8080
            user_email: 登录邮箱
            user_password: 登录密码
        返回:
            dict: {"ok": bool, "message": str}
        """
        user_email = (user_email or "").strip()
        server_url = (server_url or "").strip().rstrip("/") or network.DEFAULT_BASE_URL

        if not user_email or "@" not in user_email:
            return {"ok": False, "message": "请输入正确的邮箱"}
        if not user_password:
            return {"ok": False, "message": "请输入登录密码"}

        with self._login_lock:
            if self._login_in_progress:
                return {"ok": False, "message": "正在登录，请稍候…"}
            self._login_in_progress = True

        try:
            client = network.ApiClient(base_url=server_url)
            token = client.login(user_email, user_password)

            self.config.set_server_url(server_url)
            self.config.set_user_email(user_email)
            self.config.set_token(token)
            # 手动登录成功后保存加密密码，供下次启动自动登录
            save_encrypted_password(user_email, user_password)

            self.monitor.upload_fail_count = 0
            self.monitor.set_upload_stage("ok", f"已连接，上次上传 {mytools.hour()}", "")
            return {"ok": True, "message": "登录成功，开始每秒上传数据"}
        except network.ApiException as e:
            logging.error(f"登录失败: {e}")
            return {"ok": False, "message": str(e)}
        except Exception as e:
            logging.error(f"登录异常: {e}")
            return {"ok": False, "message": f"无法连接服务器，请检查网络与服务器地址（{e}）"}
        finally:
            self._login_in_progress = False

    def logout(self) -> dict:
        """退出登录：通知服务器作废 token，并清除本地登录信息

        返回:
            dict: {"ok": True, "message": str}
        """
        if self._logout_in_progress:
            return {"ok": False, "message": "正在退出登录…"}

        self._logout_in_progress = True
        try:
            token = self.config.get_token()
            server_url = self.config.get_server_url()
            if token:
                client = network.ApiClient(base_url=server_url, token=token)
                try:
                    client.logout()
                except Exception as e:
                    # 服务器不可达时忽略错误，本地照常退出
                    logging.warning(f"退出登录请求失败(忽略): {e}")
        finally:
            self.config.set_token("")
            self.config.set_user_email("")
            clear_encrypted_password()
            self.monitor.set_upload_stage("not_login", "未登录", "")
            self._logout_in_progress = False

        return {"ok": True, "message": "已退出登录，停止上传数据"}

    def set_auto_startup(self, enabled: bool) -> dict:
        """开启/关闭开机自启动

        参数:
            enabled: True 开启，False 关闭
        返回:
            dict: {"ok": bool, "enabled": bool, "message": str}
        """
        try:
            if enabled:
                mytools.set_startup(APP_NAME)
            else:
                mytools.unset_startup(APP_NAME)
            self.config.set_auto_setup(bool(enabled))
            return {"ok": True, "enabled": bool(enabled), "message": "已开启开机自启动" if enabled else "已关闭开机自启动"}
        except Exception as e:
            logging.error(f"设置开机自启动失败: {e}")
            return {"ok": False, "enabled": mytools.is_startup_enabled(APP_NAME), "message": f"设置失败：{e}"}

    # ==================== 历史数据 ====================

    def list_history(self) -> list:
        """返回历史数据文件列表（按日期倒序）"""
        try:
            return self.monitor.history_overview()
        except Exception as e:
            logging.error(f"读取历史列表失败: {e}")
            return []

    def load_history(self, file_name: str) -> dict:
        """切换到指定日期的历史数据

        参数:
            file_name: 形如 data_2026-01-12.json
        返回:
            dict: {"ok": bool, "message": str, "snapshot": dict}
        """
        result = self.monitor.load_history(file_name or "")
        result["snapshot"] = self.monitor.snapshot()
        return result

    def back_to_today(self) -> dict:
        """从历史模式切回实时模式"""
        self.monitor.back_to_today()
        return {"ok": True, "snapshot": self.monitor.snapshot()}

    # ==================== 其它 ====================

    def open_data_dir(self) -> dict:
        """在资源管理器中打开程序数据目录"""
        try:
            os.startfile(str(APP_DIR))
            return {"ok": True}
        except Exception as e:
            return {"ok": False, "message": f"打开目录失败：{e}"}

    def hide_window(self) -> dict:
        """最小化窗口（配合前端“收起”按钮）"""
        try:
            if self._window:
                self._window.minimize()
            return {"ok": True}
        except Exception as e:
            return {"ok": False, "message": str(e)}