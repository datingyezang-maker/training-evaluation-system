package cn.training.evaluation.common;

/** 可向用户展示的业务错误，与不可泄露的系统异常分开处理。 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) { super(message); }
}
