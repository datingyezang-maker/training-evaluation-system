package cn.training.evaluation;

import cn.training.evaluation.modules.training.TrainingService;
import cn.training.evaluation.modules.evaluation.EvaluationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 显式启用才连接本机真实库；业务测试数据在事务结束后回滚，迁移按启动流程保留。 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named="RUN_MYSQL_SMOKE",matches="true")
class MySqlSmokeTest {
 @Autowired JdbcTemplate jdbc;@Autowired TrainingService training;@Autowired EvaluationService evaluation;
 @Autowired cn.training.evaluation.modules.identity.PersonalPortalController portal;
 @Autowired cn.training.evaluation.modules.identity.TeacherWorkspaceController teacherWorkspace;
 private Map<String,Object> data(Object... pairs){Map<String,Object> d=new HashMap<>();for(int i=0;i<pairs.length;i+=2)d.put(pairs[i].toString(),pairs[i+1]);return d;}
 @Test @Transactional void actualMySqlBusinessTransaction()throws Exception{
  assertTrue(jdbc.queryForObject("SELECT VERSION()",String.class).startsWith("5.7"));
  long admin=jdbc.queryForObject("SELECT id FROM app_user WHERE username='admin'",Long.class);
  SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin",null,Collections.emptyList()));
  try{
   String suffix=UUID.randomUUID().toString();
   training.save("classes",null,data("name","事务验证班次","owner_id",admin,"start_date","2026-01-01","end_date","2026-12-31"));long c=jdbc.queryForObject("SELECT MAX(id) FROM training_class",Long.class);
   training.save("teachers",null,data("name","事务验证教师"));long teacher=jdbc.queryForObject("SELECT MAX(id) FROM teacher",Long.class);
   training.save("students",null,data("code",suffix,"name","事务验证学员"));long student=jdbc.queryForObject("SELECT MAX(id) FROM student",Long.class);training.enroll(c,student,false);
   training.save("lessons",null,data("name","事务验证课程","class_id",c,"teacher_id",teacher,"taught_at",LocalDateTime.now().toString()));long lesson=jdbc.queryForObject("SELECT MAX(id) FROM lesson",Long.class);
   training.save("tasks",null,data("lesson_id",lesson,"template_id",1,"opens_at",LocalDateTime.now().minusHours(1).toString(),"closes_at",LocalDateTime.now().plusHours(1).toString()));
   Map<String,Object> task=jdbc.queryForMap("SELECT * FROM evaluation_task ORDER BY id DESC LIMIT 1");String code=jdbc.queryForObject("SELECT access_code FROM eligibility WHERE task_id=?",String.class,task.get("id"));
   Map<String,Object> submission=data("token",task.get("token"),"access_code",code,"teaching",5,"content",4,"organization",3,"comment","回滚验证");String receipt=evaluation.submit(submission,false);assertEquals(receipt,evaluation.submit(submission,false));
   assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM answer a JOIN eligibility e ON e.id=a.eligibility_id WHERE e.task_id=?",Integer.class,task.get("id")));
   String reviewer="review-"+suffix;jdbc.update("INSERT INTO app_user(username,password_hash,role,enabled,teacher_id) VALUES(?,?,'TEACHER',TRUE,?)",reviewer,cn.training.evaluation.modules.identity.DatabaseUsers.hash("smoke-test-password"),teacher);
   SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(reviewer,null,Collections.emptyList()));
   Map<String,Object> feedback=(Map<String,Object>)portal.feedback(lesson).data;assertEquals(Boolean.TRUE,feedback.get("released"));assertFalse(((List<?>)feedback.get("dimensions")).isEmpty());
   long reviewerId=jdbc.queryForObject("SELECT id FROM app_user WHERE username=?",Long.class,reviewer);jdbc.update("INSERT INTO account_permission(user_id,permission_code) VALUES(?,'LESSON_MANAGE'),(?,'TASK_MANAGE')",reviewerId,reviewerId);jdbc.update("UPDATE training_class SET owner_id=? WHERE id=?",reviewerId,c);
   Map<String,Object> options=(Map<String,Object>)teacherWorkspace.options().data;assertEquals(Boolean.TRUE,options.get("can_publish"));
   teacherWorkspace.createLesson(data("name","教师端事务课程","class_id",c,"teacher_id",999999,"taught_at",LocalDateTime.now().toString()));long ownLesson=jdbc.queryForObject("SELECT MAX(id) FROM lesson",Long.class);
   Map<String,Object> publication=(Map<String,Object>)teacherWorkspace.publish(data("lesson_id",ownLesson,"template_id",1,"opens_at",LocalDateTime.now().minusHours(1).toString(),"closes_at",LocalDateTime.now().plusHours(1).toString())).data;
   org.springframework.mock.web.MockHttpServletResponse qr=new org.springframework.mock.web.MockHttpServletResponse();teacherWorkspace.qr(TrainingService.num(publication.get("task_id")),qr);assertEquals("image/png",qr.getContentType());assertTrue(qr.getContentAsByteArray().length>100);
  }finally{SecurityContextHolder.clearContext();}
 }
}
