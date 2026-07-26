package top.primordialcode.backend.dto.DataUpload;

import lombok.Data;
import java.time.Instant;

@Data
public class RedisSaveOtherDataDTO {
    private String userEmail;
    private Instant timestamp;
}
