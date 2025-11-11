import pygetwindow as gw
import threading
import time
import sys
from PySide2.QtWidgets import QApplication, QMainWindow,QSystemTrayIcon
from ui_mainWindow import Ui_MainWindow
from PySide2.QtWidgets import *
from PySide2.QtGui import QIcon,QPixmap
from PySide2.QtCore import QEvent
import mytools
import imgaes
import base64
import json
import pathlib
import os

# 该变量用来通知结束线程
stop_event = threading.Event()
# 定义线程锁，同时只能执行更新字典与保存字典的一个操作
thread_lock = threading.Lock()

# 每秒获取所有窗口活动状态
def window_monitor(tabelWidget: QTableWidget,all_applications_dict:dict):
    # getall，其子元素若dict里有，则吧dict的数值+1。若没有，则新增，数值默认为1

    while not stop_event.is_set():
        # 加上线程锁防止资源竞争
        with thread_lock:
            # 过滤掉空标题的窗口
            for t in gw.getAllTitles():
                if t and t in all_applications_dict:
                    # 已有该应用，则使用时长+1
                    all_applications_dict[t] += 1
                elif t:
                    # 新应用，添加到字典，使用时长默认为1
                    all_applications_dict[t] = 1
                else:
                    # 标题为空的窗口，忽略
                    pass


        # 调用关键函数
        add_row(tabelWidget, all_applications_dict)
        time.sleep(1) #每隔一秒捕获一次

# (QTableWidget对象,指定列,指定值)判断表的指定列是否存在指定值，存在则返回row_index
def is_exist(tabelWidget: QTableWidget, column: int, value) -> bool:
    row = tabelWidget.rowCount()
    for table_row in range(0, row):
        column_text = tabelWidget.item(table_row, column).text()
        if value == column_text:
            return table_row
    return False

# (QTableWidget对象,标题列表)向表中添加行
def add_row(tabelWidget: QTableWidget, all_applications_dict: dict):
    title_list = all_applications_dict.keys()
    for title in title_list:
        # 先判断表里有没有，有则更改其值，没有则添加新行
        tabel_row = is_exist(tabelWidget, 1, title)
        if tabel_row is not False:
            # tabel_time:str = tabelWidget.item(tabel_row, 2).text()  # 从窗口中获取当前time值
            tabelWidget.setItem(tabel_row, 2, QTableWidgetItem(mytools.get_strtime(all_applications_dict[title])))
        else:
            # 在末尾添加新行
            row = tabelWidget.rowCount()  # 得到当前行数
            tabelWidget.insertRow(row)

            table_id = row + 1  # 定义id的值
            tabel_default_time = all_applications_dict[title]  # 定义新建时的time值

            tabelWidget.setItem(row, 0, QTableWidgetItem(str(table_id)))  # id
            tabelWidget.setItem(row, 1, QTableWidgetItem(title))  # title
            tabelWidget.setItem(row, 2, QTableWidgetItem(mytools.get_strtime(tabel_default_time)))  # time

# 将base64字符串转成QPixmap(相当于图片文件了)
def to_image(base64_str:str):
    # 将base64转成QPixmap
    image_data = base64.b64decode(base64_str)
    pixmap = QPixmap()
    pixmap.loadFromData(image_data)
    return pixmap

# 将数据保存为json文件到本地，以日期命名
def save_data(data:dict):
    p = pathlib.Path("./history_data")
    # 若文件夹不存在则创建
    if p.exists() is False or p.is_dir() is False:
        p.mkdir()

    date_str = time.strftime("%Y-%m-%d", time.localtime()) # 获取当前日期字符串
    # 覆盖写入
    file_name = f"./history_data/data_{date_str}.json"
    
    # 加上线程锁防止竞争
    with thread_lock:
        with open(file_name, "w", encoding="utf-8") as file:
            json.dump(data, file, ensure_ascii=False, indent=4)

def auto_save_thread(all_applications_dict: dict):
    while not stop_event.is_set():
        time.sleep(60)  # 每60秒保存一次
        save_data(all_applications_dict)

# 主窗口类
class MyMainWindow(QMainWindow, Ui_MainWindow):
    def __init__(self):
        super().__init__()
        
        self.setupUi(self)
        # 重写窗口
        self.init_Window()

        self.tray_icon = QSystemTrayIcon(QIcon(to_image(imgaes.images["icon"])),self)
        # todo:当仅打开exe文件时，会因为没有ico图标而无法做到托盘显示
        # 在系统托盘中显示图标
        self.tray_icon.show()
        self.tray_icon.activated.connect(self.on_tray_icon_activated)
        
        
        # 定义存储所有应用使用时长的字典
        self.all_applications_dict = {}


        # 启动监控线程
        self.thread_windows_listening = threading.Thread(target=window_monitor, args=(self.tableWidget,self.all_applications_dict))
        self.thread_windows_listening.daemon = True  # 主线程退出时自动结束
        self.thread_windows_listening.start()
        # 启动自动保存json文件线程
        self.thread_auto_save = threading.Thread(target=auto_save_thread, args=(self.all_applications_dict,))
        self.thread_auto_save.daemon = True  # 主线程退出时自动结束
        self.thread_auto_save.start()


    # 重写父类捕获退出的方法
    def closeEvent(self, event):
        # 通知线程停止
        stop_event.set()
        # 等待监听线程结束
        self.thread_windows_listening.join()
        # 关闭时保存一次数据
        save_data(self.all_applications_dict)
        return super().closeEvent(event)
    
    # 对窗口进行重定义初始化
    def init_Window(self):
        # 设置列宽（单位：像素）
        self.tableWidget.setColumnWidth(0, 100)  # 第1列宽度
        self.tableWidget.setColumnWidth(1, 460)  # 第2列宽度
        self.tableWidget.setColumnWidth(2, 200)  # 第3列宽度
    
    # 拦截最小化
    def changeEvent(self, event):
        if event.type() == QEvent.WindowStateChange:
            if self.isMinimized():
                self.hide()
    # 单击托盘图标恢复显示
    def on_tray_icon_activated(self, reason):
        if reason == QSystemTrayIcon.Trigger:  # 单击托盘图标
            self.showNormal()
            self.activateWindow()

if __name__ == "__main__":
    # 切换工作目录为当前文件所在目录(以便正确创建文件夹)
    file_path = pathlib.Path(__file__).parent
    os.chdir(file_path)

    # 主线程是主窗口，其下有子线程刷新活动状态
    app = QApplication(sys.argv)
    window = MyMainWindow()
    window.setWindowIcon(QIcon(to_image(imgaes.images["icon"])))
    window.setWindowTitle("屏幕视奸器")
    window.show()

    sys.exit(app.exec_())  # 进入事件循环
