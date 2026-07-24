package top.primordialcode.backend.dto.DataUpload;

import lombok.Data;

@Data
public class ApplicationDTO {

    private String name;

    private String windowTitle;

    private Long duration;

    private Boolean isActive;
}