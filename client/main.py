import threading
import time
import sys
import math
from PySide2.QtWidgets import QApplication, QMainWindow,QSystemTrayIcon
from ui_mainWindow import Ui_MainWindow
from ui_settings import Ui_Settings
from PySide2.QtWidgets import *
from PySide2.QtGui import QIcon,QPixmap
from PySide2.QtCore import QEvent,Qt,QThread,Signal,QTimer
from qt_material import apply_stylesheet
from ui_exit_window import Ui_Exit
from ui_history_data_window import Ui_history_data_window
import mytools
import network
import imgaes
import base64
import json
import pathlib
import os
import logging
import hashlib
import psutil

"""数据结构
all_applications_dict = {
进程title:{
“pid":pid值,
"title":进程名,
"use_time":使用时长
}}
类型：dict
程序内部所使用的复杂数据


simple_data=
{
    "NVIDIA GeForce Overlay": 14,
    "main.py - LocalVSCode (工作区) - Visual Studio Code": 14,
}
类型：dict
存储在数据文件中的简单数据
"""
# 生成程序崩溃日志
logging.basicConfig(filename="crash.log", level=logging.ERROR)

def handle_exception(exc_type, exc_value, exc_traceback):
    logging.error("程序崩溃", exc_info=(exc_type, exc_value, exc_traceback))
    
sys.excepthook = handle_exception  #sys.excepthook可以捕获所有未被捕获的异常


# ==================== 数据上传相关模块级状态 ====================
# 当前正在运行的用户应用名(进程名)集合，由 window_monitor 每秒刷新，供上传线程判断哪些应用仍在运行
current_running_apps = set()
# 数据上传阶段: not_login=未登录 / ok=上传正常 / error=上传失败 / invalid=登录失效
upload_stage = "not_login"
# 供界面展示的当前上传状态文字(由上传线程更新)
upload_status_text = "未登录"
# 最近一次上传错误信息(由上传线程更新)
upload_last_error = ""
# 保护上传状态读写的小锁(设置窗口/上传线程分属不同线程)
upload_lock = threading.Lock()
# 全局唯一的API客户端(登录后在登录worker里单独创建实例，避免与上传线程交叉使用)
api_client = network.ApiClient()

# ==================== 输入统计计数器 ====================
# 键盘敲击累计次数（今天累计，跨天时随 all_applications_dict 一起重置）
keyboard_count: int = 0
# 鼠标点击累计次数（左键/右键/中键，不含滚轮滚动）
mouse_click_count: int = 0
# 鼠标移动累计距离（像素），换算为米时乘以 PIXEL_TO_METER
mouse_distance_px: float = 0.0
# 上一次鼠标坐标，用于计算位移；None 表示尚未记录
_last_mouse_pos: tuple = None
# 滚轮滚动累计量（以 WHEEL_DELTA=120 为单位的绝对值之和）
mouse_scroll_delta: float = 0.0
# 1 像素 ≈ 0.000265 m（按 96dpi 近似），滚轮每个 WHEEL_DELTA 对应约 48px
PIXEL_TO_METER: float = 0.000140625
SCROLL_PX_PER_DELTA: float = 3.0     # 每个最小滚动单位(1格)等效像素数(近似)
# 保护输入计数器的锁（pynput 回调线程 vs 上传线程 vs 跨天重置）
input_lock = threading.Lock()

# ==================== 客户端程序运行时长计数器 ====================
# 客户端程序今日运行累计时长(秒): 由 window_monitor 每运行一秒 +1(与是否检测到应用无关)。
# 用于前端"今日应用使用时长"卡片的总时长(≈设备总使用时长/被监控时长)，
# 而非把所有应用时长相加(多应用并发运行时相加会成倍虚高)。跨天随应用字典一起清零。
run_duration: int = 0

# UI 冻结标志：当用户打开模态对话框（如重命名备注框）时设为 True，
# 此时 window_monitor 线程跳过 add_row 刷新，避免 UI 线程被大量 setItem 操作阻塞导致卡顿。
ui_freeze: bool = False


# 更新上传状态(供上传线程调用)
def set_upload_stage(stage: str, text: str, error: str = ""):
    global upload_stage, upload_status_text, upload_last_error
    with upload_lock:
        upload_stage = stage
        upload_status_text = text
        upload_last_error = error


# ==================== pynput 输入监听回调 ====================

def _on_key_press(key):
    """键盘按键按下时累计计数（所有按键均计入）"""
    global keyboard_count
    with input_lock:
        keyboard_count += 1


def _on_mouse_click(x, y, button, pressed):
    """鼠标按键按下时累计计数（左键/右键/中键，不含滚轮滚动）"""
    global mouse_click_count
    if pressed:   # 只在按下时计数，松开不计
        with input_lock:
            mouse_click_count += 1


def _on_mouse_move(x, y):
    """鼠标移动时累计平面位移（像素）"""
    global mouse_distance_px, _last_mouse_pos
    with input_lock:
        if _last_mouse_pos is not None:
            dx = x - _last_mouse_pos[0]
            dy = y - _last_mouse_pos[1]
            mouse_distance_px += math.sqrt(dx * dx + dy * dy)
        _last_mouse_pos = (x, y)


def _on_mouse_scroll(x, y, dx, dy):
    """滚轮滚动时累计等效像素距离（dy 为垂直滚动量，dx 为水平滚动量）"""
    global mouse_distance_px
    # |dx| + |dy| 通常每格为 1，换算为等效像素后累加到总移动距离
    with input_lock:
        mouse_distance_px += (abs(dx) + abs(dy)) * SCROLL_PX_PER_DELTA


def get_mouse_distance_meters() -> float:
    """返回当前累计鼠标移动距离（米），保留两位小数"""
    with input_lock:
        return round(mouse_distance_px * PIXEL_TO_METER, 2)


def start_input_listeners():
    """启动 pynput 全局键盘和鼠标监听器（守护线程，随主线程退出自动销毁）"""
    try:
        from pynput import keyboard as _kb, mouse as _ms
        kb_listener = _kb.Listener(on_press=_on_key_press)
        ms_listener = _ms.Listener(
            on_click=_on_mouse_click,
            on_move=_on_mouse_move,
            on_scroll=_on_mouse_scroll,
        )
        kb_listener.daemon = True
        ms_listener.daemon = True
        kb_listener.start()
        ms_listener.start()
        logging.info("pynput 输入监听器已启动")
    except Exception as e:
        logging.error(f"pynput 输入监听器启动失败: {e}")


# 过滤系统路径
SYSTEM_PATHS = (
    r"C:\Windows",
    r"C:\Program Files\WindowsApps",
)

# 过滤用户
SYSTEM_USERS = (
    "SYSTEM",
    "LOCAL SERVICE",
    "NETWORK SERVICE",
)

# 判断进程是否为用户应用
def is_user_app(proc: psutil.Process) -> bool:
    try:
        exe = proc.exe()
        username = proc.username()

        if not exe:
            return False

        exe = exe.lower()

        # 排除系统路径
        for path in SYSTEM_PATHS:
            if exe.startswith(path.lower()):
                return False

        # 排除系统用户
        for user in SYSTEM_USERS:
            if user.lower() in username.lower():
                return False

        return True

    except (psutil.AccessDenied, psutil.NoSuchProcess):
        return False

 
