# -*- coding: utf-8 -*-

################################################################################
## Form generated from reading UI file 'history_data_windowyhBMHR.ui'
##
## Created by: Qt User Interface Compiler version 5.14.1
##
## WARNING! All changes made in this file will be lost when recompiling UI file!
################################################################################

from PySide2.QtCore import (QCoreApplication, QMetaObject, QObject, QPoint,
    QRect, QSize, QUrl, Qt)
from PySide2.QtGui import (QBrush, QColor, QConicalGradient, QCursor, QFont,
    QFontDatabase, QIcon, QLinearGradient, QPalette, QPainter, QPixmap,
    QRadialGradient)
from PySide2.QtWidgets import *


class Ui_history_data_window(object):
    def setupUi(self, history_data_window):
        if history_data_window.objectName():
            history_data_window.setObjectName(u"history_data_window")
        history_data_window.resize(1000, 700)
        sizePolicy = QSizePolicy(QSizePolicy.Fixed, QSizePolicy.Fixed)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(history_data_window.sizePolicy().hasHeightForWidth())
        history_data_window.setSizePolicy(sizePolicy)
        self.verticalLayoutWidget = QWidget(history_data_window)
        self.verticalLayoutWidget.setObjectName(u"verticalLayoutWidget")
        self.verticalLayoutWidget.setGeometry(QRect(90, 50, 821, 591))
        self.verticalLayout = QVBoxLayout(self.verticalLayoutWidget)
        self.verticalLayout.setObjectName(u"verticalLayout")
        self.verticalLayout.setContentsMargins(0, 0, 0, 0)
        self.label = QLabel(self.verticalLayoutWidget)
        self.label.setObjectName(u"label")
        font = QFont()
        font.setPointSize(20)
        self.label.setFont(font)
        self.label.setFocusPolicy(Qt.NoFocus)
        self.label.setTextFormat(Qt.AutoText)

        self.verticalLayout.addWidget(self.label, 0, Qt.AlignHCenter)

        self.listWidget = QListWidget(self.verticalLayoutWidget)
        self.listWidget.setObjectName(u"listWidget")

        self.verticalLayout.addWidget(self.listWidget)


        self.retranslateUi(history_data_window)

        QMetaObject.connectSlotsByName(history_data_window)
    # setupUi

    def retranslateUi(self, history_data_window):
        history_data_window.setWindowTitle(QCoreApplication.translate("history_data_window", u"\u5386\u53f2\u8bb0\u5f55\u770b\u677f", None))
        self.label.setText(QCoreApplication.translate("history_data_window", u"\u5386\u53f2\u8bb0\u5f55", None))
    # retranslateUi

