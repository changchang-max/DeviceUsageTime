import pygetwindow as gw
import threading
import time
import sys
from PySide2.QtWidgets import QApplication, QMainWindow,QSystemTrayIcon
from ui_mainWindow import Ui_MainWindow
from ui_settings import Ui_Settings
from PySide2.QtWidgets import *
from PySide2.QtGui import QIcon,QPixmap
from PySide2.QtCore import QEvent,Qt
from qt_material import apply_stylesheet
from ui_exit_window import Ui_Exit
from ui_history_data_window import Ui_history_data_window
import mytools
import imgaes
import base64
import json
import pathlib
import os
import logging

#1.引进了日志功能--差个保存为文件
#2.增加查看历史数据功能



# 每秒获取所有窗口活动状态
def window_monitor(tableWidget: QTableWidget,all_applications_dict:dict,the_old_date_application_dict:dict):
    # the_old_date_application_dict属性有两种状态，一种是flase，另一种是字典数据
    # getall，其子元素若dict里有，则吧dict的数值+1。若没有，则新增，数值默认为1
    global current_date
    global config_File  #配置文件类
    global old_date_status  # 是否处于查看历史信息状态true/flase
    global old_date_refrush_flag    # 用来标记主窗口是否已经刷新过

    while not stop_event.is_set():
        # 判断是否为新的日期
        new_date = time.strftime("%Y-%m-%d", time.localtime())
        if new_date != current_date:
            # 跨天
            with thread_lock:
                all_applications_dict.clear()#清空字典
                tableWidget.setRowCount(0)#清空表单
                current_date = new_date


        # 加上线程锁防止资源竞争
        with thread_lock:

            # 过滤掉空标题的窗口
            for t in list(set(gw.getAllTitles())):
                # 去重操作
                if t and t in all_applications_dict:
                    # 已有该应用，则使用时长+1
                    all_applications_dict[t] += 1
                elif t:
                    # 新应用，添加到字典，使用时长默认为1
                    all_applications_dict[t] = 1
                else:
                    # 标题为空的窗口，忽略
                    pass
        
        # 每次循环都对字典进行排序(应该用clear与update把操作同步给原字典，而不只是局部变量)
        def sort_dict(application_dict:dict):
            new_applications_dict = Sort(application_dict).sort(config_File.get_sort_type())
            application_dict.clear()
            application_dict.update(new_applications_dict)

        # old_date_status = False是正常状态，即历史模式未开启状态
        if old_date_status is False:
            sort_dict(all_applications_dict)
        else:
            sort_dict(the_old_date_application_dict)
        

        try:
            if old_date_status is not False:
                if old_date_refrush_flag is False:
                    tableWidget.setRowCount(0) #清空表单
                    old_date_refrush_flag = True    # 标记为已刷新
                add_row(tableWidget, the_old_date_application_dict)
            else:
                # 调用关键函数,向表中添加行
                add_row(tableWidget, all_applications_dict)
        except:
            logging.error("window_monitor函数出错了")

        # add_row(tableWidget, all_applications_dict)
        time.sleep(1) #每隔一秒捕获一次

# (QTableWidget对象,指定列,指定值)判断表的指定列是否存在指定值，存在则返回row_index
def is_exist(tableWidget: QTableWidget, column: int, value) -> bool:
    row = tableWidget.rowCount()
    for table_row in range(0, row):
        column_text = tableWidget.item(table_row, column).text()
        if value == column_text:
            return table_row
    return False

