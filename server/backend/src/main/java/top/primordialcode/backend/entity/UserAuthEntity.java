package top.primordialcode.backend.entity;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Data
public class UserAuthEntity {
    private String user_email;

    private String user_password;

    private String user_key;

    /** Upload credential: an alternative identifier for data upload without login */
    private String upload_credential;

    private String user_name;

    private Instant created_at;

    private Instant last_login_at;

}
