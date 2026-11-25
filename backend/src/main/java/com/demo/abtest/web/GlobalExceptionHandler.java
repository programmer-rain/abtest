package com.demo.abtest.web;

import com.demo.abtest.web.dto.OkResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.nio.charset.StandardCharsets;

/**
 * 全局异常处理：把旧版"catch 后返回 {ok:false,message}"的行为搬过来。
 *
 * 教学说明：
 *  - @RestControllerAdvice 统一拦截 Controller 抛出的异常，
 *    业务代码里就不用到处写 try-catch 了；
 *  - 404 分两种：接口不存在（返回 JSON）和静态文件不存在（提示先构建前端）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final MediaType TEXT_PLAIN_UTF8 =
            new MediaType("text", "plain", StandardCharsets.UTF_8);

    /** 静态资源 404（前端未构建）或未知接口 404 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> notFound(NoResourceFoundException e) {
        if (e.getResourcePath().startsWith("api/")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(OkResponse.error("接口不存在: /" + e.getResourcePath()));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(TEXT_PLAIN_UTF8)
                .body("前端尚未构建。请先在 frontend 目录执行 npm install && npm run build，"
                        + "或使用 npm run dev 通过 Vite 开发服务器访问。");
    }

    /** 实验不存在、非法状态流转等业务异常（与旧版一致：500 + 错误消息） */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<OkResponse> bizError(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(OkResponse.error(e.getMessage() == null ? "服务器内部错误" : e.getMessage()));
    }

    /** 请求体 JSON 解析失败 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<OkResponse> badBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(OkResponse.error("请求体格式错误"));
    }

    /** 其他未知异常兜底 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<OkResponse> unknown(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(OkResponse.error(e.getMessage() == null ? "服务器内部错误" : e.getMessage()));
    }
}
