import pygetwindow as gw
import threading
import time
import sys
from PySide2.QtWidgets import QApplication, QMainWindow,QSystemTrayIcon
from ui_mainWindow import Ui_MainWindow
from PySide2.QtWidgets import *
from PySide2.QtGui import QIcon
from PySide2.QtCore import QEvent
import mytools

# 该变量用来通知结束线程
stop_event = threading.Event()

# 每秒获取所有窗口活动状态
def window_monitor(tabelWidget: QTableWidget):
    global active_window_list
    while not stop_event.is_set():
        # 过滤掉空标题的窗口
        active_window_list = [t for t in gw.getAllTitles() if t]

        # 调用关键函数
        add_row(tabelWidget, active_window_list)
        time.sleep(1) #每隔一秒捕获一次

# (QTableWidget对象,指定列,指定值)判断表的指定列是否存在指定值，存在则返回row_index
def is_exist(tabelWidget: QTableWidget, column: int, value) -> bool:
    row = tabelWidget.rowCount()
    for table_row in range(0, row):
        column_text = tabelWidget.item(table_row, column).text()
        if value == column_text:
            return table_row
    return False

def add_row(tabelWidget: QTableWidget, title_list: list):
    for title in title_list:
        # 先判断表里有没有，有则更改其值，没有则添加新行
        tabel_row = is_exist(tabelWidget, 1, title)
        if tabel_row is not False:
            tabel_time:str = tabelWidget.item(tabel_row, 2).text()
            tabelWidget.setItem(tabel_row, 2, QTableWidgetItem(mytools.add_str_time(tabel_time,1)))
        else:
            # 在末尾添加新行
            row = tabelWidget.rowCount()  # 得到当前行数
            tabelWidget.insertRow(row)

            table_id = row + 1  # 定义id的值
            tabel_default_time = 1  # 定义新建时的time值

            tabelWidget.setItem(row, 0, QTableWidgetItem(str(table_id)))  # id
            tabelWidget.setItem(row, 1, QTableWidgetItem(title))  # title
            tabelWidget.setItem(row, 2, QTableWidgetItem(mytools.get_strtime(tabel_default_time)))  # time

# 主窗口类
class MyMainWindow(QMainWindow, Ui_MainWindow):
    def __init__(self):
        super().__init__()
        
        self.setupUi(self)
        # 重写窗口
        self.init_Window()

        self.tray_icon = QSystemTrayIcon(QIcon("icon.ico"),self)
        # 在系统托盘中显示图标
        self.tray_icon.show()
        self.tray_icon.activated.connect(self.on_tray_icon_activated)
        
        
        # 启动监控线程
        self.thread = threading.Thread(target=window_monitor, args=(self.tableWidget,))
        self.thread.daemon = True  # 主线程退出时自动结束
        self.thread.start()

    # 重写父类捕获退出的方法
    def closeEvent(self, event):
        # 通知线程停止
        stop_event.set()
        # 等待线程结束
        self.thread.join()
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
    app = QApplication(sys.argv)
    window = MyMainWindow()
    window.setWindowTitle("屏幕视奸器")
    window.show()

    sys.exit(app.exec_())  # 进入事件循环