def get_user_app_pids() -> dict:
    # 首先只用 process_iter 获取 name 和 pid（轻量操作），
    # 用进程名初步过滤掉明显是系统进程的条目，减少后续 is_user_app 中
    # 昂贵的 proc.exe() / proc.username() 调用次数
    result = {}
    user_procs = []
    pid_map = {}

    for proc in psutil.process_iter(['pid', 'name']):
        try:
            pname = proc.name()
        except (psutil.AccessDenied, psutil.NoSuchProcess):
            continue

        # 快速预过滤：跳过明显的系统进程名（svchost, System, Idle 等）
        if not pname or pname.lower() in _SYSTEM_PROC_NAMES:
            continue

        # 需要通过 is_user_app 进一步过滤
        if is_user_app(proc):
            user_procs.append(proc)
            pid_map[proc.pid] = proc

    # 过滤子进程：仅过滤与父进程同名的子进程（如 Electron renderer），
    # 避免误杀被其他应用启动的进程（如 uTools 启动的 DSH Desktop）
    for proc in user_procs:
        try:
            ppid = proc.ppid()
            if ppid not in pid_map:
                # 父进程不在用户进程集合中 → 保留
                result[proc.name()] = {
                    "pid": proc.pid,
                    "title": proc.name(),
                    "use_time": 0
                }
            else:
                # 父进程也在用户进程中：仅当父进程同名时才跳过（视为子进程）
                parent = pid_map[ppid]
                if parent.name() != proc.name():
                    result[proc.name()] = {
                        "pid": proc.pid,
                        "title": proc.name(),
                        "use_time": 0
                    }
        except (psutil.AccessDenied, psutil.NoSuchProcess):
            continue

    return result


# 明显的系统进程名（小写），在 get_user_app_pids 中快速跳过，避免调用昂贵的 exe()/username()
_SYSTEM_PROC_NAMES = frozenset({
    "system idle process", "system", "registry", "smss.exe", "csrss.exe",
    "wininit.exe", "services.exe", "lsass.exe", "svchost.exe", "winlogon.exe",
    "dwm.exe", "spoolsv.exe", "fontdrvhost.exe", "memory compression",
    "secure system", "audiodg.exe",
})

# 每秒获取所有窗口活动状态
def window_monitor(tableWidget: QTableWidget,all_applications_dict:dict,the_old_date_application_dict:dict):
    # the_old_date_application_dict属性有两种状态，一种是flase，另一种是字典数据
    # getall，其子元素若dict里有，则吧dict的数值+1。若没有，则新增，数值默认为1
    global current_date
    global config_File  #配置文件类
    global old_date_status  # 是否处于查看历史信息状态true/flase
    global old_date_refrush_flag    # 用来标记主窗口是否已经刷新过
    global run_duration  # 客户端程序今日运行时长(秒)
    global _row_index_cache

    # 排序计数器：避免每秒都重复排序，改为每 5 秒排序一次
    _sort_counter = 0

    while not stop_event.is_set():
        # 判断是否为新的日期
        new_date = time.strftime("%Y-%m-%d", time.localtime())
        if new_date != current_date:
            # 跨天
            with thread_lock:
                all_applications_dict.clear()#清空字典
                _row_index_cache.clear()
                current_date = new_date
                run_duration = 0  # 新的一天客户端运行时长重新从0累计
            # 跨天时同步重置输入统计计数器
            global keyboard_count, mouse_click_count, mouse_distance_px, _last_mouse_pos
            with input_lock:
                keyboard_count = 0
                mouse_click_count = 0
                mouse_distance_px = 0.0
                _last_mouse_pos = None


        # 将慢速的 psutil 进程枚举移到锁外，避免 I/O 阻塞其他线程
        current_procs = get_user_app_pids()

        # 加上线程锁防止资源竞争
        with thread_lock:
            blocked_set = blocked_file.get_all_blocked()

            # 同步“当前正在运行的应用”集合，供上传线程使用（排除被屏蔽的进程）
            current_running_apps.clear()
            current_running_apps.update({k for k in current_procs if k not in blocked_set})

            # 更新 all_applications_dict：包含所有进程（包括被屏蔽的），
            # 被屏蔽的进程仍继续累加时间，但不显示、不上传
            # 1. 对所有正在运行的进程 use_time +1
            for title in list(all_applications_dict.keys()):
                if title in current_procs:
                    all_applications_dict[title]["use_time"] += 1

            # 2. 添加新进程（被屏蔽的也加入字典，确保时间持续累计）
            for title, proc_info in current_procs.items():
                if title not in all_applications_dict:
                    all_applications_dict[title] = proc_info.copy()
                    all_applications_dict[title]["use_time"] = 1

            # 客户端程序自身今日运行时长 +1(程序每运行一秒计1秒, 与是否有应用在运行无关)
            run_duration += 1
        
        # old_date_status = False是正常状态，即历史模式未开启状态
        # 排序操作放在 thread_lock 内，防止 sort_dict 中的 clear()+update()
        # 与 auto_save_thread 的 save_data() 发生竞态，导致当天数据被清空
        # 优化：每 5 秒排序一次，避免每秒重复排序造成不必要的 CPU 开销
        _sort_counter += 1
        if _sort_counter >= 5:
            _sort_counter = 0
            with thread_lock:
                if old_date_status is False:
                    new_dict = Sort(all_applications_dict).sort(config_File.get_sort_type())
                    all_applications_dict.clear()
                    all_applications_dict.update(new_dict)
                else:
                    new_dict = Sort(the_old_date_application_dict).sort(config_File.get_sort_type())
                    the_old_date_application_dict.clear()
                    the_old_date_application_dict.update(new_dict)
        

        time.sleep(1) #每隔一秒捕获一次

# (QTableWidget对象,指定列,指定值)判断表的指定列是否存在指定值，存在则返回row_index
# 标题列改为用 UserRole（真实进程名）匹配，避免备注名干扰
def is_exist(tableWidget: QTableWidget, column: int, value) -> bool:
    row = tableWidget.rowCount()
    for table_row in range(0, row):
        item = tableWidget.item(table_row, column)
        if item is None:
            continue
        # 标题列优先用 UserRole 存储的真实进程名匹配
        real_name = item.data(Qt.UserRole)
        compare_value = real_name if real_name is not None else item.text()
        if value == compare_value:
            return table_row
    return False

# 进程名 → 行号 快速查找缓存，避免 add_row 中的 O(n²) 遍历
# 在切换到历史数据时由 setRowCount(0) 清空
_row_index_cache: dict = {}

