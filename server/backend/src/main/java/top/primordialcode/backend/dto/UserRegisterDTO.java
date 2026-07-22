package top.primordialcode.backend.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class UserRegisterDTO {
    private String email;

    private String user_key;

    private Instant createdAt;
}
