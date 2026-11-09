# -*- coding: utf-8 -*-
"""屏幕视奸器 · WebView 版客户端入口。

架构：
    main.py           启动 pywebview 窗口 + 本机静态服务
    monitor.py        后台采集（进程时长 / 输入统计 / 自动保存 / 数据上传）
    api.py            js_api 桥接层（前端 window.pywebview.api.*）
    store.py          本地配置、备注、屏蔽、密码与历史数据
    network.py        与 Spring Boot 后端通信
    web/              前端界面（HTML + CSS + 原生 ES Module）

运行：
    python main.py          正常启动
    python main.py --debug  打开调试模式（右键可审查元素）
"""
import argparse
import logging
import sys

import webview

import mytools
from api import APP_TITLE, WebApi
from monitor import MonitorService
from server import StaticServer
from store import (
    APP_DIR,
    AliasStore,
    BlockedStore,
    ConfigFile,
    HistoryStore,
    ensure_dirs,
)

# 崩溃日志写在程序目录下（与旧版保持一致，方便排查线上问题）
logging.basicConfig(
    filename=str(APP_DIR / "crash.log"),
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    encoding="utf-8",
)


def handle_exception(exc_type, exc_value, exc_traceback):
    """全局未捕获异常处理：写入 crash.log 后再打印到控制台"""
    logging.error("程序崩溃", exc_info=(exc_type, exc_value, exc_traceback))
    sys.__excepthook__(exc_type, exc_value, exc_traceback)


sys.excepthook = handle_exception


def parse_args():
    """解析命令行参数

    返回:
        argparse.Namespace: 解析结果
    """
    parser = argparse.ArgumentParser(description="屏幕视奸器 WebView 客户端")
    parser.add_argument("--debug", action="store_true", help="开启 webview 调试模式（可右键审查元素）")
    parser.add_argument("--width", type=int, default=920, help="窗口宽度，默认 920")
    parser.add_argument("--height", type=int, default=700, help="窗口高度，默认 700")
    parser.add_argument("--port", type=int, default=0, help="静态服务端口，默认由系统分配")
    return parser.parse_args()


def main():
    """程序主流程：组装依赖 → 启动静态服务 → 创建窗口 → 进入 GUI 事件循环"""
    args = parse_args()
    ensure_dirs()

    # ---------- 数据层 ----------
    config = ConfigFile()
    alias_store = AliasStore()
    blocked_store = BlockedStore()
    history_store = HistoryStore()

    # ---------- 业务层 ----------
    monitor = MonitorService(config, alias_store, blocked_store, history_store)

    # ---------- 前端资源 ----------
    web_dir = mytools.resource_dir() / "web"
    server = StaticServer(web_dir, port=args.port)
    base_url = server.start()

    # ---------- 桥接层 ----------
    api = WebApi(monitor, config, alias_store, blocked_store, history_store)

    window = webview.create_window(
        APP_TITLE,
        url=f"{base_url}/index.html",
        js_api=api,
        width=args.width,
        height=args.height,
        min_size=(760, 540),
        resizable=True,
        background_color="#eef2f9",
        text_select=True,
    )
    api.attach_window(window)
    # 拦截关闭按钮：返回 False 取消关闭，由前端弹出“确认退出”对话框
    window.events.closing += api.on_window_closing

    try:
        # private_mode=True：不落地浏览器缓存/ Cookie，避免在数据目录里生成几百 MB 的 WebView2 配置
        webview.start(
            api.start_background,
            debug=args.debug,
            icon=str(web_dir / "assets" / "icon.ico"),
        )
    finally:
        # 事件循环退出后收尾：停线程、存数据、关静态服务
        monitor.stop(save=True)
        server.stop()


if __name__ == "__main__":
    main()