# (QTableWidget对象,标题列表)向表中添加行
def add_row(tableWidget: QTableWidget, all_applications_dict: dict):
    global alias_file, _row_index_cache, ui_freeze, blocked_file

    # 若表格行数归零（切换数据源），则重建缓存
    if tableWidget.rowCount() == 0:
        _row_index_cache.clear()

    # 若 UI 被冻结（如正在编辑备注对话框），跳过本次刷新，避免积累大量操作
    if ui_freeze:
        return

    for title, proc_info in all_applications_dict.items():
        proc_name = proc_info["title"]
        # 跳过已被屏蔽的进程（不在表格中显示，也不会上传）
        if blocked_file.is_blocked(proc_name):
            continue
        # 优先用缓存查找行号，缓存未命中再回退到 is_exist 遍历
        tabel_row = _row_index_cache.get(proc_name)
        if tabel_row is None:
            tabel_row = is_exist(tableWidget, 1, proc_name)
            if tabel_row is not False:
                _row_index_cache[proc_name] = tabel_row

        if tabel_row is not False:
            # time已经在字典里更新好了，可以直接用字典的内容覆盖上去
            Item_new_time = QTableWidgetItem(mytools.get_strtime(proc_info["use_time"]))
            Item_new_time.setTextAlignment(Qt.AlignCenter) # 为新的值也设置文本居中
            tableWidget.setItem(tabel_row, 2, Item_new_time)
        else:
            # 在末尾添加新行
            row = tableWidget.rowCount()  # 得到当前行数
            tableWidget.insertRow(row)

            table_id = row + 1  # 定义id的值
            tabel_default_time = proc_info["use_time"]  # 定义新建时的time值

            # 显示备注名（若无备注则显示原进程名）
            display_name = alias_file.get_alias(proc_name)

            # 设置每个格子的内容
            Item_id = QTableWidgetItem(str(table_id))
            Item_title = QTableWidgetItem(display_name)
            # 将真实进程名存入 UserRole，供 is_exist 和双击编辑使用
            Item_title.setData(Qt.UserRole, proc_name)
            Item_time = QTableWidgetItem(mytools.get_strtime(tabel_default_time))
            # 设置格子文本居中显示
            Item_id.setTextAlignment(Qt.AlignCenter)
            Item_time.setTextAlignment(Qt.AlignCenter)

            tableWidget.setItem(row, 0, Item_id)  # id
            tableWidget.setItem(row, 1, Item_title)  # title（显示备注名）
            tableWidget.setItem(row, 2, Item_time)  # time

            # 将新行加入缓存
            _row_index_cache[proc_name] = row

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

    # 转换为简单格式 {title:use_time,...}
    simple_data = {}
    for title, proc_info in data.items():
        simple_data[title] = proc_info["use_time"]

    # 附加输入统计计数器（_statistics 键以下划线开头，不会与进程名冲突）
    with input_lock:
        simple_data["_statistics"] = {
            "keyboardCount": keyboard_count,
            "mouseClickCount": mouse_click_count,
            "mouseDistance": round(mouse_distance_px * PIXEL_TO_METER, 2),
        }
    # 附加客户端程序今日运行时长(秒)，重启后可从数据文件恢复累计值
    simple_data["_statistics"]["totalDuration"] = run_duration

    # 覆盖写入
    # 在锁内读取 current_date 快照，防止跨天瞬间 window_monitor 修改 current_date
    # 导致文件名与数据内容日期不一致
    with thread_lock:
        save_date = current_date
        # 防御：如果字典为空但存在当天数据文件，说明可能是竞态导致的空字典，
        # 此时跳过本次保存，避免覆盖有效数据
        if len(data) == 0:
            existing = pathlib.Path(f"./history_data/data_{save_date}.json")
            if existing.exists() and existing.stat().st_size > 50:
                # 文件存在且有实质内容，跳过本次空写
                return
        with open(str(pathlib.Path(f"./history_data/data_{save_date}.json")), "w", encoding="utf-8") as file:
            json.dump(simple_data, file, ensure_ascii=False, indent=4)

# 自动保存线程函数
def auto_save_thread(all_applications_dict: dict):
    while not stop_event.is_set():
        time.sleep(60)  # 每60秒保存一次
        save_data(all_applications_dict)


# 数据上传线程：已登录时，每秒向服务器上传一次增量数据
def data_upload_thread(all_applications_dict: dict):
    while not stop_event.is_set():
        # 读取本地配置；未登录(token为空)时每秒空转等待登录
        token = config_File.get_token()
        user_email = config_File.get_user_email()

        if not token or not user_email:
            set_upload_stage("not_login", "未登录", "")
            time.sleep(1)
            continue

        # 每次上传前同步服务器地址与token(登录页可随时修改服务器地址)
        api_client.base_url = config_File.get_server_url()
        api_client.token = token

        try:
            # 在线程锁内拷贝快照，避免与window_monitor同时读写造成数据错乱
            try:
                with thread_lock:
                    snapshot = {name: dict(proc_info) for name, proc_info in all_applications_dict.items()}
                    running_apps = set(current_running_apps)
                    running_seconds = run_duration  # 客户端程序今日运行时长(秒)
                # 过滤掉已被屏蔽的进程（不显示也不上传）
                blocked_set = blocked_file.get_all_blocked()
                snapshot = {k: v for k, v in snapshot.items() if k not in blocked_set}
                running_apps = {a for a in running_apps if a not in blocked_set}
            except RuntimeError:
                # 恰好赶上window_monitor在锁外排序(clear+update)改写字典，本秒跳过，下一秒重试
                time.sleep(1)
                continue
            # 获取当前屏幕最顶端窗口的进程名与标题(用于标记isActive与windowTitle)
            foreground_name, foreground_title = mytools.get_foreground_window_info()
            # 读取输入统计计数器快照（在锁内复制，避免与pynput回调线程竞争）
            with input_lock:
                kb_count = keyboard_count
                mc_count = mouse_click_count
                md_meters = round(mouse_distance_px * PIXEL_TO_METER, 2)
            # 读取注备名映射字典，存在备注名时以上传备注名替换真实进程名
            alias_map = alias_file._alias_dict.copy() if alias_file else {}
            upload_data = network.build_upload_payload(
                snapshot, running_apps, foreground_name, foreground_title, user_email,
                kb_count, mc_count, md_meters, running_seconds, alias_map)
            api_client.upload(upload_data)
            set_upload_stage("ok", f"已连接，上次上传 {mytools.hour()}", "")
        except network.ApiException as e:
            if e.code in (401, 403):
                # token已失效：清空本地token，等待用户重新登录
                logging.error(f"数据上传失败，token失效: {e}")
                config_File.set_token("")
                set_upload_stage("invalid", "登录已失效，请重新登录", str(e))
            else:
                set_upload_stage("error", "上传失败", str(e))
        except Exception as e:
            # 网络抖动等未知异常：记录日志后下个周期自动重试
            logging.error(f"数据上传异常: {str(e)}")
            set_upload_stage("error", "上传失败", f"网络异常: {e}")

        time.sleep(1)  # 按要求每秒上传一次


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
        return {k:self.all_applications_dict[k] for k in sorted(self.all_applications_dict.keys(), reverse=True)}
    # 按值升序
    def useTime_up(self)->dict:
        return {k:v for k,v in sorted(self.all_applications_dict.items(), key=lambda x: x[1]["use_time"])}
    # 按值降序
    def useTime_down(self)->dict:
        return {k:v for k,v in sorted(self.all_applications_dict.items(), key=lambda x: x[1]["use_time"], reverse=True)}        


