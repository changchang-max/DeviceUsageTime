package top.primordialcode.backend.model;

import lombok.Data;

import java.time.Instant;

@Data
public class UserRegister {
    private String email;

    private String user_key;

    private Instant createdAt;
}
