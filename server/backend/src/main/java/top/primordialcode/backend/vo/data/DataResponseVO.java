package top.primordialcode.backend.vo.data;

import lombok.Data;

import java.time.Instant;

@Data
public class DataResponseVO {
    private boolean received;
    private Instant timestamp;
}
