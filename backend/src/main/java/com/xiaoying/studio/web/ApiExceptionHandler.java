package com.xiaoying.studio.web;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> unsupported(HttpMediaTypeNotSupportedException exception) {
        return ResponseEntity.status(415).body(new ApiError("UNSUPPORTED_MEDIA_TYPE", "请求格式应为指定的 JSON 或 multipart 表单", Map.of(), UUID.randomUUID().toString()));
    }

    @ExceptionHandler(ApiProblem.class)
    public ResponseEntity<ApiError> problem(ApiProblem exception) {
        return ResponseEntity.status(exception.status()).body(new ApiError(exception.code(), exception.getMessage(), Map.of(), UUID.randomUUID().toString()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(413).body(new ApiError("FILE_TOO_LARGE", "单张图片不能超过 5 MiB，上传请求不能超过 6 MiB", Map.of(), UUID.randomUUID().toString()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalid(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new java.util.LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fields.put(error.getField(), "字段不能为空或超出允许范围"));
        return ResponseEntity.badRequest().body(new ApiError("INVALID_PARAMETERS", "请求参数不完整或不符合要求", fields, UUID.randomUUID().toString()));
    }

    @ExceptionHandler({ServletRequestBindingException.class, HttpMessageNotReadableException.class, MissingServletRequestPartException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> malformed(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "请求格式不正确，请检查必填字段和文件", Map.of(), UUID.randomUUID().toString()));
    }

    @ExceptionHandler({ResourceNotFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> notFound(Exception exception) {
        String message = exception instanceof ResourceNotFoundException ? exception.getMessage() : "资源不存在";
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("NOT_FOUND", message, Map.of(), UUID.randomUUID().toString()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception) {
        String requestId = UUID.randomUUID().toString();
        log.error("Request failed, requestId={}", requestId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "服务暂时不可用，请稍后重试", Map.of(), requestId));
    }
}
