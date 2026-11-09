# -*- coding: utf-8 -*-
"""本地数据存储层。

统一管理 WebView 客户端的本地文件：
    - config/config.json              用户配置（排序方式、开机自启、服务器地址、登录信息）
    - config/alias.json               进程名 → 备注名 映射
    - config/blocked_processes.json   被屏蔽的进程列表
    - config/password.enc             机器绑定的加密密码（用于自动登录）
    - history_data/data_YYYY-MM-DD.json  每日使用时长历史数据

所有写入都采用“临时文件 + 原子替换”，避免程序被强杀时把文件写坏。
"""
import base64
import hashlib
import json
import logging
import os
import pathlib
import threading

import mytools
import network

# 程序根目录（兼容 PyInstaller 打包）
APP_DIR = mytools.app_dir()

# 各个数据文件的位置
CONFIG_DIR = APP_DIR / "config"
HISTORY_DIR = APP_DIR / "history_data"
CONFIG_FILE = CONFIG_DIR / "config.json"
ALIAS_FILE = CONFIG_DIR / "alias.json"
BLOCKED_FILE = CONFIG_DIR / "blocked_processes.json"
PASSWORD_FILE = CONFIG_DIR / "password.enc"

# 排序方式的合法取值
SORT_TYPES = ("windowName_up", "windowName_down", "useTime_up", "useTime_down")


def ensure_dirs():
    """确保 config 与 history_data 目录存在，程序启动时调用一次"""
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    HISTORY_DIR.mkdir(parents=True, exist_ok=True)


def _write_json(path: pathlib.Path, data):
    """原子写入 JSON 文件：先写临时文件再替换，避免写入中断造成文件损坏

    参数:
        path: 目标文件路径
        data: 可被 json 序列化的对象
    """
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    with open(tmp, "w", encoding="utf-8") as file:
        json.dump(data, file, ensure_ascii=False, indent=4)
    os.replace(tmp, path)


def _read_json(path: pathlib.Path, default):
    """读取 JSON 文件，失败时返回默认值

    参数:
        path: 文件路径
        default: 文件不存在或解析失败时的返回值
    """
    try:
        with open(path, "r", encoding="utf-8") as file:
            return json.load(file)
    except Exception:
        return default


class ConfigFile:
    """负责读取/写回 config.json。

    与旧版一致：只在启动时整体读入内存，之后修改内存并写回文件。
    """

    def __init__(self):
        self._lock = threading.RLock()
        ensure_dirs()
        if not CONFIG_FILE.is_file():
            _write_json(CONFIG_FILE, {})
        self.config_dict = _read_json(CONFIG_FILE, {})
        if not isinstance(self.config_dict, dict):
            self.config_dict = {}

    def writeback(self):
        """把内存中的配置写回文件（加锁，避免多线程交叉写入）"""
        with self._lock:
            _write_json(CONFIG_FILE, self.config_dict)

    # ---------- 排序方式 ----------
    def get_sort_type(self) -> str:
        """返回排序方式，缺省写入并返回 useTime_down"""
        with self._lock:
            sort_type = self.config_dict.get("sort_type")
        if sort_type not in SORT_TYPES:
            self.set_sort_type("useTime_down")
            return "useTime_down"
        return sort_type

    def set_sort_type(self, sort_type: str):
        """设置排序方式（非法值回落到 useTime_down）"""
        if sort_type not in SORT_TYPES:
            sort_type = "useTime_down"
        with self._lock:
            self.config_dict["sort_type"] = sort_type
        self.writeback()

    # ---------- 开机自启动 ----------
    def get_auto_setup(self) -> bool:
        """返回配置中记录的开机自启状态"""
        with self._lock:
            value = self.config_dict.get("auto_setup")
        return bool(value) if value is not None else False

    def set_auto_setup(self, auto_setup: bool):
        """写入开机自启状态"""
        with self._lock:
            self.config_dict["auto_setup"] = bool(auto_setup)
        self.writeback()

    # ---------- 服务器地址 ----------
    def get_server_url(self) -> str:
        """返回后端服务器地址，缺省使用 network.DEFAULT_BASE_URL"""
        with self._lock:
            server_url = self.config_dict.get("server_url")
        if not server_url:
            self.set_server_url(network.DEFAULT_BASE_URL)
            return network.DEFAULT_BASE_URL
        return server_url

    def set_server_url(self, server_url: str):
        """保存服务器地址（去掉末尾斜杠）"""
        with self._lock:
            self.config_dict["server_url"] = (server_url or "").rstrip("/")
        self.writeback()

    # ---------- 登录邮箱 ----------
    def get_user_email(self) -> str:
        """返回登录邮箱，未登录时为空字符串"""
        with self._lock:
            return self.config_dict.get("user_email", "") or ""

    def set_user_email(self, user_email: str):
        """保存登录邮箱"""
        with self._lock:
            self.config_dict["user_email"] = user_email or ""
        self.writeback()

    # ---------- 登录 token ----------
    def get_token(self) -> str:
        """返回 JWT token，未登录时为空字符串"""
        with self._lock:
            return self.config_dict.get("token", "") or ""

    def set_token(self, token: str):
        """保存 JWT token"""
        with self._lock:
            self.config_dict["token"] = token or ""
        self.writeback()


