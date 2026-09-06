package top.primordialcode.backend.dto.DataUpload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApplicationDTO {
    //要求字段不能是 null、不能是空字符串 ""，也不能只包含空白字符。
    @NotBlank(message = "应用名称不能为空")
    private String name;

    private String windowTitle;

    @NotNull(message = "持续时间不能为空")
    private Long duration;

    @NotNull(message = "活跃状态不能为空")
    private Boolean isActive;

    /**
     * 是否仍在运行(当前是否仍在持续上报)。
     * true=进程仍在运行, false=进程已关闭(已不再上报)。
     * 与isActive配合使用: isActive=true且isRunning=true表示桌面最顶端的窗口;
     * isActive=false且isRunning=true表示后台正在执行的进程。
     */
    private Boolean isRunning;
}
