; ===========================
; 安装器基本信息
; ===========================
Unicode true

Name "屏幕视奸器"
OutFile "屏幕视奸器安装程序.exe"
InstallDir $PROGRAMFILES\屏幕视奸器
RequestExecutionLevel admin     ;设置安装权限user/admin

; ===========================
; 页面设置
; ===========================
Page directory      ; 选择安装目录
Page instfiles      ; 显示安装进度

; ===========================
; 安装段
; ===========================
Section "Install"

    ; 设置安装根目录
    SetOutPath $INSTDIR

    ; 复制主 exe 文件
    File "D:\Code\PythonCode\Projects\DeviceUsageTime\dist\main.exe"

    ; 复制 data_history 文件夹
    SetOutPath "$INSTDIR\data_history"
    File /r "D:\Code\PythonCode\Projects\DeviceUsageTime\屏幕视奸器\history_data\*.*"

    ; 复制 config 文件夹
    SetOutPath "$INSTDIR\config"
    File /r "D:\Code\PythonCode\Projects\DeviceUsageTime\屏幕视奸器\config\*.*"

    ; 创建桌面快捷方式
    CreateShortcut "$DESKTOP\屏幕视奸器.lnk" "$INSTDIR\main.exe" "" "$INSTDIR\main.exe" 0


SectionEnd

; ===========================
; 卸载段
; ===========================
Section "Uninstall"

    ; 删除 exe
    Delete "$INSTDIR\main.exe"

    ; 删除 data_history 文件夹
    RMDir /r "$INSTDIR\data_history"

    ; 删除 config 文件夹
    RMDir /r "$INSTDIR\config"

    ; 删除桌面快捷方式
    Delete "$DESKTOP\屏幕视奸器.lnk"

    ; 删除安装根目录（如果空的话）
    RMDir "$INSTDIR"

SectionEnd