class AliasStore:
    """管理 config/alias.json，存储进程名到备注名的映射。

    文件格式：{"进程名.exe": "我的备注", ...}
    """

    def __init__(self):
        ensure_dirs()
        if not ALIAS_FILE.is_file():
            _write_json(ALIAS_FILE, {})
        self._lock = threading.RLock()
        data = _read_json(ALIAS_FILE, {})
        self._alias_dict = data if isinstance(data, dict) else {}

    def _save(self):
        with self._lock:
            _write_json(ALIAS_FILE, self._alias_dict)

    def get_alias(self, proc_name: str) -> str:
        """返回备注名，没有备注时返回原进程名"""
        with self._lock:
            return self._alias_dict.get(proc_name, proc_name)

    def get_raw_alias(self, proc_name: str) -> str:
        """返回备注名，没有备注时返回空字符串"""
        with self._lock:
            return self._alias_dict.get(proc_name, "")

    def has_alias(self, proc_name: str) -> bool:
        """判断该进程是否设置了备注名"""
        with self._lock:
            return proc_name in self._alias_dict

    def set_alias(self, proc_name: str, alias: str):
        """设置备注；传入空字符串或与进程名相同则删除备注"""
        alias = (alias or "").strip()
        with self._lock:
            if alias == "" or alias == proc_name:
                self._alias_dict.pop(proc_name, None)
            else:
                self._alias_dict[proc_name] = alias
        self._save()

    def snapshot(self) -> dict:
        """返回备注字典的浅拷贝（供上传线程使用）"""
        with self._lock:
            return dict(self._alias_dict)


class BlockedStore:
    """管理 config/blocked_processes.json，存储被屏蔽的进程名列表。

    被屏蔽的进程不会显示在界面上，也不会被上传到服务器，但其使用时长仍在本地累计。
    文件格式：["process1.exe", "process2.exe", ...]
    """

    def __init__(self):
        ensure_dirs()
        if not BLOCKED_FILE.is_file():
            _write_json(BLOCKED_FILE, [])
        self._lock = threading.RLock()
        data = _read_json(BLOCKED_FILE, [])
        self._blocked_set = set(data) if isinstance(data, list) else set()

    def _save(self):
        with self._lock:
            _write_json(BLOCKED_FILE, sorted(self._blocked_set))

    def is_blocked(self, proc_name: str) -> bool:
        """判断进程是否已被屏蔽"""
        with self._lock:
            return proc_name in self._blocked_set

    def block(self, proc_name: str):
        """屏蔽指定进程"""
        if not proc_name:
            return
        with self._lock:
            self._blocked_set.add(proc_name)
        self._save()

    def unblock(self, proc_name: str):
        """解除指定进程的屏蔽"""
        with self._lock:
            self._blocked_set.discard(proc_name)
        self._save()

    def get_all_blocked(self) -> list:
        """返回全部被屏蔽的进程名（已排序）"""
        with self._lock:
            return sorted(self._blocked_set)

    def count(self) -> int:
        """返回被屏蔽进程数量"""
        with self._lock:
            return len(self._blocked_set)


