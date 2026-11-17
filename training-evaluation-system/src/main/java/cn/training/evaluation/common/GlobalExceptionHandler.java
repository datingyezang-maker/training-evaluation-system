package cn.training.evaluation.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;

/** 不向调用方返回堆栈、SQL 或数据库连接信息。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(org.springframework.dao.ConcurrencyFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> busy(){return ApiResponse.error("RETRY_REQUIRED","数据正在被其他请求处理，请稍后重试");}
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> tooLarge(){return ApiResponse.error("FILE_TOO_LARGE","文件过大，请上传不超过2MB的xlsx文件");}
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> business(BusinessException error) { return ApiResponse.error("BUSINESS_ERROR", error.getMessage()); }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> conflict() { return ApiResponse.error("CONFLICT", "数据重复或仍被其他记录引用，请刷新后重试"); }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class, org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> malformed() { return ApiResponse.error("INVALID_ARGUMENT", "请求格式不正确"); }
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> validation() { return ApiResponse.error("INVALID_ARGUMENT", "请求参数不正确"); }
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> forbidden() { return ApiResponse.error("FORBIDDEN", "无权执行此操作"); }
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> unexpected(Exception error) {
        LOG.error("请求处理失败，异常类型：{}", error.getClass().getName());
        return ApiResponse.error("INTERNAL_ERROR", "服务处理失败，请联系管理员");
    }
}
