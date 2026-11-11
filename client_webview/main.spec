# -*- mode: python ; coding: utf-8 -*-
"""PyInstaller 打包配置（WebView 版客户端）。

与旧版 PyQt5 客户端的区别：前端资源 web/ 需要一并打包，
运行时由 mytools.resource_dir() 在 sys._MEIPASS 下定位。

用法：
    pyinstaller main.spec
"""
import os

block_cipher = None

# 前端静态资源必须打进包里
datas = [("web", "web")]

a = Analysis(
    ["main.py"],
    pathex=[os.getcwd()],
    binaries=[],
    datas=datas,
    hiddenimports=[
        "webview.platforms.edgechromium",
        "webview.platforms.winforms",
        "clr_loader",
        "pythonnet",
        # pystray 的 Windows 后端是运行时动态导入的，需显式声明
        "pystray._win32",
        "PIL.Image",
        "PIL.ImageDraw",
    ],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    # 排除本项目用不到的 GUI 库，减小体积
    excludes=["PyQt5", "PySide2", "PyQt6", "PySide6", "tkinter", "matplotlib"],
    noarchive=False,
    cipher=block_cipher,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.zipfiles,
    a.datas,
    [],
    name="DeviceUsageTime",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=False,
    icon=os.path.join("web", "assets", "icon.ico"),
)