# 登录工作线程：在后台调用登录接口，避免网络等待卡住界面
class LoginWorker(QThread):
    # 登录成功信号(携带token字符串)
    login_success = Signal(str)
    # 登录失败信号(携带错误提示)
    login_failed = Signal(str)

    def __init__(self, user_email: str, user_password: str, server_url: str):
        super().__init__()
        self.user_email = user_email
        self.user_password = user_password
        self.server_url = server_url

    def run(self):
        # 独立创建ApiClient实例，避免与上传线程共用实例产生竞态
        client = network.ApiClient(base_url=self.server_url)
        try:
            token = client.login(self.user_email, self.user_password)
            self.login_success.emit(token)
        except network.ApiException as e:
            self.login_failed.emit(str(e))
        except Exception as e:
            logging.error(f"登录异常: {str(e)}")
            self.login_failed.emit(f"无法连接服务器，请检查网络与服务器地址({e})")


# 退出登录工作线程：通知服务器将token加入黑名单(网络异常时忽略错误，本地仍然退出)
class LogoutWorker(QThread):
    # 退出登录完成信号(无论服务器是否可达都会发出)
    logout_finished = Signal()

    def __init__(self, server_url: str, token: str):
        super().__init__()
        self.server_url = server_url
        self.token = token

    def run(self):
        client = network.ApiClient(base_url=self.server_url, token=self.token)
        try:
            client.logout()
        except Exception as e:
            logging.warning(f"退出登录请求失败(忽略): {e}")
        self.logout_finished.emit()


# 进程备注文件相关类（初始化、读取、修改）
class Init_AliasFile:
    """管理 ./config/alias.json，存储进程名到备注名的映射。
    格式：{"进程名.exe": "我的备注", ...}
    """
    def __init__(self):
        self.alias_file_path = pathlib.Path("./config/alias.json")
        self._init_file()
        self._alias_dict = self._load()

    def _init_file(self):
        """若文件不存在则创建，内容为空 JSON 对象"""
        if not self.alias_file_path.exists() or not self.alias_file_path.is_file():
            self.alias_file_path.parent.mkdir(parents=True, exist_ok=True)
            with open(self.alias_file_path, "w", encoding="utf-8") as f:
                json.dump({}, f, ensure_ascii=False, indent=4)

    def _load(self) -> dict:
        try:
            with open(self.alias_file_path, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return {}

    def _save(self):
        with open(self.alias_file_path, "w", encoding="utf-8") as f:
            json.dump(self._alias_dict, f, ensure_ascii=False, indent=4)

    def get_alias(self, proc_name: str) -> str:
        """返回备注名，若无备注则返回原进程名"""
        return self._alias_dict.get(proc_name, proc_name)

    def set_alias(self, proc_name: str, alias: str):
        """设置备注；若 alias 为空则删除备注"""
        if alias.strip() == "" or alias.strip() == proc_name:
            self._alias_dict.pop(proc_name, None)
        else:
            self._alias_dict[proc_name] = alias.strip()
        self._save()

    def has_alias(self, proc_name: str) -> bool:
        return proc_name in self._alias_dict


# 进程屏蔽文件相关类（初始化、读取、修改）
class Init_BlockedFile:
    """管理 ./config/blocked_processes.json，存储被屏蔽的进程名列表。
    被屏蔽的进程不会显示在主界面表格中，也不会被上传到服务器。
    格式：["process1.exe", "process2.exe", ...]
    """
    def __init__(self):
        self.blocked_file_path = pathlib.Path("./config/blocked_processes.json")
        self._lock = threading.Lock()
        self._init_file()
        self._blocked_set = self._load()

    def _init_file(self):
        """若文件不存在则创建，内容为空 JSON 数组"""
        if not self.blocked_file_path.exists() or not self.blocked_file_path.is_file():
            self.blocked_file_path.parent.mkdir(parents=True, exist_ok=True)
            with open(self.blocked_file_path, "w", encoding="utf-8") as f:
                json.dump([], f, ensure_ascii=False, indent=4)

    def _load(self) -> set:
        try:
            with open(self.blocked_file_path, "r", encoding="utf-8") as f:
                return set(json.load(f))
        except Exception:
            return set()

    def _save(self):
        with self._lock:
            with open(self.blocked_file_path, "w", encoding="utf-8") as f:
                json.dump(sorted(self._blocked_set), f, ensure_ascii=False, indent=4)

    def is_blocked(self, proc_name: str) -> bool:
        with self._lock:
            return proc_name in self._blocked_set

    def block(self, proc_name: str):
        with self._lock:
            self._blocked_set.add(proc_name)
        self._save()

    def unblock(self, proc_name: str):
        with self._lock:
            self._blocked_set.discard(proc_name)
        self._save()

    def get_all_blocked(self) -> list:
        with self._lock:
            return sorted(self._blocked_set)


# ==================== 密码加密与存储管理 ====================

class PasswordManager:
    """密码加密/解密工具。

    使用 Windows 机器唯一标识（MachineGuid）作为密钥种子派生加密密钥，
    确保加密后的密码文件无法在其他机器上解密。
    加密方式：SHA-256 派生密钥 + XOR 流密码 + Base64 编码。
    """

    @staticmethod
    def _get_machine_key() -> bytes:
        """从 Windows MachineGuid 派生 32 字节加密密钥"""
        try:
            import winreg
            reg_key = winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE,
                                     r"SOFTWARE\Microsoft\Cryptography")
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
        # XOR 流密码：每个字节与密钥字节（循环）异或
        ciphertext = bytes(data[i] ^ key[i % len(key)] for i in range(len(data)))
        return base64.b64encode(ciphertext).decode("utf-8")

    @staticmethod
    def decrypt(cipher_b64: str) -> str:
        """解密 Base64 编码的密文，返回明文字符串"""
        key = PasswordManager._get_machine_key()
        ciphertext = base64.b64decode(cipher_b64)
        data = bytes(ciphertext[i] ^ key[i % len(key)] for i in range(len(ciphertext)))
        return data.decode("utf-8")


# 密码文件路径
PASSWORD_FILE_PATH = pathlib.Path("./config/password.enc")


def save_encrypted_password(email: str, password: str):
    """将邮箱和加密后的密码保存到本地密码文件"""
    try:
        encrypted_pw = PasswordManager.encrypt(password)
        data = {"email": email, "password": encrypted_pw}
        PASSWORD_FILE_PATH.parent.mkdir(parents=True, exist_ok=True)
        with open(PASSWORD_FILE_PATH, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=4)
    except Exception as e:
        logging.error(f"保存加密密码失败: {e}")


def load_encrypted_password() -> tuple:
    """从本地密码文件读取邮箱和加密密码，返回 (email, password) 或 (None, None)"""
    try:
        if not PASSWORD_FILE_PATH.exists() or not PASSWORD_FILE_PATH.is_file():
            return None, None
        with open(PASSWORD_FILE_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)
        email = data.get("email", "")
        encrypted_pw = data.get("password", "")
        if not email or not encrypted_pw:
            return None, None
        password = PasswordManager.decrypt(encrypted_pw)
        return email, password
    except Exception as e:
        logging.error(f"读取加密密码失败: {e}")
        return None, None


