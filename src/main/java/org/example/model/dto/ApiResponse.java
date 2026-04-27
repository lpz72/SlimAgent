package org.example.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class ApiResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = -6548579745978588218L;

    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("success");
        response.setData(data);
        return response;
    }

    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(500);
        response.setMessage(message);
        return response;
    }

}