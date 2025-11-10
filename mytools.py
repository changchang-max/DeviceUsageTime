# 自定义工具包
import smtplib
from email.mime.text import MIMEText
import random
from datetime import datetime
import time

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
def add_str_time(str_time:str,second:int):
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



if __name__ == "__main__":
    if not get_fileExtensionName("aaa"):
        print("不存在.")
    pass