def clear_encrypted_password():
    """删除本地密码文件"""
    try:
        if PASSWORD_FILE_PATH.exists():
            PASSWORD_FILE_PATH.unlink()
    except Exception as e:
        logging.error(f"删除密码文件失败: {e}")


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
        # 加锁防止上传线程与界面线程同时写文件导致损坏
        with thread_lock:
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

    # 服务器地址(上传数据的目标服务器，形如 http://localhost:8080)
    def get_server_url(self):
        server_url = self.config_dict.get("server_url", None)
        if server_url is None:
            self.set_server_url(network.DEFAULT_BASE_URL)
            return network.DEFAULT_BASE_URL
        return server_url
    def set_server_url(self, server_url:str):
        self.config_dict["server_url"] = server_url
        self.wirteback_config()

    # 登录邮箱
    def get_user_email(self):
        user_email = self.config_dict.get("user_email", None)
        if user_email is None:
            self.set_user_email("")
            return ""
        return user_email
    def set_user_email(self, user_email:str):
        self.config_dict["user_email"] = user_email
        self.wirteback_config()

    # 登录后获取的JWT token(用于每次上传的Authorization请求头)
    def get_token(self):
        token = self.config_dict.get("token", None)
        if token is None:
            self.set_token("")
            return ""
        return token
    def set_token(self, token:str):
        self.config_dict["token"] = token
        self.wirteback_config()

