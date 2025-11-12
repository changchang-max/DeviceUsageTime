>该软件仅适用于Windows系统
# 下载
exe可执行文件在`./dist`目录中  **<--请优先选择这个**
**或**
蓝奏云下载链接(dist内为最新版本，蓝奏云可能更新不及时)
https://wwfm.lanzoub.com/iMigp3ao0tah
密码:f0cp

# 环境部署
>前提：已安装conda环境。若未安装，请前往[我的博客](http://primordialblog.publicnote.top/)->环境配置->Windows安装miniconda
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
- [x] 将数据保存在本地json文件中
- [x] 增加开机自启动选项
- [ ] 增加显示过往数据功能
- [ ] 增加按软件使用时长排序功能