class PasswordManager:
    """密码加密/解密工具。

    使用 Windows 机器唯一标识（MachineGuid）作为密钥种子派生密钥，
    确保加密后的密码文件无法在其他机器上解密。
    加密方式：SHA-256 派生密钥 + XOR 流密码 + Base64 编码。
    """

    @staticmethod
    def _get_machine_key() -> bytes:
        """从 Windows MachineGuid 派生 32 字节加密密钥"""
        try:
            import winreg

            reg_key = winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, r"SOFTWARE\Microsoft\Cryptography")
            guid, _ = winreg.QueryValueEx(reg_key, "MachineGuid")
            winreg.CloseKey(reg_key)
            seed = guid.encode("utf-8")
        except Exception:
            # 降级方案：用主机名作为种子（至少与其他机器不同）
            import socket

            seed = socket.gethostname().encode("utf-8")
        return hashlib.sha256(seed).digest()

    @staticmethod
    def encrypt(plaintext: str) -> str:
        """加密明文，返回 Base64 编码的密文字符串"""
        key = PasswordManager._get_machine_key()
        data = plaintext.encode("utf-8")
        ciphertext = bytes(data[i] ^ key[i % len(key)] for i in range(len(data)))
        return base64.b64encode(ciphertext).decode("utf-8")

    @staticmethod
    def decrypt(cipher_b64: str) -> str:
        """解密 Base64 编码的密文，返回明文字符串"""
        key = PasswordManager._get_machine_key()
        ciphertext = base64.b64decode(cipher_b64)
        data = bytes(ciphertext[i] ^ key[i % len(key)] for i in range(len(ciphertext)))
        return data.decode("utf-8")


def save_encrypted_password(email: str, password: str):
    """把邮箱和机器绑定的加密密码写入 config/password.enc"""
    try:
        _write_json(PASSWORD_FILE, {"email": email, "password": PasswordManager.encrypt(password)})
    except Exception as e:
        logging.error(f"保存加密密码失败: {e}")


def load_encrypted_password() -> tuple:
    """读取本地加密密码，返回 (email, password)，不存在时返回 (None, None)"""
    try:
        if not PASSWORD_FILE.is_file():
            return None, None
        data = _read_json(PASSWORD_FILE, {})
        email = data.get("email", "")
        encrypted_pw = data.get("password", "")
        if not email or not encrypted_pw:
            return None, None
        return email, PasswordManager.decrypt(encrypted_pw)
    except Exception as e:
        logging.error(f"读取加密密码失败: {e}")
        return None, None


def clear_encrypted_password():
    """删除本地加密密码文件"""
    try:
        if PASSWORD_FILE.exists():
            PASSWORD_FILE.unlink()
    except Exception as e:
        logging.error(f"删除密码文件失败: {e}")


class HistoryStore:
    """历史数据读写：history_data/data_YYYY-MM-DD.json。

    文件结构：{"进程名": 使用秒数, ..., "_statistics": {...}}
    """

    def save(self, date_str: str, app_dict: dict, statistics: dict):
        """保存某一天的数据。

        参数:
            date_str: 日期字符串 YYYY-MM-DD
            app_dict: {进程名: {"pid":.., "title":.., "use_time":..}}
            statistics: 键盘/鼠标/总时长等统计信息
        """
        simple_data = {}
        for title, proc_info in app_dict.items():
            simple_data[title] = proc_info.get("use_time", 0)
        # _statistics 键以下划线开头，不会与进程名冲突
        simple_data["_statistics"] = statistics
        _write_json(HISTORY_DIR / f"data_{date_str}.json", simple_data)

    def exists(self, date_str: str) -> bool:
        """判断某一天的数据文件是否存在"""
        return (HISTORY_DIR / f"data_{date_str}.json").is_file()

    def size(self, date_str: str) -> int:
        """返回某一天数据文件的字节大小，不存在时返回 0"""
        path = HISTORY_DIR / f"data_{date_str}.json"
        try:
            return path.stat().st_size
        except OSError:
            return 0

    def load(self, file_name: str) -> dict:
        """读取指定历史文件，返回原始字典（含 _statistics）"""
        return _read_json(HISTORY_DIR / file_name, {})

    def list_files(self) -> list:
        """返回全部历史数据文件，按日期从新到旧排序"""
        if not HISTORY_DIR.is_dir():
            return []
        names = [name for name in os.listdir(HISTORY_DIR) if name.startswith("data_") and name.endswith(".json")]
        return sorted(names, reverse=True)