# 主窗口类
class MyMainWindow(QMainWindow, Ui_MainWindow):
    def __init__(self):
        super().__init__()
        """数据初始化区"""
        # 把存储当前时间放到这里，防止打包后的获取当天时间出现问题
        global current_date
        global config_File  # 创建配置文件类实例
        global alias_file   # 创建备注文件类实例
        global blocked_file # 创建进程屏蔽文件类实例
        global old_date_status  # 表示用户是否正处于查看历史信息的状态 true正在查看历史/flase没有查看历史
        global old_date_refrush_flag    # 用来标记主窗口是否已经刷新过
        config_File = Init_ConfigFile()
        alias_file = Init_AliasFile()
        blocked_file = Init_BlockedFile()
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
        # print(self.all_applications_dict)  #--del
        # 有false和字典数据两种状态。被用户双击选中日期后会变为字典数据。用户再次选择当天日期后，再变回false
        self.the_old_date_application_dict = {} #用于存储旧日期的数据。
        


        # 启动监控线程（后台线程只更新数据字典，不再操作 GUI）
        self.thread_windows_listening = threading.Thread(target=window_monitor, args=(self.tableWidget,self.all_applications_dict,self.the_old_date_application_dict))
        self.thread_windows_listening.daemon = True  # 主线程退出时自动结束
        self.thread_windows_listening.start()
        # 启动主线程定时器，每秒刷新表格（GUI 操作必须在主线程执行）
        self.refresh_timer = QTimer(self)
        self.refresh_timer.timeout.connect(self.refresh_table)
        self.refresh_timer.start(1000)
        # 启动自动保存json文件线程
        self.thread_auto_save = threading.Thread(target=auto_save_thread, args=(self.all_applications_dict,))
        self.thread_auto_save.daemon = True  # 主线程退出时自动结束
        self.thread_auto_save.start()
        # 启动数据上传线程(已登录时每秒上传一次数据)
        self.thread_upload = threading.Thread(target=data_upload_thread, args=(self.all_applications_dict,))
        self.thread_upload.daemon = True  # 主线程退出时自动结束
        self.thread_upload.start()
        # 启动全局键盘/鼠标输入监听器，采集键盘敲击、鼠标点击和移动距离
        start_input_listeners()

        # 上一次的上传阶段，用于在上传状态变化时向托盘发一次通知
        self._prev_upload_stage = None
        # 登录/退出请求进行中标记，防止重复点击或重复打开设置窗口造成多个后台任务
        self._login_in_progress = False
        self._logout_in_progress = False
        # 手动登录时暂存密码（登录成功后加密保存；自动登录时无此值）
        self._pending_password = None
        # 定时检查上传状态变化(如登录失效/网络中断)，变化时用托盘气泡提示
        self.upload_notify_timer = QTimer(self)
        self.upload_notify_timer.timeout.connect(self.check_upload_notification)
        self.upload_notify_timer.start(2000)

        # 程序启动时检测本地是否有加密保存的密码，若有则尝试自动登录
        QTimer.singleShot(500, self.try_auto_login)

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

        # 禁止直接在表格内编辑（双击时用自定义对话框代替）
        self.tableWidget.setEditTriggers(QTableWidget.NoEditTriggers)
        # 双击单元格 → 弹出备注编辑框
        self.tableWidget.cellDoubleClicked.connect(self.on_cell_double_clicked)
        # 右键单元格 → 弹出操作菜单（屏蔽进程等）
        self.tableWidget.setContextMenuPolicy(Qt.CustomContextMenu)
        self.tableWidget.customContextMenuRequested.connect(self.on_table_context_menu)


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
    

    # 主线程定时刷新表格（由 QTimer 每秒触发）
    # 后台线程 window_monitor 只更新数据字典，GUI 操作全部集中在此方法
    def refresh_table(self):
        global old_date_status, old_date_refrush_flag, ui_freeze
        # 若 UI 被冻结（如正在编辑备注对话框），跳过本次刷新
        if ui_freeze:
            return

        try:
            if old_date_status is not False:
                if old_date_refrush_flag is False:
                    self.tableWidget.setRowCount(0)
                    old_date_refrush_flag = True
                add_row(self.tableWidget, self.the_old_date_application_dict)
            else:
                if old_date_refrush_flag is False:
                    self.tableWidget.setRowCount(0)
                    old_date_refrush_flag = True
                add_row(self.tableWidget, self.all_applications_dict)
        except Exception as e:
            logging.error(f"refresh_table 出错: {str(e)}")


    # 双击表格单元格 → 在单元格内原地编辑备注（仅对标题列生效）
    # 不再使用 QInputDialog 模态对话框，彻底避免模态对话框阻塞事件循环导致卡顿
    def on_cell_double_clicked(self, row: int, column: int):
        global alias_file
        # 只处理第1列（进程名/标题列）
        if column != 1:
            return

        item = self.tableWidget.item(row, 1)
        if item is None:
            return

        # 取出真实进程名（存在 UserRole 中）
        proc_name = item.data(Qt.UserRole)
        if proc_name is None:
            proc_name = item.text()

        # 当前备注（若无备注则显示原进程名作为占位提示）
        current_alias = alias_file._alias_dict.get(proc_name, "")
        display_text = current_alias if current_alias else proc_name

        # 在单元格内嵌入 QLineEdit，原地编辑
        editor = QLineEdit(self.tableWidget)
        editor.setText(display_text)
        editor.setPlaceholderText("留空则清除备注")
        editor.selectAll()
        editor.setFocus()

        # 将 editor 放入单元格
        self.tableWidget.setCellWidget(row, 1, editor)

        # 编辑完成时保存（回车确认，Esc 取消）
        # 使用防重入标志，避免 returnPressed 和 editingFinished 同时触发
        _finished = [False]

        def commit_edit():
            if _finished[0]:
                return
            _finished[0] = True
            editor.blockSignals(True)  # 防止 removeCellWidget 触发 editingFinished

            text = editor.text().strip()
            alias_file.set_alias(proc_name, text)
            # 移除 editor，恢复为纯文本 QTableWidgetItem
            self.tableWidget.removeCellWidget(row, 1)
            display_name = alias_file.get_alias(proc_name)
            new_item = QTableWidgetItem(display_name)
            new_item.setData(Qt.UserRole, proc_name)
            new_item.setTextAlignment(Qt.AlignLeft | Qt.AlignVCenter)
            self.tableWidget.setItem(row, 1, new_item)

        def cancel_edit():
            if _finished[0]:
                return
            _finished[0] = True
            editor.blockSignals(True)
            # 移除 editor，恢复原 QTableWidgetItem
            self.tableWidget.removeCellWidget(row, 1)
            # item 是原来的 QTableWidgetItem，重新设置回去
            self.tableWidget.setItem(row, 1, item)

        editor.returnPressed.connect(commit_edit)

        # Esc 取消编辑：通过重写 keyPressEvent 无法直接做到（editor 是独立控件），
        # 改用 editingFinished 在失去焦点时自动提交（用户点击其他地方 = 确认）
        # 但需要防止 removeCellWidget 时二次触发
        editor.editingFinished.connect(commit_edit)

    # 右键表格单元格时弹出上下文菜单
    def on_table_context_menu(self, pos):
        """右键点击表格时弹出操作菜单，包含屏蔽进程等选项"""
        global blocked_file, _row_index_cache
        row = self.tableWidget.rowAt(pos.y())
        if row < 0:
            return
        item = self.tableWidget.item(row, 1)
        if item is None:
            return
        # 取出真实进程名
        proc_name = item.data(Qt.UserRole)
        if proc_name is None:
            proc_name = item.text()

        # 显示名称（有备注则显示备注名）
        display_name = alias_file.get_alias(proc_name)

        menu = QMenu(self.tableWidget)

        # 屏蔽此进程
        if not blocked_file.is_blocked(proc_name):
            block_action = menu.addAction(f"⛔ 屏蔽此进程：{display_name}")
        else:
            block_action = menu.addAction(f"✅ 已屏蔽：{display_name}")
            block_action.setEnabled(False)

        action = menu.exec_(self.tableWidget.viewport().mapToGlobal(pos))
        if action == block_action and not blocked_file.is_blocked(proc_name):
            self.block_process(proc_name)

    # 屏蔽指定进程
    def block_process(self, proc_name: str):
        """将指定进程加入屏蔽列表，立即从表格移除并停止上传。
        
        注意：进程仍在 all_applications_dict 中持续累积时间，
        只是不显示在表格中、不上传给服务器。
        """
        global blocked_file, _row_index_cache, old_date_refrush_flag
        blocked_file.block(proc_name)
        # 清空表格 + 缓存，强制完整重建
        self.tableWidget.setRowCount(0)
        _row_index_cache.clear()
        old_date_refrush_flag = False
        self.refresh_table()
        # 托盘气泡提示
        self.tray_icon.showMessage(
            "屏幕视奸器",
            f"已屏蔽进程：{proc_name}\n可在设置中解除屏蔽",
            QSystemTrayIcon.Information, 3000
        )

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
            self.settings_ui.label_3.setText("已开启")
        else:
            self.settings_ui.checkBox.setChecked(False)
            self.settings_ui.label_3.setText("已关闭")

        # 第二页：账号与数据上传设置
        self.init_account_setting()

        # 第三页：进程屏蔽管理（需先添加 page_3 到 stackedWidget）
        self._init_blocked_process_page()

    # 在设置窗口第二页搭建“账号与上传”表单(账号登录/退出、服务器地址、上传状态)
    def init_account_setting(self):
        page = self.settings_ui.page_2
        # 隐藏原“预留选项”页的占位文字
        self.settings_ui.label_2.hide()
        # 修改左侧菜单第二项名称
        list_item = self.settings_ui.listWidget.item(1)
        if list_item is not None:
            list_item.setText("账号与上传")

        layout = QVBoxLayout(page)
        layout.setContentsMargins(10, 10, 10, 10)
        layout.setSpacing(8)

        title_label = QLabel("登录后开始每秒上传数据", page)
        title_label.setStyleSheet("font-size:16px;font-weight:bold;")
        layout.addWidget(title_label)

        # 表单：服务器地址/邮箱/密码
        form_layout = QFormLayout()
        self.server_edit = QLineEdit(page)
        self.server_edit.setPlaceholderText(network.DEFAULT_BASE_URL)
        self.email_edit = QLineEdit(page)
        self.email_edit.setPlaceholderText("请输入登录邮箱")
        self.pass_edit = QLineEdit(page)
        self.pass_edit.setEchoMode(QLineEdit.Password)
        self.pass_edit.setPlaceholderText("请输入登录密码")
        form_layout.addRow("服务器地址:", self.server_edit)
        form_layout.addRow("邮箱:", self.email_edit)
        form_layout.addRow("密码:", self.pass_edit)
        layout.addLayout(form_layout)

        # 登录/退出按钮
        btn_row = QHBoxLayout()
        self.login_btn = QPushButton("登录并开始上传", page)
        self.logout_btn = QPushButton("退出登录", page)
        btn_row.addWidget(self.login_btn)
        btn_row.addWidget(self.logout_btn)
        layout.addLayout(btn_row)

        # 当前上传状态展示
        self.account_status_label = QLabel("", page)
        self.account_status_label.setWordWrap(True)
        layout.addWidget(self.account_status_label)

        hint_label = QLabel(
            "提示：登录后客户端会每秒向服务器上传一次本机运行中的应用及使用时长。\n"
            "服务器地址需与后端地址一致，例如 http://localhost:8080", page)
        hint_label.setWordWrap(True)
        hint_label.setStyleSheet("color:gray;")
        layout.addWidget(hint_label)
        layout.addStretch()

        # 事件绑定
        self.login_btn.clicked.connect(self.on_login_clicked)
        self.logout_btn.clicked.connect(self.on_logout_clicked)

        # 预填已保存的服务器地址与邮箱
        self.server_edit.setText(config_File.get_server_url())
        self.email_edit.setText(config_File.get_user_email())

        # 每秒刷新一次上传状态(上传线程每秒更新一次)
        self.account_timer = QTimer(self.settings_window)
        self.account_timer.timeout.connect(self.refresh_account_status)
        self.account_timer.start(1000)
        # 打开设置窗口时立即刷新一次状态
        self.refresh_account_status()

    # 根据上传线程的实时状态刷新“账号与上传”页展示
    def refresh_account_status(self):
        token = config_File.get_token()
        email = config_File.get_user_email()
        with upload_lock:
            stage = upload_stage
            stage_text = upload_status_text
            error_text = upload_last_error

        if token:
            self.login_btn.setEnabled(False)
            self.logout_btn.setEnabled(True)
            email_text = email if email else "未知邮箱"
            if stage == "ok":
                self.account_status_label.setText(f"已登录：{email_text}\n{stage_text}")
            elif stage == "error":
                self.account_status_label.setText(f"已登录：{email_text}\n{stage_text}：{error_text}")
            elif stage == "not_login":
                self.account_status_label.setText(f"已登录：{email_text}\n正在启动上传…")
            else:
                self.account_status_label.setText(f"已登录：{email_text}\n当前状态：{stage_text}")
        else:
            self.login_btn.setEnabled(True)
            self.logout_btn.setEnabled(False)
            if stage == "invalid":
                self.account_status_label.setText(f"登录已失效，请重新登录\n({error_text})")
            else:
                self.account_status_label.setText("未登录（登录后每秒自动上传数据）")

    # 在设置窗口添加第三页：进程屏蔽管理
    def _init_blocked_process_page(self):
        """在设置窗口的 stackedWidget 中创建第三页，展示被屏蔽的进程列表并支持解除屏蔽"""
        global blocked_file
        # 使用标志位防止重复初始化（多次打开设置窗口时只创建一次）
        if hasattr(self, "_blocked_page_inited") and self._blocked_page_inited:
            self._refresh_blocked_list()
            return

        # 创建第三页 widget
        page_3 = QWidget()
        self.settings_ui.stackedWidget.addWidget(page_3)  # index = 2

        # 新增第三项“进程屏蔽”（第二项已在 init_account_setting 中改名）
        item_3 = QListWidgetItem("进程屏蔽")
        item_3.setTextAlignment(Qt.AlignCenter)
        font = self.settings_ui.listWidget.font()
        font.setBold(True)
        font.setPointSize(12)
        item_3.setFont(font)
        self.settings_ui.listWidget.addItem(item_3)

        # 布局页面
        layout = QVBoxLayout(page_3)
        layout.setContentsMargins(10, 10, 10, 10)
        layout.setSpacing(8)

        title = QLabel("被屏蔽的进程", page_3)
        title.setStyleSheet("font-size:16px;font-weight:bold;")
        layout.addWidget(title)

        hint = QLabel(
            "以下进程已被屏蔽：不会显示在主界面表格中，也不会被上传到服务器。\n"
            "单击选中进程名，点击“解除屏蔽”即可恢复。", page_3)
        hint.setWordWrap(True)
        hint.setStyleSheet("color:gray;")
        layout.addWidget(hint)

        # 进程列表（单击选中进程名，点击"解除屏蔽"即可恢复）
        self.blocked_list_widget = QTableWidget(page_3)
        self.blocked_list_widget.setColumnCount(1)
        self.blocked_list_widget.setHorizontalHeaderLabels(["进程名"])
        self.blocked_list_widget.horizontalHeader().setSectionResizeMode(0, QHeaderView.Stretch)
        self.blocked_list_widget.verticalHeader().setVisible(False)
        self.blocked_list_widget.setEditTriggers(QTableWidget.NoEditTriggers)
        self.blocked_list_widget.setSelectionBehavior(QTableWidget.SelectRows)
        self.blocked_list_widget.setSelectionMode(QTableWidget.SingleSelection)
        layout.addWidget(self.blocked_list_widget)

        # 按钮行：解除屏蔽
        btn_row = QHBoxLayout()
        self.unblock_btn = QPushButton("解除屏蔽", page_3)
        self.refresh_blocked_btn = QPushButton("刷新列表", page_3)
        btn_row.addWidget(self.unblock_btn)
        btn_row.addWidget(self.refresh_blocked_btn)
        btn_row.addStretch()
        layout.addLayout(btn_row)

        layout.addStretch()

        # 绑定事件
        self.unblock_btn.clicked.connect(self._on_unblock_clicked)
        self.refresh_blocked_btn.clicked.connect(self._refresh_blocked_list)

        self._blocked_page_inited = True
        # 初次填充列表
        self._refresh_blocked_list()

    # 刷新被屏蔽进程列表
    def _refresh_blocked_list(self):
        """重新读取 blocked 列表并刷新表格"""
        global blocked_file
        blocked_list = blocked_file.get_all_blocked()
        self.blocked_list_widget.setRowCount(0)
        for i, proc_name in enumerate(blocked_list):
            self.blocked_list_widget.insertRow(i)
            # 进程名（点击选中行即可）
            display_name = alias_file.get_alias(proc_name)
            item = QTableWidgetItem(display_name)
            item.setData(Qt.UserRole, proc_name)
            item.setTextAlignment(Qt.AlignCenter)
            self.blocked_list_widget.setItem(i, 0, item)

    # 点击“解除屏蔽”
    def _on_unblock_clicked(self):
        """解除用户选中的进程的屏蔽状态"""
        global blocked_file, _row_index_cache
        selected_rows = self.blocked_list_widget.selectionModel().selectedRows()
        if not selected_rows:
            return
        unchecked_count = 0
        for index in selected_rows:
            row = index.row()
            item = self.blocked_list_widget.item(row, 0)
            if item is None:
                continue
            proc_name = item.data(Qt.UserRole)
            if proc_name:
                blocked_file.unblock(proc_name)
                unchecked_count += 1

        if unchecked_count > 0:
            # 清除行缓存，强制主表格完整重建（解除屏蔽的进程会重新出现）
            _row_index_cache.clear()
            self.tableWidget.setRowCount(0)
            self._refresh_blocked_list()
            self.refresh_table()
            self.tray_icon.showMessage(
                "屏幕视奸器",
                f"已解除 {unchecked_count} 个进程的屏蔽",
                QSystemTrayIcon.Information, 3000
            )
        else:
            # 没有选中任何进程，提示用户
            pass

    # 点击“登录并开始上传”
    def on_login_clicked(self):
        # 上一次登录请求仍在进行中则忽略本次点击
        if self._login_in_progress:
            self.account_status_label.setText("正在登录，请稍候…")
            return

        user_email = self.email_edit.text().strip()
        user_password = self.pass_edit.text()
        server_url = self.server_edit.text().strip() or network.DEFAULT_BASE_URL

        if not user_email or "@" not in user_email:
            self.account_status_label.setText("请输入正确的邮箱")
            return
        if not user_password:
            self.account_status_label.setText("请输入登录密码")
            return

        # 保存服务器地址(其余信息登录成功后再保存)
        config_File.set_server_url(server_url)
        self._login_in_progress = True
        self.login_btn.setEnabled(False)
        self.account_status_label.setText("正在登录，请稍候…")

        # 暂存密码，登录成功后用于本地加密保存（仅手动登录有此值）
        self._pending_password = user_password

        # 后台线程调用登录接口，避免网络等待卡住界面
        self.login_worker = LoginWorker(user_email, user_password, server_url)
        self.login_worker.login_success.connect(
            lambda token: self.on_login_success(user_email, server_url, token))
        self.login_worker.login_failed.connect(self.on_login_failed)
        self.login_worker.start()

    # 登录成功回调
    def on_login_success(self, user_email: str, server_url: str, token: str):
        self._login_in_progress = False
        # 保存登录信息到本地配置(上传线程每秒读取)
        config_File.set_server_url(server_url)
        config_File.set_user_email(user_email)
        config_File.set_token(token)

        # 手动登录成功：将密码加密保存到本地，供下次启动时自动登录
        pending_pw = getattr(self, "_pending_password", None)
        if pending_pw:
            save_encrypted_password(user_email, pending_pw)
            self._pending_password = None

        self.pass_edit.clear()
        self.account_status_label.setText("登录成功，开始每秒上传数据…")
        # 托盘气泡提示
        self.tray_icon.showMessage("屏幕视奸器", "登录成功，开始每秒上传数据", QSystemTrayIcon.Information, 3000)
        self.refresh_account_status()

    # 登录失败回调
    def on_login_failed(self, error_message: str):
        self._login_in_progress = False
        self.login_btn.setEnabled(True)
        self.account_status_label.setText(f"登录失败：{error_message}")
        # 托盘气泡提示
        self.tray_icon.showMessage("屏幕视奸器", f"登录失败：{error_message}", QSystemTrayIcon.Warning, 3000)

    # 程序启动时尝试自动登录（读取本地加密保存的密码）
    def try_auto_login(self):
        """读取本地加密密码文件，若存在则尝试自动登录"""
        email, password = load_encrypted_password()
        if not email or not password:
            return  # 没有保存的密码，跳过自动登录

        server_url = config_File.get_server_url()
        logging.info("检测到本地加密密码，尝试自动登录…")

        self._pending_password = None  # 自动登录不保存密码，避免覆盖已有凭据
        self._login_in_progress = True

        # 后台线程调用登录接口
        self.login_worker = LoginWorker(email, password, server_url)
        self.login_worker.login_success.connect(
            lambda token: self._on_auto_login_success(email, server_url, token))
        self.login_worker.login_failed.connect(self._on_auto_login_failed)
        self.login_worker.start()

    # 自动登录成功回调
    def _on_auto_login_success(self, user_email: str, server_url: str, token: str):
        self._login_in_progress = False
        # 保存登录信息到本地配置(上传线程每秒读取)
        config_File.set_server_url(server_url)
        config_File.set_user_email(user_email)
        config_File.set_token(token)
        logging.info("自动登录成功")
        # 托盘气泡提示
        self.tray_icon.showMessage(
            "屏幕视奸器", "自动登录成功，开始每秒上传数据",
            QSystemTrayIcon.Information, 3000)

    # 自动登录失败回调
    def _on_auto_login_failed(self, error_message: str):
        self._login_in_progress = False
        logging.info(f"自动登录失败: {error_message}")
        # 自动登录失败不弹托盘提示，避免频繁打扰用户
        # 不清除密码文件，用户可在设置中手动点击登录修正

    # 点击“退出登录”
    def on_logout_clicked(self):
        # 上一次退出请求仍在进行中则忽略本次点击
        if self._logout_in_progress:
            self.account_status_label.setText("正在退出登录…")
            return

        token = config_File.get_token()
        server_url = config_File.get_server_url()

        self._logout_in_progress = True
        self.logout_btn.setEnabled(False)
        self.login_btn.setEnabled(False)
        self.account_status_label.setText("正在退出登录…")

        # 后台线程通知服务器使token失效；无论成败都清除本地登录信息
        self.logout_worker = LogoutWorker(server_url, token)
        self.logout_worker.logout_finished.connect(self.on_logout_finished)
        self.logout_worker.start()

    # 退出登录完成回调(通知服务器失败时也照常清除本地信息)
    def on_logout_finished(self):
        self._logout_in_progress = False
        config_File.set_token("")
        config_File.set_user_email("")
        # 退出登录时同时清除本地加密保存的密码
        clear_encrypted_password()
        with upload_lock:
            stage = upload_stage
        # 若此前正处于“登录失效”状态，需要复位阶段标记便于重新登录
        if stage == "invalid":
            set_upload_stage("not_login", "未登录", "")
        self.tray_icon.showMessage("屏幕视奸器", "已退出登录，停止上传数据", QSystemTrayIcon.Information, 3000)
        self.refresh_account_status()

    # 定时检查上传状态变化，仅在状态切换时向托盘发一次通知，避免反复弹窗
    def check_upload_notification(self):
        with upload_lock:
            stage = upload_stage
            error_text = upload_last_error
        prev_stage = self._prev_upload_stage
        self._prev_upload_stage = stage

        # 首次运行(prev为None)不通知
        if prev_stage is None:
            return
        if prev_stage == stage:
            return

        if stage == "invalid":
            # 已登录状态变为登录失效(如token过期)
            self.tray_icon.showMessage("屏幕视奸器", "登录已失效，请打开设置重新登录",
                                       QSystemTrayIcon.Warning, 3000)
        elif stage == "error" and prev_stage in ("ok", "not_login"):
            # 首次上传失败(网络中断等)
            self.tray_icon.showMessage("屏幕视奸器", f"数据上传失败：{error_text}",
                                       QSystemTrayIcon.Warning, 3000)
        elif stage == "ok" and prev_stage == "error":
            # 网络恢复
            self.tray_icon.showMessage("屏幕视奸器", "网络已恢复，继续上传数据",
                                       QSystemTrayIcon.Information, 3000)

    # 初始化数据，若存在当天数据则读取，而不是从空开始
    def init_data(self):
        global keyboard_count, mouse_click_count, mouse_distance_px, run_duration
        date_str = time.strftime("%Y-%m-%d", time.localtime()) # 获取当前日期字符串
        file_name = f"./history_data/data_{date_str}.json"
        p = pathlib.Path(file_name)
        if p.exists() and p.is_file():
            # 读取json文件
            with open(file_name, "r", encoding="utf-8") as file:
                simple_data = json.load(file)
                # 还原输入统计计数器（兼容无 _statistics 键的旧格式文件）
                stats = simple_data.pop("_statistics", {})
                with input_lock:
                    keyboard_count = int(stats.get("keyboardCount", 0))
                    mouse_click_count = int(stats.get("mouseClickCount", 0))
                    # mouseDistance 存储的是米，换算回像素存入 mouse_distance_px
                    mouse_distance_px = float(stats.get("mouseDistance", 0.0)) / PIXEL_TO_METER
                # 还原客户端程序今日运行时长(秒)，兼容旧格式文件缺失该键的情况
                run_duration = int(stats.get("totalDuration", 0))
                # 转换为程序内部使用的复杂格式
                self.all_applications_dict = {}
                for title, use_time in simple_data.items():
                    # 由于保存时只保存了title和use_time，pid需要重新获取
                    # 因为是历史记录，pid没有意义，设为0即可
                    self.all_applications_dict[title] = {
                        "pid": 0,
                        "title": title,
                        "use_time": use_time
                    }
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
        # 重置屏蔽页标志位（每次重新创建窗口，旧的 page_3 已被销毁）
        self._blocked_page_inited = False
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
            self.settings_ui.label_3.setText("已开启")  #将文字重新设置为已开启
        else:
            # 未选中“开机自启动”
            self.functions.unset_startup()  #执行“取消开机自启动”
            config_File.set_auto_setup(False)   #写回配置文件
            self.settings_ui.label_3.setText("已关闭")  #将文字重新设置为已关闭
    
    # 主窗口页-表格排序动作函数
    def sort_change(self,target_type:str):
        global config_File, _row_index_cache
        self.tableWidget.setRowCount(0)
        _row_index_cache.clear()
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
        global _row_index_cache
        old_date_refrush_flag = False
        _row_index_cache.clear()
        
        if item.text() == f"data_{current_date}.json":
            # 点击当天日期后，将查看历史数据功能关闭
            old_date_status = False
        else:
            old_date_status = True # 开启查看历史信息模式
            with open(f"./history_data/{item.text()}","r",encoding="utf-8") as file:
                simple_data = json.load(file)
                # 转换为程序内部使用的复杂格式
                self.the_old_date_application_dict.clear()#不能用self.the_old_date_application_dict = {}的形式，在线程传输时会出问题
                for title, use_time in simple_data.items():
                    self.the_old_date_application_dict[title] = {
                        "pid": 0,
                        "title": title,
                        "use_time": use_time
                    }
                
        
        
    



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
