# -*- coding: utf-8 -*-
"""内置静态文件服务。

pywebview 支持直接加载本地 html 文件，但 file:// 协议下 ES Module、
fetch 与缓存策略都比较别扭，因此这里起一个本机回环端口的静态服务，
前端页面通过 http://127.0.0.1:PORT 访问，行为与浏览器完全一致。
"""
import functools
import http.server
import logging
import pathlib
import threading


class _QuietHandler(http.server.SimpleHTTPRequestHandler):
    """静态文件处理器：关闭访问日志、禁用缓存，方便开发时即时生效。"""

    # 补充部分 Windows 系统下默认不认识的 MIME 类型
    extensions_map = {
        **http.server.SimpleHTTPRequestHandler.extensions_map,
        ".js": "text/javascript; charset=utf-8",
        ".mjs": "text/javascript; charset=utf-8",
        ".css": "text/css; charset=utf-8",
        ".html": "text/html; charset=utf-8",
        ".json": "application/json; charset=utf-8",
        ".svg": "image/svg+xml",
        ".ico": "image/x-icon",
        ".woff2": "font/woff2",
    }

    def end_headers(self):
        # 客户端是本地应用，不需要浏览器缓存，避免改动后仍加载旧文件
        self.send_header("Cache-Control", "no-store, must-revalidate")
        super().end_headers()

    def log_message(self, format, *args):  # noqa: A002 - 覆盖父类的同名参数
        """把访问日志降级到 debug，避免刷屏"""
        logging.debug("static: " + format % args)


class StaticServer:
    """本地静态文件服务，绑定 127.0.0.1 的随机可用端口。"""

    def __init__(self, root: pathlib.Path, host: str = "127.0.0.1", port: int = 0):
        """
        参数:
            root: 静态文件根目录（web 文件夹）
            host: 监听地址，默认仅本机回环
            port: 监听端口，0 表示由系统分配
        """
        self.root = pathlib.Path(root)
        self.host = host
        self.port = port
        self._httpd = None
        self._thread = None

    def start(self) -> str:
        """启动服务

        返回:
            str: 形如 http://127.0.0.1:52331 的基地址
        """
        handler = functools.partial(_QuietHandler, directory=str(self.root))
        self._httpd = http.server.ThreadingHTTPServer((self.host, self.port), handler)
        self._httpd.daemon_threads = True
        self.port = self._httpd.server_address[1]
        self._thread = threading.Thread(target=self._httpd.serve_forever, name="static-server", daemon=True)
        self._thread.start()
        logging.info(f"静态服务已启动: http://{self.host}:{self.port} -> {self.root}")
        return self.base_url()

    def base_url(self) -> str:
        """返回服务基地址"""
        return f"http://{self.host}:{self.port}"

    def stop(self):
        """停止服务"""
        if self._httpd is not None:
            self._httpd.shutdown()
            self._httpd.server_close()
            self._httpd = None
        if self._thread is not None and self._thread.is_alive():
            self._thread.join(timeout=2)
        self._thread = None