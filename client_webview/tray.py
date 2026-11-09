# -*- coding: utf-8 -*-
"""系统托盘图标。

对应旧版 PyQt5 客户端里的 QSystemTrayIcon：
    - 最小化窗口时隐藏到托盘（任务栏不再占用）
    - 单击托盘图标 / 菜单“显示主界面”恢复窗口
    - 菜单“退出程序”保存数据后退出

依赖 pystray + Pillow；若运行环境缺少依赖则自动降级为“无托盘模式”，
程序其余功能不受影响。
"""
import logging
import threading

try:
    import pystray
    from PIL import Image, ImageDraw
    from pystray import Menu, MenuItem

    TRAY_AVAILABLE = True
    _IMPORT_ERROR = None
except Exception as exc:  # pragma: no cover - 仅在缺依赖时触发
    pystray = None
    Menu = None
    MenuItem = None
    Image = None
    ImageDraw = None
    TRAY_AVAILABLE = False
    _IMPORT_ERROR = exc


def _fallback_image():
    """托盘图标缺失时画一个简单的占位图标

    返回:
        PIL.Image.Image: 64x64 蓝色圆角图标
    """
    size = 64
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((4, 4, size - 4, size - 4), radius=16, fill=(33, 150, 243, 255))
    draw.ellipse((20, 20, 44, 44), fill=(255, 255, 255, 235))
    return image


class TrayController:
    """托盘图标控制器。"""

    def __init__(self, window, icon_path, title, on_quit):
        """
        参数:
            window: pywebview 窗口对象
            icon_path: 托盘图标路径（web/assets/icon.ico）
            title: 托盘提示文字
            on_quit: 点击“退出程序”时的回调（由 main.py 传入 api.confirm_exit）
        """
        self.window = window
        self.icon_path = icon_path
        self.title = title
        self.on_quit = on_quit

        self._icon = None
        self._thread = None

    def available(self) -> bool:
        """托盘功能是否可用（依赖是否齐全）"""
        return TRAY_AVAILABLE

    def start(self) -> bool:
        """创建并启动托盘图标

        返回:
            bool: 成功启动返回 True
        """
        if not TRAY_AVAILABLE:
            logging.warning(f"未安装 pystray/Pillow，跳过系统托盘：{_IMPORT_ERROR}")
            return False

        try:
            image = Image.open(self.icon_path).convert("RGBA")
            # 托盘图标需要较大尺寸，放大到 64x64 避免模糊
            image = image.resize((64, 64), Image.LANCZOS)
        except Exception as e:
            logging.warning(f"读取托盘图标失败，使用内置占位图标: {e}")
            image = _fallback_image()

        menu = Menu(
            MenuItem("显示主界面", self._on_show, default=True),
            MenuItem("打开设置", self._on_settings),
            MenuItem("隐藏到托盘", self._on_hide),
            Menu.SEPARATOR,
            MenuItem("退出程序", self._on_quit),
        )

        try:
            self._icon = pystray.Icon("DeviceUsageTime", image, self.title, menu)
            # pystray 的 run() 会阻塞，放到守护线程里不影响 pywebview 事件循环
            self._thread = threading.Thread(target=self._run, name="tray", daemon=True)
            self._thread.start()
            logging.info("系统托盘图标已启动")
            return True
        except Exception as e:
            logging.error(f"启动系统托盘失败: {e}")
            return False

    def _run(self):
        """托盘消息循环（阻塞）"""
        try:
            self._icon.run()
        except Exception as e:  # pragma: no cover
            logging.error(f"托盘消息循环异常: {e}")

    def stop(self):
        """停止托盘图标"""
        try:
            if self._icon is not None:
                self._icon.stop()
        except Exception as e:
            logging.warning(f"停止托盘图标失败: {e}")
        self._icon = None

    def notify(self, message: str, title: str = None, timeout: int = 3000):
        """弹出托盘气泡提示（不支持时静默忽略）

        参数:
            message: 提示内容
            title: 标题，默认使用程序名
            timeout: 显示时长（毫秒）
        """
        if self._icon is None:
            return
        try:
            self._icon.notify(message, title or self.title)
        except Exception as e:
            logging.debug(f"托盘气泡提示失败(忽略): {e}")

    # ==================== 菜单动作 ====================

    def show_window(self):
        """显示并还原主窗口"""
        try:
            if self.window is None:
                return
            self.window.show()
            # Show() 不会自动取消最小化状态，这里显式还原
            self.window.restore()
        except Exception as e:
            logging.error(f"从托盘恢复窗口失败: {e}")

    def hide_window(self):
        """隐藏主窗口到托盘"""
        try:
            if self.window is not None:
                self.window.hide()
        except Exception as e:
            logging.error(f"隐藏窗口失败: {e}")

    def _on_show(self, icon=None, item=None):
        self.show_window()

    def _on_hide(self, icon=None, item=None):
        self.hide_window()

    def _on_settings(self, icon=None, item=None):
        """打开设置页：先恢复窗口，再通知前端切到设置视图"""
        self.show_window()
        try:
            if self.window is not None:
                self.window.evaluate_js("window.APP && window.APP.openSettings && window.APP.openSettings();")
        except Exception as e:
            logging.warning(f"打开设置页失败: {e}")

    def _on_quit(self, icon=None, item=None):
        """退出程序：在独立线程里走保存 + 销毁窗口流程"""
        threading.Thread(target=self._quit, name="tray-quit", daemon=True).start()

    def _quit(self):
        try:
            self.on_quit()
        except Exception as e:
            logging.error(f"托盘退出失败: {e}")