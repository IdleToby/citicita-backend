package com.citacita.dto; // 建议放在一个新的 dto 包下

import lombok.Data;
@Data
public class ResultDTO<T> {
    private Integer code;
    private T data;
    private String msg;

    /**
     * 成功响应（携带数据）
     * @param data 响应数据
     * @return ResultDTO 实例
     * @param <T> 数据的类型
     */
    public static <T> ResultDTO<T> success(T data) {
        ResultDTO<T> result = new ResultDTO<>();
        result.setCode(200);
        result.setMsg("");
        result.setData(data);
        return result;
    }

    /**
     * 成功响应（不携带数据，例如删除、修改成功）
     * @return ResultDTO 实例
     */
    public static <T> ResultDTO<T> success() {
        return success(null);
    }

    /**
     * 失败响应
     * @param code 状态码
     * @param msg 错误消息
     * @return ResultDTO 实例
     */
    public static <T> ResultDTO<T> error(Integer code, String msg) {
        ResultDTO<T> result = new ResultDTO<>();
        result.setCode(code);
        result.setMsg(msg);
        result.setData(null); // 失败时 data 通常为 null
        return result;
    }

    /**
     * 失败响应（使用默认的服务器错误状态）
     * @param msg 错误消息
     * @return ResultDTO 实例
     */
    public static <T> ResultDTO<T> error(String msg) {
        return error(500, msg);
    }
}