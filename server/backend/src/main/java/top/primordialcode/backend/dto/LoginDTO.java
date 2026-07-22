package top.primordialcode.backend.dto;

import lombok.Data;

@Data
public class LoginDTO {
    private String user_email;

    private String user_password;
}
