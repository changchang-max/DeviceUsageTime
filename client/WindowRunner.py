import sys

from PySide2.QtWidgets import QApplication, QMainWindow
from ui_settings import Ui_Settings

def on_click(item):
    print("Clicked"+item.text())    

if "__main__" == __name__:
    app = QApplication(sys.argv)   # 创建应用对象
    MainWindow = QMainWindow()     # 创建主窗口实例

    
    ui = Ui_Settings()           # 创建 UI 实例
    ui.setupUi(MainWindow)         # 载入 UI 到主窗口

    # for i in range(5):
    #     ui.listWidget.addItem("item"+str(i))
        # ui.listWidget.item(i).clicked.connect(on_click)
    # ui.listWidget.itemDoubleClicked.connect(on_click)


    

    #ui.pushButton.clicked.connect(MainWindow.close)
    
    # ui.listWidget.setCurrentRow(0)  # 默认选中第一个菜单项
    # 手动修改self.checkBox.setEnabled(True)
    # Settings.setEnabled(True)

    MainWindow.show()              # 显示窗口
    sys.exit(app.exec_())          # 进入应用循环