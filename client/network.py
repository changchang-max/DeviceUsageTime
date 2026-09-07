# -*- coding: utf-8 -*-
"""
网络请求模块：封装与后端服务器(Spring Boot)的 HTTP 通信。

接口按 docs/前后端API文档.md 实现：
    - 用户登录      POST /api/auth/login
    - 退出登录      POST /api/auth/logout
    - 上传增量数据  POST /api/data/upload      (由调用方每秒调用一次)
    - 获取用户信息  GET  /api/user/profile     (用于校验Token是否仍有效)

服务端统一响应格式：{"code": 200, "message": "操作成功", "data": ...}
业务失败或网络异常时抛出 ApiException。
"""
import datetime
import logging
import requests

# 默认服务器地址（开发环境），设置页可修改
DEFAULT_BASE_URL = "http://localhost:8080"

# 请求超时：(连接超时秒数, 读取超时秒数)
REQUEST_TIMEOUT = (3, 10)


class ApiException(Exception):
    """接口调用异常：携带业务码 code 与提示信息 message"""

    def __init__(self, code: int, message: str):
        super().__init__(message)
        self.code = code
        self.message = message

    def __str__(self):
        return self.message


class ApiClient:
    """后端 API 客户端。base_url 形如 http://localhost:8080（不含 /api）"""

    def __init__(self, base_url: str = DEFAULT_BASE_URL, token: str = None):
        # 去掉末尾的斜杠，避免拼URL时出现双斜杠
        self.base_url = base_url.rstrip("/") if base_url else DEFAULT_BASE_URL
        # 登录后持有的JWT token，上传数据时放入 Authorization 请求头
        self.token = token

    def _url(self, path: str) -> str:
        """拼接完整接口地址，path 形如 /api/auth/login"""
        return f"{self.base_url}/{path.lstrip('/')}"

    def _headers(self) -> dict:
        headers = {"Content-Type": "application/json"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        return headers

    def _request(self, method: str, path: str, **kwargs) -> object:
        """发起一次请求并解析统一响应，成功时返回响应中的 data"""
        try:
            resp = requests.request(
                method, self._url(path),
                headers=self._headers(),
                timeout=REQUEST_TIMEOUT,
                **kwargs
            )
        except requests.RequestException as e:
            # 连接失败、超时等网络层错误
            logging.error(f"网络请求失败: {method} {path}, 错误: {e}")
            raise ApiException(-1, f"无法连接服务器，请检查网络与服务器地址（{e}）")

        # Token无效/已过期/被加入黑名单时，过滤器会直接返回HTTP 401
        if resp.status_code == 401:
            raise ApiException(401, "登录已失效，请重新登录")
        # 触发限流(如数据上传每秒1次)
        if resp.status_code == 429:
            raise ApiException(429, "请求过于频繁，请稍后再试")

        # 解析统一响应体
        try:
            body = resp.json()
        except ValueError:
            raise ApiException(resp.status_code, f"服务器返回异常(HTTP {resp.status_code})")

        code = body.get("code", resp.status_code)
        message = body.get("message", "未知错误")
        data = body.get("data")

        # HTTP状态码或业务码非2xx一律视为失败
        if resp.status_code >= 400 or (isinstance(code, int) and code >= 400):
            raise ApiException(code, message)

        return data

    def login(self, user_email: str, user_password: str) -> str:
        """登录接口(3.3)：成功后返回token字符串，并保存到本实例"""
        data = self._request("POST", "/api/auth/login", json={
            "user_email": user_email,
            "user_password": user_password,
        })
        # 按API文档，登录成功时 data 直接为 token 字符串
        if not isinstance(data, str) or not data:
            raise ApiException(-1, "登录失败：服务器未返回有效的Token")
        self.token = data
        return data

    def logout(self):
        """退出登录(3.5)：使当前token失效，进入Redis黑名单"""
        if not self.token:
            return
        self._request("POST", "/api/auth/logout")

    def get_user_profile(self) -> dict:
        """获取当前登录用户信息(4.1)：可用于校验token是否仍然有效"""
        data = self._request("GET", "/api/user/profile")
        return data if isinstance(data, dict) else {}

    def upload(self, upload_data: dict):
        """上传一次增量数据(5.1)：服务端收到后推送实时数据给查看者"""
        self._request("POST", "/api/data/upload", json=upload_data)


def now_iso_timestamp() -> str:
    """生成带本地时区偏移的ISO8601时间戳，如 2026-09-06T12:34:56.789+08:00。

    携带明确时区偏移可避免服务端按默认时区降级解析并记录error日志。
    """
    return datetime.datetime.now().astimezone().isoformat(timespec="milliseconds")


def trim_process_name(process_name: str) -> str:
    """去掉进程名末尾的.exe后缀，如 "chrome.exe" -> "chrome"，使上传名称更简洁"""
    if process_name is None:
        return process_name
    if process_name.lower().endswith(".exe"):
        return process_name[:-4]
    return process_name


def build_upload_payload(app_snapshot: dict, running_apps: set,
                         foreground_name: str = None, foreground_title: str = None,
                         user_email: str = "") -> dict:
    """把客户端内部监控数据组装成 前后端API文档 5.1 的增量上传JSON结构。

    :param app_snapshot: 内部字典快照，形如 {进程名: {"pid":..,"title":..,"use_time":..}}
    :param running_apps: 当前仍在运行的应用进程名集合(仅运行中的应用会上报)
    :param foreground_name: 屏幕最顶端窗口所在进程名(用于标记isActive)
    :param foreground_title: 屏幕最顶端窗口标题(仅前台应用携带)
    :param user_email: 登录用户邮箱
    """
    applications = []
    for name in running_apps:
        proc_info = app_snapshot.get(name)
        if proc_info is None:
            continue
        # 最顶端窗口所在进程名与当前应用一致时，视为前台活跃应用
        is_active = foreground_name is not None and name == foreground_name
        app_item = {
            "name": trim_process_name(name),
            "duration": int(proc_info.get("use_time", 0)),
            "isActive": is_active,
            # 出现在上报列表即代表正在运行，isRunning默认为true
            "isRunning": True,
        }
        # 仅在应用处于前台时携带窗口标题(窗口标题随时可能变化)
        if is_active and foreground_title:
            app_item["windowTitle"] = foreground_title
        applications.append(app_item)

    return {
        "userEmail": user_email,
        "timestamp": now_iso_timestamp(),
        "applications": applications,
        # 键盘敲击/鼠标点击/鼠标移动距离统计尚未实现，暂不上传statistics字段，
        # 服务端收到null时不会覆盖已有的统计数据
    }
