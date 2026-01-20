# -*- coding: utf-8 -*-

################################################################################
## Form generated from reading UI file 'settingsKalTeY.ui'
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

import resources_rc

class Ui_Settings(object):
    def setupUi(self, Settings):
        if Settings.objectName():
            Settings.setObjectName(u"Settings")
        Settings.setEnabled(True)
        Settings.resize(510, 380)
        sizePolicy = QSizePolicy(QSizePolicy.Fixed, QSizePolicy.Fixed)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(Settings.sizePolicy().hasHeightForWidth())
        Settings.setSizePolicy(sizePolicy)
        icon = QIcon()
        icon.addFile(u":/icons/icon.ico", QSize(), QIcon.Normal, QIcon.Off)
        Settings.setWindowIcon(icon)
        self.listWidget = QListWidget(Settings)
        font = QFont()
        font.setPointSize(12)
        font.setBold(True)
        font.setWeight(75)
        __qlistwidgetitem = QListWidgetItem(self.listWidget)
        __qlistwidgetitem.setTextAlignment(Qt.AlignCenter);
        __qlistwidgetitem.setFont(font);
        __qlistwidgetitem1 = QListWidgetItem(self.listWidget)
        __qlistwidgetitem1.setTextAlignment(Qt.AlignCenter);
        __qlistwidgetitem1.setFont(font);
        self.listWidget.setObjectName(u"listWidget")
        self.listWidget.setGeometry(QRect(0, 0, 131, 381))
        self.listWidget.setSpacing(5)
        self.stackedWidget = QStackedWidget(Settings)
        self.stackedWidget.setObjectName(u"stackedWidget")
        self.stackedWidget.setEnabled(True)
        self.stackedWidget.setGeometry(QRect(130, 0, 361, 371))
        sizePolicy.setHeightForWidth(self.stackedWidget.sizePolicy().hasHeightForWidth())
        self.stackedWidget.setSizePolicy(sizePolicy)
        self.page_1 = QWidget()
        self.page_1.setObjectName(u"page_1")
        self.checkBox = QCheckBox(self.page_1)
        self.checkBox.setObjectName(u"checkBox")
        self.checkBox.setEnabled(True)
        self.checkBox.setGeometry(QRect(10, 60, 131, 41))
        self.checkBox.setTabletTracking(False)
        self.checkBox.setAutoFillBackground(False)
        self.checkBox.setTristate(False)
        self.label = QLabel(self.page_1)
        self.label.setObjectName(u"label")
        self.label.setGeometry(QRect(10, 10, 91, 41))
        font1 = QFont()
        font1.setPointSize(16)
        font1.setBold(True)
        font1.setWeight(75)
        self.label.setFont(font1)
        self.label_3 = QLabel(self.page_1)
        self.label_3.setObjectName(u"label_3")
        self.label_3.setGeometry(QRect(150, 70, 54, 21))
        self.stackedWidget.addWidget(self.page_1)
        self.page_2 = QWidget()
        self.page_2.setObjectName(u"page_2")
        self.label_2 = QLabel(self.page_2)
        self.label_2.setObjectName(u"label_2")
        self.label_2.setGeometry(QRect(60, 110, 121, 41))
        self.stackedWidget.addWidget(self.page_2)

        self.retranslateUi(Settings)
        self.listWidget.currentRowChanged.connect(self.stackedWidget.setCurrentIndex)

        QMetaObject.connectSlotsByName(Settings)
    # setupUi

    def retranslateUi(self, Settings):
        Settings.setWindowTitle(QCoreApplication.translate("Settings", u"Form", None))

        __sortingEnabled = self.listWidget.isSortingEnabled()
        self.listWidget.setSortingEnabled(False)
        ___qlistwidgetitem = self.listWidget.item(0)
        ___qlistwidgetitem.setText(QCoreApplication.translate("Settings", u"\u901a\u7528", None));
#if QT_CONFIG(tooltip)
        ___qlistwidgetitem.setToolTip(QCoreApplication.translate("Settings", u"<html><head/><body><p align=\"center\"><br/></p></body></html>", None));
#endif // QT_CONFIG(tooltip)
        ___qlistwidgetitem1 = self.listWidget.item(1)
        ___qlistwidgetitem1.setText(QCoreApplication.translate("Settings", u"\u9884\u7559\u9009\u9879", None));
        self.listWidget.setSortingEnabled(__sortingEnabled)

        self.checkBox.setText(QCoreApplication.translate("Settings", u"\u5f00\u673a\u81ea\u542f\u52a8", None))
        self.label.setText(QCoreApplication.translate("Settings", u"\u901a\u7528", None))
        self.label_3.setText(QCoreApplication.translate("Settings", u"null", None))
        self.label_2.setText(QCoreApplication.translate("Settings", u"\u8fd9\u662f\u7b2c\u4e8c\u4e2a\u9875\u9762", None))
    # retranslateUi

