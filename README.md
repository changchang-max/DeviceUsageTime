# >>软件使用时长监控器<<
>该软件仅适用于Windows系统
# 下载
exe可执行文件在`./dist`目录中  **<--请优先选择这个**
**或**
蓝奏云下载链接(dist内为最新版本，蓝奏云可能更新不及时)
https://wwfm.lanzoub.com/iMigp3ao0tah
密码:f0cp

![主页面](https://primordialimg.publicnote.top/app/thumb.php?img=/i/2026/01/14/f7w93k.png)
![历史数据](https://primordialimg.publicnote.top/app/thumb.php?img=/i/2026/01/14/f7w7rq.png)
![设置页](https://primordialimg.publicnote.top/app/thumb.php?img=/i/2026/01/14/f7wbol.png)
![排序](https://primordialimg.publicnote.top/app/thumb.php?img=/i/2026/01/14/f7wbol.png)
![最小化](https://primordialimg.publicnote.top/app/thumb.php?img=/i/2026/01/14/f7w8l9.png)

# 环境部署
>前提：已安装conda环境。若未安装，请前往[我的博客](http://primordialblog.publicnote.top/)->环境配置->Windows安装miniconda
>注：python版本必须在3.10以上
1. 创建conda环境并激活
```bash
conda create --name DeviceUsageTime python=3.10
conda activate DeviceUsageTime
```
2. `cd`进入到`DeviceUsageTime`目录中
```bash
cd DeviceUsageTime
```
3. 从文件安装依赖库
```bash
pip install -r package.txt
```
4. 运行程序
```bash
python main.py
```

# 文件说明：
- `dist/`:存放exe可执行文件的目录
- `images.py`:存放图片对应base64编码的文件
- `main.py`:程序的入口
- `mainWindow.ui`:使用`PyQt5Designer`设计UI时所产生的ui文件
- `mytools.py`:工具包
- `package.txt`:包含了环境所需的全部Python库
- `ui_mainWindow.py`:`PyQt5Designer`设计UI时所产生的py文件，也是该程序的大体布局信息 **（非全部）**

# 更新计划
1.❌️如果同时多点了，打开两个程序可能会冲突。：只允许同时存在一个程序
2.✅️拉起线程每60s保存一次字典。
3.✅️在点击关闭时保存一次字典，阻塞，该线程结束后才可正常关闭
4.✅️每次启动程序先检查有没有json文件，如果有就读取，没有就正常运行
5.✅️开机自启动
6.✅️在桌面生成快捷方式--在安装时可自动生成
7.✅️同一个进程打开两个时，会每秒时长+2.改为+1，即对字典去重
8.✅️表格内容居中
9.✅️记住开机自启动的状态。json文件？并每次启动程序时读取开机自启动的状态。在勾选与取消勾选的时候也要改变文件中的开机自启动的状态
10.有时鼠标滚轮滚动过快会导致内部大量 widgets 重绘，进而引发程序崩溃。
如果数据量大、滚动多，建议改用：
✔ QTableView + QStandardItemModel
或
✔ QTableView + 自定义 Model（MVC模式）

它们更稳定、更快。
11.在菜单栏增加“打开配置文件目录”功能
12.✅️记住状态：用户选择的“排序方式”状态，和“开机自启”状态的显示
13.更改获取线程逻辑，改为根据pid获取，这样就不会出现每个窗口都重新计数的情况
14.增加“总在线时长”统计
15.✅️优化UI，引用开源主题？
16.用户自定义样式功能，如自定义字体大小、禁用样式等
17.自动获取最新版本
18.✅️增加一个安装程序
19.引进了日志功能--差个保存为文件功能（logging模块）
20.✅️增加查看历史数据功能
21.操作提示
22.用户自定义过滤指定窗口名

bug!!:
✅️开机自启动那个在注册表里的路径好像不太对
✅️当连续开着跨过12点时，会在新的一天生成一个接着昨天时长的文件，原因是字典没有清空

新的更改：
1.引入了qt_material库并应用其主题
2.禁止主窗口拉伸



# 给我提建议/bug
e-mail:primordial@qq.com
# 我的博客（有很多笔记和配置攻略~）
[传送门](http://primordialblog.publicnote.top/)
# 我的B站
[传送门](https://space.bilibili.com/1587827517)