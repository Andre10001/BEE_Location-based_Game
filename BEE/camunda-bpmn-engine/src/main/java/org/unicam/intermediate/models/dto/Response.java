package org.unicam.intermediate.models.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Response<T> {
    private boolean success;
    private T data;
    private String message;

    public static <T> Response<T> ok(T data) {
        return new Response<>(true, data, null);
    }

    public static <T> Response<T> error(String message) {
        return new Response<>(false, null, message);
    }
}