# (QTableWidget对象,标题列表)向表中添加行
def add_row(tableWidget: QTableWidget, all_applications_dict: dict):
    title_list = all_applications_dict.keys()
    for title in title_list:
        # 先判断表里有没有，有则更改其值，没有则添加新行
        tabel_row = is_exist(tableWidget, 1, title)
        if tabel_row is not False:
            # time已经在字典里更新好了，可以直接用字典的内容覆盖上去
            Item_new_time = QTableWidgetItem(mytools.get_strtime(all_applications_dict[title]))
            Item_new_time.setTextAlignment(Qt.AlignCenter) # 为新的值也设置文本居中
            tableWidget.setItem(tabel_row, 2, Item_new_time)
        else:
            # 在末尾添加新行
            row = tableWidget.rowCount()  # 得到当前行数
            tableWidget.insertRow(row)

            table_id = row + 1  # 定义id的值
            tabel_default_time = all_applications_dict[title]  # 定义新建时的time值
            
            
            # 设置每个格子的内容
            Item_id = QTableWidgetItem(str(table_id))
            Item_title = QTableWidgetItem(title)
            Item_time = QTableWidgetItem(mytools.get_strtime(tabel_default_time))
            # 设置格子文本居中显示
            Item_id.setTextAlignment(Qt.AlignCenter)
            Item_time.setTextAlignment(Qt.AlignCenter)


            tableWidget.setItem(row, 0, Item_id)  # id
            tableWidget.setItem(row, 1, Item_title)  # title
            tableWidget.setItem(row, 2, Item_time)  # time

# 将base64字符串转成QPixmap(相当于图片文件了)
def to_image(base64_str:str):
    # 将base64转成QPixmap
    image_data = base64.b64decode(base64_str)
    pixmap = QPixmap()
    pixmap.loadFromData(image_data)
    return pixmap

# 将数据保存为json文件到本地，以日期命名
def save_data(data:dict):
    global current_date
    p = pathlib.Path("./history_data")
    # 若文件夹不存在则创建。2025/12/8补丁：若当前日期文件夹不存在在创建时清空字典。防止过了零点后数据没有清空导致逻辑bug
    if p.exists() is False or p.is_dir() is False:
        p.mkdir()

    # 覆盖写入
    file_name = pathlib.Path(f"./history_data/data_{current_date}.json")

    # 加上线程锁防止竞争
    with thread_lock:
        with open(str(file_name), "w", encoding="utf-8") as file:
            json.dump(data, file, ensure_ascii=False, indent=4)

# 自动保存线程函数
def auto_save_thread(all_applications_dict: dict):
    while not stop_event.is_set():
        time.sleep(60)  # 每60秒保存一次
        save_data(all_applications_dict)


# 定义“功能”类
class Functions:
    def __init__(self):
        self.app_name = "DeviceUsageTime"
        self.app_path = mytools.resource_path()

    def set_startup(self):
        # 设置开机自启动
        mytools.set_startup(self.app_name)

    def unset_startup(self):
        # 取消开机自启动
        mytools.unset_startup(self.app_name)

# 给字典排序类
class Sort:
    def __init__(self,all_applications_dict:dict):
        # 预设有四种模式（下面这四个函数）
        self.all_applications_dict = all_applications_dict

    def sort(self,type:str):
        match type:
            case "windowName_up":
                return self.windowName_up()
            case "windowName_down":
                return self.windowName_down()
            case "useTime_up":
                return self.useTime_up()
            case "useTime_down":
                return self.useTime_down()
            case _ :
                print("预期外的值")

    # 按名字升序
    def windowName_up(self)->dict:
        return {k:self.all_applications_dict[k] for k in sorted(self.all_applications_dict.keys())}
    # 按名字降序
    def windowName_down(self)->dict:
        return {k:self.all_applications_dict[k] for k in sorted(self.all_applications_dict.keys(),reverse=True)}
    # 按值升序
    def useTime_up(self)->dict:
        return {k:v for k,v in sorted(self.all_applications_dict.items(),key=lambda x: x[1])}
    # 按值降序
    def useTime_down(self)->dict:
        return {k:v for k,v in sorted(self.all_applications_dict.items(),key=lambda x: x[1],reverse=True)}        

