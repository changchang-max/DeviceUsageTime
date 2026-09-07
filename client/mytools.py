# 自定义工具包
import smtplib
from email.mime.text import MIMEText
import random
import time
import winreg
import sys
import pathlib
import os

# 提前定义一些日志标志，方便打印
info_error = "[ERROR]"
info_warning = "[WARNING]"
info_debug = "[DEBUG]"
info_info = "[INFO]"


# 邮件发送函数
# 发件人，授权码，收件人，主题，正文
def send_email(sender:str,passcode:str,receiver:str,subject:str,content:str):
    # 创建邮件对象
    message = MIMEText(content,"plain","utf-8")
    message['From'] = sender
    message['To'] = receiver
    message['Subject'] = subject

    try:
        # 新建对象，连接邮件服务器
        smtp_SSL = smtplib.SMTP_SSL("smtp.qq.com",465)#使用ssl加密通道
        smtp_SSL.login(sender,passcode)

        #发送邮件
        smtp_SSL.send_message(message)
        print(f"To:{receiver} 验证码发送成功")
    except smtplib.SMTPException as e:
        print(f"To:{receiver} 验证码发送失败。错误信息:{e}")
    
    # 测试函数：send_email("UserCodeSender@qq.com","qnzfhtvvxvykdbia","2774118934@qq.com","PublicNote验证码","376248")

# 生成6位数验证码
def create_passcode():
    passcode = str(random.randint(100000,999999))

    # 测试函数：print(create_passcode())
    return passcode

# 获取当前时分秒
def hour() -> str:
    # #返回值示例：23:04:20
    nowtime = time.strftime("%H:%M:%S",time.localtime())
    return nowtime

# 计算两个时分秒的差值的绝对值
def time_defference(time1:str,time2:str) -> int:
    # time1和time2应该是由自定义的00:00:00格式的时间字符串
    time_tuple1 = time.strptime(time1,"%H:%M:%S")
    time_tuple2 = time.strptime(time2,"%H:%M:%S")
    
    # 转换为时间戳
    timestamp1 = time.mktime(time_tuple1)
    timestamp2 = time.mktime(time_tuple2)

    # 获取差值秒数
    diff_seconds = timestamp1-timestamp2

    if(diff_seconds<0):
        return int(-diff_seconds)
    else:
        return int(diff_seconds)
    # 测试代码：print(time_defference("00:23:57","00:29:34"))

# 获取文件后缀名
def get_fileExtensionName(file_name:str):
    # 判断文件名里是否含有"."
    if "." in file_name:
        # 含有，则以.为分隔，把最后一个字符串返回
        ExtensionName = file_name.split(".")[-1]
        return ExtensionName
    else:
        # 没有.则返回None
        return None
    # 测试代码：print(get_fileExtensionName("abc.aoe.lll.txt"))

# 返回由秒转化成的时间字符串
def get_strtime(second:int) -> str: 
    return time.strftime("%H:%M:%S",time.gmtime(second))

# 可传入完整的时间字符串或时分秒，返回加上second秒后的时间字符串
def add_str_time(str_time:str,second:int) -> str:
    # 可能传入完整的时间字符串或仅时分秒
    try:
        # 尝试解析完整的时间字符串
        time_tuple = time.strptime(str_time, "%Y-%m-%d %H:%M:%S")
        time_stamp = time.mktime(time_tuple)
        time_stamp+=second
        return time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(time_stamp))

    except:
        hms = str_time[-8:]
        time_tuple = time.strptime(f'2011-09-11 {hms}', "%Y-%m-%d %H:%M:%S")
        time_stamp = time.mktime(time_tuple)
        time_stamp+=second
        return time.strftime("%H:%M:%S", time.localtime(time_stamp))
    
    # 测试代码：print(add_str_time("2011-09-11 08:40:22",3600000))
    # 测试代码：print(add_str_time("08:40:22",10))

# 获取当前文件的绝对路径，兼容打包和源码运行
def resource_path() -> str:
    """获取资源文件的绝对路径，兼容打包和源码运行"""
    
    if getattr(sys, 'frozen', False):
        # 被 PyInstaller 打包后的 exe 文件
        current_file = pathlib.Path(sys.executable)
    else:
        # 普通 Python 源码运行时
        current_file = pathlib.Path(__file__)
    return str(current_file)
    # print("当前文件名：", current_file.name)
    # print("当前完整路径：", current_file)

# 将自身设置开机自启动,参数传要写入注册表的键名
def set_startup(app_name:str):
    # 获取当前文件的真是路径
    p = pathlib.Path(resource_path())
    key_path = r"Software\Microsoft\Windows\CurrentVersion\Run"
    
    key = winreg.OpenKey(winreg.HKEY_CURRENT_USER,key_path,0,winreg.KEY_SET_VALUE)
    winreg.SetValueEx(key,app_name,0,winreg.REG_SZ,f'"{str(p)}"')
    
    # 关闭句柄
    winreg.CloseKey(key)

# 将自身取消开机自启动,参数传要删除的注册表的键名
def unset_startup(app_name:str):
    
    key_path = r"Software\Microsoft\Windows\CurrentVersion\Run"
    
    key = winreg.OpenKey(winreg.HKEY_CURRENT_USER,key_path,0,winreg.KEY_SET_VALUE)
    try:
        winreg.DeleteValue(key,app_name)
    except FileNotFoundError:
        pass
    except:
        with open("error_log.txt","a",encoding="utf-8") as f:
            f.write("取消开机自启动时出现未知错误\n")
    # 关闭句柄
    winreg.CloseKey(key)

# 将指定路径设为开机自启动
def add_to_startup(name, exe_path=None):
    if exe_path is None:
        exe_path = os.path.abspath(__file__)  # 当前脚本路径

    key = r"Software\Microsoft\Windows\CurrentVersion\Run"
    reg_key = winreg.OpenKey(winreg.HKEY_CURRENT_USER, key, 0, winreg.KEY_SET_VALUE)
    winreg.SetValueEx(reg_key, name, 0, winreg.REG_SZ, exe_path)
    winreg.CloseKey(reg_key)
    print(f"已设置 {name} 为开机自启：{exe_path}")

    # 使用示例
    # add_to_startup("MyApp", r"C:\Path\To\your_program.exe")

# 移除开机自启动
def remove_from_startup(name):
    key = r"Software\Microsoft\Windows\CurrentVersion\Run"
    reg_key = winreg.OpenKey(winreg.HKEY_CURRENT_USER, key, 0, winreg.KEY_ALL_ACCESS)
    try:
        winreg.DeleteValue(reg_key, name)
        print(f"已移除 {name} 的开机自启设置")
    except FileNotFoundError:
        print("该启动项不存在")
    winreg.CloseKey(reg_key)

# 获取当前屏幕最顶端(前台)窗口对应的进程名与窗口标题
# 返回值：(进程名, 窗口标题)，失败或无前台窗口时返回 (None, None)
def get_foreground_window_info():
    # 仅在Windows下可用，且依赖psutil获取进程名，因此延迟导入避免模块间耦合
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

        # 获取该窗口所属进程的PID
        pid = ctypes.c_ulong()
        user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))

        # 获取窗口标题(先取长度再取内容，避免中文截断)
        title_length = user32.GetWindowTextLengthW(hwnd)
        title_buffer = ctypes.create_unicode_buffer(title_length + 1)
        user32.GetWindowTextW(hwnd, title_buffer, title_length + 1)
        window_title = title_buffer.value.strip()

        # 由PID得到进程名(与psutil扫描结果格式一致，如 xxx.exe)
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
    if not get_fileExtensionName("aaa"):
        print("不存在.")
    pass