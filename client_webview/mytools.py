# -*- coding: utf-8 -*-
"""通用工具包。

与 PyQt5 版本相比，这里去掉了所有界面相关的依赖，
只保留时间格式化、前台窗口识别、开机自启动等纯逻辑工具，
供 WebView 版本的监控层与桥接层复用。
"""
import os
import pathlib
import sys
import time
import winreg

# 提前定义一些日志标志，方便打印
info_error = "[ERROR]"
info_warning = "[WARNING]"
info_debug = "[DEBUG]"
info_info = "[INFO]"

# 开机自启动写入的注册表键（当前用户）
STARTUP_KEY_PATH = r"Software\Microsoft\Windows\CurrentVersion\Run"


# 获取当前时分秒
def hour() -> str:
    """返回当前本地时间字符串，格式 HH:MM:SS

    返回:
        str: 例如 "23:04:20"
    """
    return time.strftime("%H:%M:%S", time.localtime())


# 返回由秒转化成的时间字符串
def get_strtime(second: int) -> str:
    """把秒数转换成 HH:MM:SS 形式的字符串

    参数:
        second: 累计秒数
    返回:
        str: 例如 3661 -> "01:01:01"
    """
    try:
        second = int(second)
    except (TypeError, ValueError):
        second = 0
    if second < 0:
        second = 0
    return time.strftime("%H:%M:%S", time.gmtime(second))


# 应用根目录：源码运行时为 main.py 所在目录，打包后为 exe 所在目录
def app_dir() -> pathlib.Path:
    """返回程序根目录，兼容 PyInstaller 打包与源码运行

    返回:
        pathlib.Path: 程序根目录
    """
    if getattr(sys, "frozen", False):
        return pathlib.Path(sys.executable).resolve().parent
    return pathlib.Path(__file__).resolve().parent


# 资源目录：打包后资源被解压到 _MEIPASS，源码运行时就是程序根目录
def resource_dir() -> pathlib.Path:
    """返回静态资源根目录（前端 web 文件夹所在目录）

    返回:
        pathlib.Path: 资源根目录
    """
    meipass = getattr(sys, "_MEIPASS", None)
    if meipass:
        return pathlib.Path(meipass)
    return app_dir()


# 生成写入注册表的启动命令
def startup_command() -> str:
    """生成开机自启动需要的命令行

    返回:
        str: 打包运行时为 exe 路径；源码运行时为 pythonw.exe + main.py
    """
    if getattr(sys, "frozen", False):
        return f'"{pathlib.Path(sys.executable)}"'

    python_exe = pathlib.Path(sys.executable)
    # 源码运行时优先用 pythonw.exe，避免开机弹出黑色控制台窗口
    pythonw = python_exe.with_name("pythonw.exe")
    if pythonw.exists():
        python_exe = pythonw
    main_script = app_dir() / "main.py"
    return f'"{python_exe}" "{main_script}"'


# 将自身设置开机自启动，参数传要写入注册表的键名
def set_startup(app_name: str):
    """把本程序写入 HKCU 的 Run 键

    参数:
        app_name: 注册表中的启动项名称
    """
    key = winreg.OpenKey(winreg.HKEY_CURRENT_USER, STARTUP_KEY_PATH, 0, winreg.KEY_SET_VALUE)
    try:
        winreg.SetValueEx(key, app_name, 0, winreg.REG_SZ, startup_command())
    finally:
        winreg.CloseKey(key)


# 将自身取消开机自启动，参数传要删除的注册表的键名
def unset_startup(app_name: str):
    """从 HKCU 的 Run 键中删除本程序的启动项

    参数:
        app_name: 注册表中的启动项名称
    """
    key = winreg.OpenKey(winreg.HKEY_CURRENT_USER, STARTUP_KEY_PATH, 0, winreg.KEY_SET_VALUE)
    try:
        winreg.DeleteValue(key, app_name)
    except FileNotFoundError:
        # 启动项本就不存在，视为已关闭
        pass
    finally:
        winreg.CloseKey(key)


# 查询当前是否已经设置为开机自启动
def is_startup_enabled(app_name: str) -> bool:
    """检查注册表中是否存在本程序的启动项

    参数:
        app_name: 注册表中的启动项名称
    返回:
        bool: 存在返回 True
    """
    try:
        key = winreg.OpenKey(winreg.HKEY_CURRENT_USER, STARTUP_KEY_PATH, 0, winreg.KEY_READ)
    except OSError:
        return False
    try:
        winreg.QueryValueEx(key, app_name)
        return True
    except FileNotFoundError:
        return False
    except OSError:
        return False
    finally:
        winreg.CloseKey(key)


# 获取当前屏幕最顶端(前台)窗口对应的进程名与窗口标题
# 返回值：(进程名, 窗口标题)，失败或无前台窗口时返回 (None, None)
def get_foreground_window_info():
    """读取前台窗口所属进程名与窗口标题

    返回:
        tuple: (进程名, 窗口标题)，例如 ("chrome.exe", "百度一下")
    """
    # 仅在 Windows 下可用，且依赖 psutil 获取进程名，因此延迟导入避免模块间耦合
    try:
        import ctypes
        import psutil
    except ImportError:
        return None, None

    try:
        user32 = ctypes.windll.user32

        # 获取前台窗口句柄
        hwnd = user32.GetForegroundWindow()
        if not hwnd:
            return None, None

        # 获取该窗口所属进程的 PID
        pid = ctypes.c_ulong()
        user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))

        # 获取窗口标题(先取长度再取内容，避免中文截断)
        title_length = user32.GetWindowTextLengthW(hwnd)
        title_buffer = ctypes.create_unicode_buffer(title_length + 1)
        user32.GetWindowTextW(hwnd, title_buffer, title_length + 1)
        window_title = title_buffer.value.strip()

        # 由 PID 得到进程名(与 psutil 扫描结果格式一致，如 xxx.exe)
        process_name = None
        try:
            process_name = psutil.Process(pid.value).name()
        except (psutil.AccessDenied, psutil.NoSuchProcess):
            pass

        return process_name, window_title
    except Exception as e:
        print(f"获取前台窗口信息失败: {e}")
        return None, None


if __name__ == "__main__":
    print("当前时间：", hour())
    print("运行目录：", app_dir())
    print("启动命令：", startup_command())
    print("前台窗口：", get_foreground_window_info())