package top.primordialcode.backend.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    private int code;
    private String message;
    private Object data;

    public static Result success(int code) {
        return new Result(code,"操作成功",null);
    }
    public static Result success(){
        return new Result(200,"操作成功",null);
    }
    public static Result success(String message,Object data) {
        return new Result(200,message,data);
    }
    public static Result success(String message) {
        return new Result(200,message,null);
    }
    public static Result error(int code,String message,Object data){
        return new Result(code,message,data);
    }

}
