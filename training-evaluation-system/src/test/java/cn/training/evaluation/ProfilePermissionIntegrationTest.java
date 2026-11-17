package cn.training.evaluation;

import cn.training.evaluation.modules.identity.DatabaseUsers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

/** 验证授权入口、独立功能、数据范围、撤权与完整发布流程，而非仅检查按钮。 */
@SpringBootTest(properties={"app.security.admin-password=integration-only-password","spring.datasource.url=jdbc:h2:mem:permissions;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc
class ProfilePermissionIntegrationTest {
 @Test void teacherCreatesOwnLessonAndPublishesFromPersonalWorkspace()throws Exception{
  grant("LESSON_MANAGE","TASK_MANAGE").andExpect(status().isOk());
  long admin=jdbc.queryForObject("SELECT id FROM app_user WHERE username='admin'",Long.class);
  jdbc.update("INSERT INTO training_class(name,start_date,end_date,owner_id) VALUES('教师负责班次','2026-01-01','2099-01-01',?)",account);long c=jdbc.queryForObject("SELECT id FROM training_class",Long.class);jdbc.update("INSERT INTO enrollment(class_id,student_id) VALUES(?,?)",c,student);
  mvc.perform(post("/api/portal/teaching/lessons").with(user("13900000001")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"本人新增课程\",\"class_id\":"+c+",\"teacher_id\":999999,\"taught_at\":\"2026-11-15T09:00\"}")).andExpect(status().isOk());
  assertEquals(teacher,jdbc.queryForObject("SELECT teacher_id FROM lesson",Long.class));long lesson=jdbc.queryForObject("SELECT id FROM lesson",Long.class);
  String body="{\"lesson_id\":"+lesson+",\"template_id\":1,\"opens_at\":\"2026-01-01T00:00\",\"closes_at\":\"2099-01-01T00:00\"}";
  mvc.perform(post("/api/portal/teaching/tasks").with(user("13900000001")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.data.url").isNotEmpty());
  mvc.perform(post("/api/portal/teaching/tasks").with(user("13900000001")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
  long task=jdbc.queryForObject("SELECT id FROM evaluation_task",Long.class);mvc.perform(get("/api/portal/teaching/tasks/"+task+"/qr").with(user("13900000001"))).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
  mvc.perform(get("/api/portal/teaching/options").with(user("13900000001"))).andExpect(jsonPath("$.data.lessons.length()").value(0));
  jdbc.update("INSERT INTO training_class(name,start_date,end_date,owner_id) VALUES('其他班次','2026-01-01','2099-01-01',?)",admin);long other=jdbc.queryForObject("SELECT id FROM training_class WHERE name='其他班次'",Long.class);
  mvc.perform(post("/api/portal/teaching/lessons").with(user("13900000001")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"越权\",\"class_id\":"+other+",\"taught_at\":\"2026-11-15T09:00\"}")).andExpect(status().isForbidden());
  grant().andExpect(status().isOk());mvc.perform(post("/api/portal/teaching/tasks").with(user("13900000001")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
 }
 @Test void teacherCannotPublishAnotherTeachersLessonAndNeedsExplicitPermission()throws Exception{
  mvc.perform(post("/api/portal/teaching/lessons").with(user("13900000001")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
  grant("TASK_MANAGE").andExpect(status().isOk());
  jdbc.update("INSERT INTO training_class(name,start_date,end_date,owner_id) VALUES('负责班次','2026-01-01','2099-01-01',?)",account);long c=jdbc.queryForObject("SELECT id FROM training_class",Long.class);
  jdbc.update("INSERT INTO teacher(name) VALUES('其他讲师')");long other=jdbc.queryForObject("SELECT MAX(id) FROM teacher",Long.class);
  jdbc.update("INSERT INTO lesson(class_id,teacher_id,name,taught_at) VALUES(?,?,'其他教师课程',CURRENT_TIMESTAMP)",c,other);long lesson=jdbc.queryForObject("SELECT id FROM lesson",Long.class);
  mvc.perform(post("/api/portal/teaching/tasks").with(user("13900000001")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"lesson_id\":"+lesson+"}")).andExpect(status().isForbidden());
 }
 @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
 long teacher,student,account;
 @BeforeEach void reset(){
  for(String table:new String[]{"task_publication_guard","answer_dimension","answer","eligibility","evaluation_task","lesson","enrollment","training_class","account_permission"})jdbc.update("DELETE FROM "+table);
  jdbc.update("DELETE FROM app_user WHERE username<>'admin'");jdbc.update("DELETE FROM student");jdbc.update("DELETE FROM teacher");
  jdbc.update("INSERT INTO teacher(name) VALUES('授权教师')");teacher=jdbc.queryForObject("SELECT id FROM teacher",Long.class);
  jdbc.update("INSERT INTO student(code,name) VALUES('PERM-1','授权学员')");student=jdbc.queryForObject("SELECT id FROM student",Long.class);
  jdbc.update("INSERT INTO app_user(username,password_hash,role,enabled,teacher_id) VALUES('13900000001',?,'TEACHER',TRUE,?)",DatabaseUsers.hash("permission-test-password"),teacher);
  account=jdbc.queryForObject("SELECT id FROM app_user WHERE username='13900000001'",Long.class);
 }
 String path(){return "/api/admin/profile-permissions/teachers/"+teacher;}
 ResultActions grant(String... codes)throws Exception{return mvc.perform(put(path()).with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Collections.singletonMap("permissions",Arrays.asList(codes)))));}
 ResultActions create(String entity,String body)throws Exception{return mvc.perform(post("/api/business/"+entity).with(user("13900000001")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));}
 String classBody(){return "{\"name\":\"教师班次\",\"start_date\":\"2026-01-01\",\"end_date\":\"2099-01-01\",\"owner_id\":1}";}
 @Test void onlyAdminCanGrantAndUnknownCodesRollback()throws Exception{
  mvc.perform(put(path()).with(user("13900000001")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"permissions\":[\"CLASS_MANAGE\"]}")).andExpect(status().isForbidden());
  grant("CLASS_MANAGE").andExpect(status().isOk());grant("ADMIN").andExpect(status().isBadRequest());
  assertEquals("CLASS_MANAGE",jdbc.queryForObject("SELECT permission_code FROM account_permission",String.class));
  mvc.perform(get(path()).with(user("admin"))).andExpect(jsonPath("$.data.permissions[0]").value("CLASS_MANAGE"));
 }
 @Test void classPermissionDoesNotAllowLessonOrAccountWritesAndRevocationIsImmediate()throws Exception{
  create("classes",classBody()).andExpect(status().isForbidden());grant("CLASS_MANAGE").andExpect(status().isOk());
  create("classes",classBody()).andExpect(status().isOk());assertEquals(account,jdbc.queryForObject("SELECT owner_id FROM training_class",Long.class));
  create("lessons","{}").andExpect(status().isForbidden());create("users","{}").andExpect(status().isForbidden());create("students","{}").andExpect(status().isForbidden());
  mvc.perform(get("/api/auth/me").with(user("13900000001"))).andExpect(jsonPath("$.data.workspace_allowed").value(true));
  grant().andExpect(status().isOk());mvc.perform(get("/api/business/classes").with(user("13900000001"))).andExpect(status().isForbidden());
 }
 @Test void authorizedTeacherCanOrganizeAndPublishButCannotAccessOtherOwners()throws Exception{
  grant("CLASS_MANAGE","LESSON_MANAGE","TASK_MANAGE").andExpect(status().isOk());create("classes",classBody()).andExpect(status().isOk());
  long classId=jdbc.queryForObject("SELECT id FROM training_class",Long.class);
  mvc.perform(post("/api/business/classes/"+classId+"/members/select").with(user("13900000001")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"student_ids\":["+student+"]}")).andExpect(status().isOk());
  create("lessons","{\"name\":\"授权课程\",\"class_id\":"+classId+",\"teacher_id\":"+teacher+",\"taught_at\":\"2026-11-14T12:00\"}").andExpect(status().isOk());
  long lesson=jdbc.queryForObject("SELECT id FROM lesson",Long.class);
  create("tasks","{\"lesson_id\":"+lesson+",\"opens_at\":\"2026-01-01T00:00\",\"closes_at\":\"2099-01-01T00:00\"}").andExpect(status().isOk());
  long task=jdbc.queryForObject("SELECT id FROM evaluation_task",Long.class);
  mvc.perform(get("/api/business/tasks/"+task+"/share").with(user("13900000001"))).andExpect(status().isOk());
  long admin=jdbc.queryForObject("SELECT id FROM app_user WHERE username='admin'",Long.class);
  jdbc.update("INSERT INTO training_class(name,start_date,end_date,owner_id) VALUES('其他班次','2026-01-01','2099-01-01',?)",admin);
  long other=jdbc.queryForObject("SELECT id FROM training_class WHERE name='其他班次'",Long.class);
  mvc.perform(get("/api/business/classes/"+other+"/members").with(user("13900000001"))).andExpect(status().isForbidden());
  create("lessons","{\"name\":\"越权课程\",\"class_id\":"+other+",\"teacher_id\":"+teacher+",\"taught_at\":\"2026-11-14T12:00\"}").andExpect(status().isForbidden());
 }
 @Test void studentCanReceiveSameExplicitPermissionsWithoutChangingIdentity()throws Exception{
  jdbc.update("INSERT INTO app_user(username,password_hash,role,enabled,student_id) VALUES('13800000001',?,'STUDENT',TRUE,?)",DatabaseUsers.hash("permission-test-password"),student);
  mvc.perform(put("/api/admin/profile-permissions/students/"+student).with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"permissions\":[\"CLASS_MANAGE\"]}")).andExpect(status().isOk());
  mvc.perform(post("/api/business/classes").with(user("13800000001")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(classBody())).andExpect(status().isOk());
  assertEquals("STUDENT",jdbc.queryForObject("SELECT role FROM app_user WHERE username='13800000001'",String.class));
  mvc.perform(get("/api/portal/courses").with(user("13800000001"))).andExpect(status().isOk());
 }
 @Test void profileWithoutAccountIsNotSilentlyConvertedOrGranted()throws Exception{
  mvc.perform(get("/api/admin/profile-permissions/students/"+student).with(user("admin"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.account").doesNotExist());
  mvc.perform(put("/api/admin/profile-permissions/students/"+student).with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"permissions\":[\"TASK_MANAGE\"]}")).andExpect(status().isBadRequest());
 }
}