# 配置文件相关类（初始化、读取、修改）
class Init_ConfigFile:
    # （先尝试从配置文件中拿到数据，如果拿不到则写入配置文件）
    def __init__(self):
        # 仅在程序打开的时候读取一次配置文件。剩下的都是如果有改动则写入。
        self.configFile_path = pathlib.Path("./config/config.json") # 配置文件路径
        self.init_config() # 初始化配置文件
        with open(self.configFile_path, "r", encoding="utf-8") as file:
            self.config_dict = json.load(file)    # 拿到存储的配置文件数据
        
        
    
    # 初始化配置文件（即第一次启动时新建配置文件）
    def init_config(self):
        
        if self.configFile_path.exists() is False or self.configFile_path.is_file() is False:
            self.configFile_path.parent.mkdir(parents=True, exist_ok=True) #创建父级目录
            # 创建一个内容为空的配置文件
            with open(self.configFile_path, "w", encoding="utf-8") as file:
                # 配置文件内容预留
                # 排序方式、开机自启{"sort_type": "useTime_up","auto_setup":True},
                json.dump({}, file, ensure_ascii=False, indent=4)

    # 将数据写回文件（在数据发生修改后使用）
    def wirteback_config(self):
        with open(self.configFile_path, "w", encoding="utf-8") as file:
            json.dump(self.config_dict, file, ensure_ascii=False, indent=4)
            
    # 排序方式
    def get_sort_type(self):
        # 尝试拿到sort_type，如果拿不到（可能配置文件为空）则默认为useTime_up
        self.sort_type = self.config_dict.get("sort_type",None)
        if self.sort_type is None: 
            self.set_sort_type("windowName_up")
            return "windowName_up" #如果不加这句，虽然在配置文件设置了，但返回的还是None
        return self.sort_type
    def set_sort_type(self,sort_type:str):
        self.config_dict["sort_type"] = sort_type

        self.wirteback_config()
        

    # 开机自启
    def get_auto_setup(self):
        # 尝试拿到auto_setup，如果拿不到（可能配置文件为空）则默认为False
        self.auto_setup = self.config_dict.get("auto_setup",None)
        if self.auto_setup is None: 
            self.set_auto_setup(False)
            return False
        return self.auto_setup
    def set_auto_setup(self,auto_setup:bool):
        self.config_dict["auto_setup"] = auto_setup
        self.wirteback_config()

