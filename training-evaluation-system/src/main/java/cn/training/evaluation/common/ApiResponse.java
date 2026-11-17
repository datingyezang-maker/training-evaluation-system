package cn.training.evaluation.common;

/** 统一接口信封；业务数据不直接暴露数据库实体。 */
public class ApiResponse<T> {
    public final String code;
    public final String message;
    public final T data;
    private ApiResponse(String code, String message, T data) {
        this.code = code; this.message = message; this.data = data;
    }
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>("OK", "成功", data); }
    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
