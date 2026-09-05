import requests
import json
from datetime import datetime
import time

class DataUploadTester:
    def __init__(self, base_url="http://localhost:8080", token=None):
        self.base_url = base_url
        self.token = token
        self.headers = {
            "Content-Type": "application/json"
        }
        if token:
            self.headers["Authorization"] = f"Bearer {token}"
    
    def login(self, email, password):
        """登录获取token"""
        url = f"{self.base_url}/api/auth/login"
        data = {
            "user_email": email,
            "user_password": password
        }
        
        try:
            response = requests.post(url, json=data, headers={"Content-Type": "application/json"})
            response.raise_for_status()
            result = response.json()
            
            
            if result.get("code") == 200 and "data" in result:
                self.token = result["data"]
                self.headers["Authorization"] = f"Bearer {self.token}"
                print(f"✅ 登录成功! Token: {self.token[:20]}...")
                return True
            else:
                print(f"❌ 登录失败: {result.get('message')}")
                return False
        except Exception as e:
            print(f"❌ 登录异常: {e}")
            return False
    
    def upload_data(self, applications=None, statistics=None, upload_date=None):
        """上传数据"""
        url = f"{self.base_url}/api/data/upload"
        
        # 构建默认数据
        if applications is None:
            applications = [
                {
                    "name": "Chrome",
                    "windowTitle": "Google搜索",
                    "duration": 3600,
                    "isActive": True
                },
                {
                    "name": "VSCode",
                    "windowTitle": "main.py",
                    "duration": 1800,
                    "isActive": False
                }
            ]
        
        if statistics is None:
            statistics = {
                "keyboardCount": 1250,
                "mouseClickCount": 856,
                "mouseDistance": 23.01
            }
        
        # 构建 timestamp：日期使用用户输入，时间使用本地当前时间，附带本地时区偏移
        now = datetime.now().astimezone()
        tz_offset = now.strftime("%z")  # 例如 +0800
        tz_offset = f"{tz_offset[:3]}:{tz_offset[3:]}"  # 转为 +08:00
        if upload_date:
            timestamp = f"{upload_date}T{now.strftime('%H:%M:%S.%f')[:-3]}{tz_offset}"
        else:
            timestamp = f"{now.strftime('%Y-%m-%dT%H:%M:%S.%f')[:-3]}{tz_offset}"

        data = {
            "timestamp": timestamp,
            "applications": applications,
            "statistics": statistics
        }
        
        try:
            response = requests.post(url, json=data, headers=self.headers)
            response.raise_for_status()
            result = response.json()
            
            if result.get("code") == 200:
                print(f"✅ 数据上传成功!")
                print(f"   响应: {json.dumps(result, ensure_ascii=False, indent=2)}")
                return True
            else:
                print(f"❌ 数据上传失败: {result.get('message')}")
                print(f"   详情: {json.dumps(result, ensure_ascii=False, indent=2)}")
                return False
        except requests.exceptions.HTTPError as e:
            print(f"❌ HTTP错误 {e.response.status_code}: {e.response.text}")
            return False
        except Exception as e:
            print(f"❌ 上传异常: {e}")
            return False
    
    def test_scenarios(self, upload_date=None):
        """测试各种场景"""
        print("\n" + "="*60)
        print("开始测试数据上传接口")
        if upload_date:
            print(f"上传日期: {upload_date}")
        print("="*60)
        
        # 场景1: 正常上传
        print("\n【场景1】正常上传数据")
        self.upload_data(upload_date=upload_date)
        time.sleep(1)
        
        # 场景2: 空applications列表
        print("\n【场景2】空applications列表")
        self.upload_data(applications=[], statistics={
            "keyboardCount": 100,
            "mouseClickCount": 50,
            "mouseDistance": 5.5
        }, upload_date=upload_date)
        time.sleep(1)
        
        # 场景3: statistics为None
        print("\n【场景3】statistics为None")
        self.upload_data(
            applications=[{
                "name": "WeChat",
                "windowTitle": "微信",
                "duration": 600,
                "isActive": True
            }],
            statistics=None,
            upload_date=upload_date
        )
        time.sleep(1)
        
        # 场景4: 多个应用
        print("\n【场景4】多个应用数据")
        self.upload_data(
            applications=[
                {"name": "Chrome", "windowTitle": "YouTube", "duration": 7200, "isActive": False},
                {"name": "VSCode", "windowTitle": "Python", "duration": 5400, "isActive": True},
                {"name": "WeChat", "windowTitle": "聊天", "duration": 1200, "isActive": False},
                {"name": "QQ", "windowTitle": "群聊", "duration": 900, "isActive": False}
            ],
            statistics={
                "keyboardCount": 5000,
                "mouseClickCount": 3000,
                "mouseDistance": 150.75
            },
            upload_date=upload_date
        )
        
        print("\n" + "="*60)
        print("测试完成!")
        print("="*60)
    
    def continuous_upload(self, interval=1, count=10, upload_date=None):
        """持续上传数据(模拟客户端每秒上传)"""
        print(f"\n开始持续上传测试 (间隔:{interval}秒, 次数:{count})")
        if upload_date:
            print(f"上传日期: {upload_date}")
        print("-" * 60)
        
        for i in range(count):
            print(f"\n第 {i+1}/{count} 次上传:")
            
            # 模拟动态数据
            applications = [
                {
                    "name": "Chrome",
                    "windowTitle": f"网页浏览-{i+1}",
                    "duration": 3600 + i * 10,
                    "isActive": i % 2 == 0
                },
                {
                    "name": "VSCode",
                    "windowTitle": "test.py",
                    "duration": 1800 + i * 5,
                    "isActive": i % 2 == 1
                }
            ]
            
            statistics = {
                "keyboardCount": 1000 + i * 50,
                "mouseClickCount": 500 + i * 20,
                "mouseDistance": 20.0 + i * 2.5
            }
            
            self.upload_data(applications, statistics, upload_date=upload_date)
            
            if i < count - 1:
                time.sleep(interval)
        
        print("\n持续上传测试完成!")


