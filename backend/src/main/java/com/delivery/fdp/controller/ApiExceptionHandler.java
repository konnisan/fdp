package com.delivery.fdp.controller;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handle(RuntimeException e) {
        return Map.of("message", message(e));
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleDatabase(DataAccessException e) {
        Throwable root = e.getMostSpecificCause();
        String detail = root == null ? message(e) : message(root);
        String migration = detail.contains("Incorrect string value")
                ? "migration_v12_managed_project_utf8mb4.sql"
                : "migration_v11_managed_artifact_binding_compat.sql 和 migration_v12_managed_project_utf8mb4.sql";
        return Map.of("message", "数据库结构或写入失败：" + detail + "。请执行最新 " + migration + "。");
    }

    private String message(Throwable e) {
        return e == null || e.getMessage() == null || e.getMessage().isBlank() ? "Request failed" : e.getMessage();
    }
}