# 主窗口类
class MyMainWindow(QMainWindow, Ui_MainWindow):
    def __init__(self):
        super().__init__()
        """数据初始化区"""
        # 把存储当前时间放到这里，防止打包后的获取当天时间出现问题
        global current_date
        global config_File # 创建配置文件类实例
        global old_date_status  # 表示用户是否正处于查看历史信息的状态 true正在查看历史/flase没有查看历史
        global old_date_refrush_flag    # 用来标记主窗口是否已经刷新过
        config_File = Init_ConfigFile()
        # self.config_File = config_File
        # 存储当天时间
        current_date = time.strftime("%Y-%m-%d")

        old_date_status = False
        old_date_refrush_flag = False


        """配置文件数据初始化区(暂时没用到)"""
        # 从配置文件拿到排序方式
        # self.sort_type = config_File.get_sort_type()  
        # 从配置文件拿到开机自启功能开启状态
        # auto_setup = config_File.get_auto_setup()  


        """窗口初始化区"""
        self.setupUi(self)
        # 重写窗口
        self.init_Window()

        # 新建功能类对象
        self.functions = Functions()
        
        self.tray_icon = QSystemTrayIcon(QIcon(to_image(imgaes.images["icon"])),self)

        # 在系统托盘中显示图标
        self.tray_icon.show()
        self.tray_icon.activated.connect(self.on_tray_icon_activated)

        # 隐藏TableWidget左侧默认自带的序号栏
        self.tableWidget.verticalHeader().setVisible(False)
        
        
        # 定义存储所有应用使用时长的字典
        self.all_applications_dict = {}
        self.init_data()  # 初始化数据
        # 有false和字典数据两种状态。被用户双击选中日期后会变为字典数据。用户再次选择当天日期后，再变回false
        self.the_old_date_application_dict = {} #用于存储旧日期的数据。
        


        # 启动监控线程
        self.thread_windows_listening = threading.Thread(target=window_monitor, args=(self.tableWidget,self.all_applications_dict,self.the_old_date_application_dict))
        self.thread_windows_listening.daemon = True  # 主线程退出时自动结束
        self.thread_windows_listening.start()
        # 启动自动保存json文件线程
        self.thread_auto_save = threading.Thread(target=auto_save_thread, args=(self.all_applications_dict,))
        self.thread_auto_save.daemon = True  # 主线程退出时自动结束
        self.thread_auto_save.start()

    # 退出程序时的确认窗口
    def open_exit_window(self):
        self.exit_window = QMainWindow()
        self.exit_ui = Ui_Exit()
        self.exit_ui.setupUi(self.exit_window)

        self.exit_ui.pushButton.clicked.connect(self.exit_application_action)
        self.exit_ui.pushButton_2.clicked.connect(self.exit_window.close)
        self.exit_window.show()
    
    # 执行退出程序的动作
    def exit_application_action(self):

        # 通知线程停止
        stop_event.set()
        # 等待监听线程结束
        self.thread_windows_listening.join()
        # 关闭时保存一次数据
        save_data(self.all_applications_dict)

        QApplication.quit()

    # 重写父类捕获退出的方法
    def closeEvent(self, event):
        self.open_exit_window()
        #return super().closeEvent(event)
        event.ignore()  # 忽略关闭事件
    
    # 对窗口进行重定义初始化
    def init_Window(self):
        # 设置列宽（单位：像素）
        self.tableWidget.setColumnWidth(0, 100)  # 第1列宽度
        self.tableWidget.setColumnWidth(1, 520)  # 第2列宽度
        self.tableWidget.setColumnWidth(2, 160)  # 第3列宽度


        # 为“设置”菜单添加点击动作
        # self.action_settings = QAction("打开设置",self)
        # self.menu_2.addAction(self.action_settings) # 添加下拉选项
        self.action_settings = self.action_5
        self.action_settings.triggered.connect(self.open_settings_window)

        # 为“排序”菜单添加点击动作
        self.action_windowName_up = self.action
        self.action_windowName_down = self.action_2
        self.action_useTime_up = self.action_3
        self.action_useTime_down = self.action_4

        # 为“历史”菜单添加点击动作
        self.action_history = self.action_6
        self.action_history.triggered.connect(self.open_historyData_window)

        self.action_windowName_up.triggered.connect(lambda: self.sort_change("windowName_up"))
        self.action_windowName_down.triggered.connect(lambda: self.sort_change("windowName_down"))
        self.action_useTime_up.triggered.connect(lambda: self.sort_change("useTime_up"))
        self.action_useTime_down.triggered.connect(lambda: self.sort_change("useTime_down"))
    
    # 初始化“设置”窗口
    def init_Settings_Window(self):
        global config_File
        # 连接“开机自启动”复选框与对应的动作
        self.settings_ui.checkBox.stateChanged.connect(self.on_checkBox_stateChanged)

        # 从配置文件拿到开机自启功能开启状态
        auto_setup = config_File.get_auto_setup()  
        # 设置复选框的状态
        if auto_setup:
            self.settings_ui.checkBox.setChecked(True)
        else:
            self.settings_ui.checkBox.setChecked(False)
    
    # 初始化数据，若存在当天数据则读取，而不是从空开始
    def init_data(self):
        date_str = time.strftime("%Y-%m-%d", time.localtime()) # 获取当前日期字符串
        file_name = f"./history_data/data_{date_str}.json"
        p = pathlib.Path(file_name)
        if p.exists() and p.is_file():
            # 读取json文件
            with open(file_name, "r", encoding="utf-8") as file:
                self.all_applications_dict = json.load(file)
        else:
            # 不存在则初始化为空字典
            self.all_applications_dict = {}
    
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
    
    # 动作：打开“设置”窗口
    def open_settings_window(self):
        # 点击设置时再创建Settings窗口实例
        self.settings_window = QMainWindow()
        self.settings_ui = Ui_Settings()
        self.settings_ui.setupUi(self.settings_window)
        self.settings_window.setWindowTitle("设置")

        # 启动前执行初始化函数（因为是自己定义的，写在ui_settings.py里容易丢失）
        self.init_Settings_Window()
        self.settings_window.show()
        
    # 设置页-开机自启动的单选框选择与取消动作
    def on_checkBox_stateChanged(self, state):
        global config_File
        # 2是选中，0是未选中，1是部分选中（该复选框不存在此数值）
        if state == 2:
            # 选中“开机自启动”
            self.functions.set_startup()    #执行“设置开机自启动”
            config_File.set_auto_setup(True)    #写回配置文件
        else:
            # 未选中“开机自启动”
            self.functions.unset_startup()  #执行“取消开机自启动”
            config_File.set_auto_setup(False)   #写回配置文件
    
    # 主窗口页-表格排序动作函数
    def sort_change(self,target_type:str):
        global config_File
        self.tableWidget.setRowCount(0)
        match target_type:
            case "windowName_up":
                config_File.set_sort_type("windowName_up")
            case "windowName_down":
                config_File.set_sort_type("windowName_down")
            case "useTime_up":
                config_File.set_sort_type("useTime_up")
            case "useTime_down":
                config_File.set_sort_type("useTime_down")
            case _ :
                print("预期外的值")
        
    #“退出”窗口，当用户点X时弹出
    def eixt_action(self):
        self.close()

    # 打开历史数据查看窗口
    def open_historyData_window(self):
        self.historyData_window = QMainWindow()
        self.historyData_ui = Ui_history_data_window()
        self.historyData_ui.setupUi(self.historyData_window)
        
        # 初始化listWidget内容
        self.init_historyList()
        # 双击内容打开文件->即重绘主窗口并关闭此窗口。同时后台记录不要断
        self.historyData_ui.listWidget.itemDoubleClicked.connect(self.on_item_doubleClicked)

        self.historyData_window.show()
    
    # 初始化listWidget历史数据
    def init_historyList(self):
        self.historyData_ui.listWidget.clear()
        # 将历史数据文件名添加到listWidget中
        for i in self.get_all_historyDataName():
            self.historyData_ui.listWidget.addItem(i)
        

    # 返回history_data文件夹中所有历史数据文件名（可能会有非常规文件，要有try异常捕获）
    def get_all_historyDataName(self) -> list:
        # 判断文件夹是否存在（仅判断，不存在则返回错误信息）
        historyData_dir = pathlib.Path("./history_data")
        if historyData_dir.exists() is False or historyData_dir.is_dir() is False:
            logging.error("history_data文件夹不存在")
            return []
        
        # 获取文件夹下所有文件名
        historyData_list = os.listdir(historyData_dir)

        # 把列表翻转一下，日期先新后旧好一些
        return sorted(historyData_list,reverse=True)

    # listWidget中的内容被双击时触发的函数
    def on_item_doubleClicked(self,item):
        global old_date_status
        global old_date_refrush_flag
        old_date_refrush_flag = False
        print("双击：",item.text())#--del
        if item.text() == f"data_{current_date}.json":
            # 点击当天日期后，将查看历史数据功能关闭
            old_date_status = False
        else:
            old_date_status = True # 开启查看历史信息模式
            with open(f"./history_data/{item.text()}","r",encoding="utf-8") as file:
                self.the_old_date_application_dict.update(json.load(file))
                print(self.the_old_date_application_dict)#--del
        
        
    



if __name__ == "__main__":
    # 该变量用来通知结束线程
    stop_event = threading.Event()
    # 定义线程锁，同时只能执行更新字典与保存字典的一个操作
    thread_lock = threading.Lock()
    


    # 切换工作目录为当前文件所在目录(以便正确创建文件夹)
    file_path = pathlib.Path(mytools.resource_path()).parent
    os.chdir(file_path)

    # 主线程是主窗口，其下有子线程刷新活动状态
    app = QApplication(sys.argv)
    # 2. 应用主题
    # 'dark_teal.xml' 是主题文件名，你可以换成其他的
    apply_stylesheet(
        app,
        theme='light_blue_500.xml',
        extra={
            'font_family': 'Microsoft YaHei',
            'font_size': '18px',      # 全局字体大小
        }
    )
    # app.setAttribute(Qt.AA_EnableHighDpiScaling)
    window = MyMainWindow()
    window.setFixedSize(800, 600)   #固定窗口大小，不可拉伸
    window.setWindowIcon(QIcon(to_image(imgaes.images["icon"])))
    window.setWindowTitle("屏幕视奸器")
    window.show()

    sys.exit(app.exec_())  # 进入事件循环