def get_upload_date():
    """获取用户输入的上传日期，格式 YYYY-MM-DD，直接回车使用本地今日日期"""
    today = datetime.now().strftime("%Y-%m-%d")
    while True:
        date_input = input(f"请输入上传数据的日期 (格式: YYYY-MM-DD，直接回车使用今天 {today}): ").strip()
        if not date_input:
            print(f"使用今天日期: {today}")
            return today
        try:
            datetime.strptime(date_input, "%Y-%m-%d")
            return date_input
        except ValueError:
            print("❌ 日期格式错误，请按 YYYY-MM-DD 格式输入，例如: 2026-09-04")


def main():
    print("=" * 60)
    print("数据上传接口测试程序")
    print("=" * 60)
    
    # 配置
    BASE_URL = "http://localhost:8080"
    
    # 创建测试器
    tester = DataUploadTester(base_url=BASE_URL)
    
    # 选择测试模式
    print("\n请选择测试模式:")
    print("1. 使用已有Token直接测试")
    print("2. 先登录获取Token再测试")
    print("3. 持续上传测试(模拟客户端)")
    
    choice = input("\n请输入选项 (1/2/3): ").strip()
    
    if choice == "1":
        token = input("请输入Token: ").strip()
        tester.token = token
        tester.headers["Authorization"] = f"Bearer {token}"
        upload_date = get_upload_date()
        tester.test_scenarios(upload_date=upload_date)
        
    elif choice == "2":
        email = input("请输入邮箱: ").strip()
        password = input("请输入密码: ").strip()
        
        if tester.login(email, password):
            upload_date = get_upload_date()
            tester.test_scenarios(upload_date=upload_date)
        else:
            print("登录失败，无法继续测试")
            
    elif choice == "3":
        # 先登录
        email = input("请输入邮箱: ").strip()
        password = input("请输入密码: ").strip()
        
        if tester.login(email, password):
            upload_date = get_upload_date()
            interval = input("上传间隔(秒,默认1): ").strip() or "1"
            count = input("上传次数(默认10): ").strip() or "10"
            
            tester.continuous_upload(int(interval), int(count), upload_date=upload_date)
        else:
            print("登录失败，无法继续测试")
    else:
        print("无效的选项")


if __name__ == "__main__":
    main()
