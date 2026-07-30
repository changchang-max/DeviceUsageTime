package top.primordialcode.backend.entity;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class UserAuthEntity {
    private String user_email;

    private String user_password;

    private String user_key;

    private String user_name;

}
