# -*- coding: utf-8 -*-

################################################################################
## Form generated from reading UI file 'exit_windowejuavt.ui'
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


class Ui_Exit(object):
    def setupUi(self, Exit):
        if Exit.objectName():
            Exit.setObjectName(u"Exit")
        Exit.resize(300, 172)
        self.label = QLabel(Exit)
        self.label.setObjectName(u"label")
        self.label.setGeometry(QRect(80, 40, 211, 41))
        sizePolicy = QSizePolicy(QSizePolicy.Preferred, QSizePolicy.Preferred)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(self.label.sizePolicy().hasHeightForWidth())
        self.label.setSizePolicy(sizePolicy)
        font = QFont()
        font.setPointSize(13)
        self.label.setFont(font)
        self.pushButton = QPushButton(Exit)
        self.pushButton.setObjectName(u"pushButton")
        self.pushButton.setGeometry(QRect(50, 110, 75, 31))
        font1 = QFont()
        font1.setPointSize(11)
        self.pushButton.setFont(font1)
        self.pushButton_2 = QPushButton(Exit)
        self.pushButton_2.setObjectName(u"pushButton_2")
        self.pushButton_2.setGeometry(QRect(180, 110, 75, 31))
        self.pushButton_2.setFont(font1)

        self.retranslateUi(Exit)

        QMetaObject.connectSlotsByName(Exit)
    # setupUi

    def retranslateUi(self, Exit):
        Exit.setWindowTitle(QCoreApplication.translate("Exit", u"Exit", None))
        self.label.setText(QCoreApplication.translate("Exit", u"\u786e\u8ba4\u8981\u9000\u51fa\u5417\uff1f", None))
        self.pushButton.setText(QCoreApplication.translate("Exit", u"Yes", None))
        self.pushButton_2.setText(QCoreApplication.translate("Exit", u"No", None))
    # retranslateUi

