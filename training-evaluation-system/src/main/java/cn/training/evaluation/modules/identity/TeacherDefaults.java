package cn.training.evaluation.modules.identity;
import org.springframework.jdbc.core.JdbcTemplate;
/** 仅创建或转为教师身份时赋予默认权限，不干预管理员后续撤权。调用方负责事务。 */
public final class TeacherDefaults {
 private TeacherDefaults(){}
 public static void grant(JdbcTemplate jdbc,Object id){
  for(String code:new String[]{"CLASS_MANAGE","LESSON_MANAGE","TASK_MANAGE"})
   if(jdbc.queryForObject("SELECT COUNT(*) FROM account_permission WHERE user_id=? AND permission_code=?",Integer.class,id,code)==0)
    jdbc.update("INSERT INTO account_permission(user_id,permission_code) VALUES(?,?)",id,code);
 }
}
