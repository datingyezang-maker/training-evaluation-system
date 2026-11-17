package cn.training.evaluation;

import cn.training.evaluation.modules.identity.DatabaseUsers;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import org.springframework.http.MediaType;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 端到端接口验证：数据持久化、权限、二维码、报表与真实评价事务。 */
@SpringBootTest(properties={"app.security.admin-password=integration-only-password","spring.datasource.url=jdbc:h2:mem:business;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc
class BusinessFlowIntegrationTest {
 @Test void classOverviewIsScopedAndGroupsRelatedRecords()throws Exception{
  mvc.perform(get("/api/business/classes/"+classId+"/overview").with(user("admin"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.students.length()").value(1)).andExpect(jsonPath("$.data.lessons[0].id").value(lessonId)).andExpect(jsonPath("$.data.tasks[0].expected").value(1));
  mvc.perform(get("/api/business/classes/"+classId+"/overview").with(user("business"))).andExpect(status().isForbidden());
 }
 @Test void teacherFeedbackOnlyShowsOwnReleasedAggregates()throws Exception{
  long teacher=jdbc.queryForObject("SELECT teacher_id FROM lesson WHERE id=?",Long.class,lessonId);
  jdbc.update("INSERT INTO app_user(username,password_hash,role,enabled,teacher_id) VALUES('13900000121',?,'TEACHER',TRUE,?)",DatabaseUsers.hash("teacher-test-password"),teacher);
  jdbc.update("INSERT INTO student(code,name) VALUES('FEEDBACK-2','另一学员')");long second=jdbc.queryForObject("SELECT id FROM student WHERE code='FEEDBACK-2'",Long.class);
  jdbc.update("INSERT INTO eligibility(task_id,student_id,access_code) VALUES(?,?,'feedback-second-code')",taskId,second);submit(answer()).andExpect(status().isOk());
  mvc.perform(get("/api/portal/lessons/"+lessonId+"/feedback").with(user("13900000121"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.released").value(false)).andExpect(jsonPath("$.data.dimensions.length()").value(0));
  mvc.perform(get("/api/portal/courses").with(user("13900000121"))).andExpect(jsonPath("$.data[0].average_score").isEmpty());
  jdbc.update("UPDATE evaluation_task SET status='CLOSED' WHERE id=?",taskId);
  String result=mvc.perform(get("/api/portal/lessons/"+lessonId+"/feedback").with(user("13900000121"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.released").value(true)).andExpect(jsonPath("$.data.summary.submitted").value(1)).andExpect(jsonPath("$.data.dimensions.length()").value(3)).andReturn().getResponse().getContentAsString();
  assertFalse(result.contains("access_code"));assertFalse(result.contains("student_id"));assertFalse(result.contains("有效反馈"));
  jdbc.update("INSERT INTO teacher(name) VALUES('另一教师')");long other=jdbc.queryForObject("SELECT MAX(id) FROM teacher",Long.class);
  jdbc.update("INSERT INTO app_user(username,password_hash,role,enabled,teacher_id) VALUES('13900000122',?,'TEACHER',TRUE,?)",DatabaseUsers.hash("teacher-test-password"),other);
  mvc.perform(get("/api/portal/lessons/"+lessonId+"/feedback").with(user("13900000122"))).andExpect(status().isForbidden());
 }
 @Test void fullySubmittedTaskCompletesAndCannotBeEvaluatedAgain()throws Exception{String first=submit(answer()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM evaluation_task WHERE id=?",String.class,taskId));mvc.perform(get("/api/business/tasks").with(user("admin"))).andExpect(jsonPath("$.data[0].status").value("COMPLETED"));mvc.perform(get("/api/public/task").param("token",token)).andExpect(status().isBadRequest());Map<String,Object> changed=answer();changed.put("teaching",1);changed.put("comment","重复修改");assertEquals(first,submit(changed).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM answer",Integer.class));assertEquals(5,jdbc.queryForObject("SELECT teaching FROM answer",Integer.class));assertEquals("有效反馈",jdbc.queryForObject("SELECT comment FROM answer",String.class));mvc.perform(post("/api/business/tasks/"+taskId+"/extend").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"closes_at\":\"2100-01-01T00:00\",\"reason\":\"再次收集\"}")).andExpect(status().isBadRequest());}
 @Test void taskOnlyCompletesWhenEveryEligibleStudentSubmitted()throws Exception{jdbc.update("INSERT INTO student(code,name) VALUES('SECOND','第二位')");long student=jdbc.queryForObject("SELECT id FROM student WHERE code='SECOND'",Long.class);jdbc.update("INSERT INTO eligibility(task_id,student_id,access_code) VALUES(?,?,'second-code')",taskId,student);submit(answer()).andExpect(status().isOk());assertEquals("OPEN",jdbc.queryForObject("SELECT status FROM evaluation_task WHERE id=?",String.class,taskId));Map<String,Object> second=answer();second.put("access_code","second-code");submit(second).andExpect(status().isOk());assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM evaluation_task WHERE id=?",String.class,taskId));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM answer",Integer.class));}
 @Test void pickerLoadsUnenrolledStudentsWithoutTypingAndEnforcesScope()throws Exception{jdbc.update("INSERT INTO student(code,name) VALUES('PICK-2','李老师')");mvc.perform(get("/api/business/classes/"+classId+"/student-picker").with(user("admin"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].name").value("李老师"));mvc.perform(get("/api/business/classes/"+classId+"/student-picker").param("keyword","李").with(user("admin"))).andExpect(jsonPath("$.data.total").value(1));mvc.perform(get("/api/business/classes/"+classId+"/student-picker").with(user("business"))).andExpect(status().isForbidden());mvc.perform(get("/api/business/classes/"+classId+"/student-picker").with(user("viewer"))).andExpect(status().isForbidden());}
 @Test void selectingStudentsIsAtomicAndIgnoresDuplicateSelections()throws Exception{jdbc.update("INSERT INTO student(code,name) VALUES('PICK-3','王同学')");long id=jdbc.queryForObject("SELECT id FROM student WHERE code='PICK-3'",Long.class);String path="/api/business/classes/"+classId+"/members/select";mvc.perform(post(path).with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"student_ids\":["+id+",9999999]}")).andExpect(status().isBadRequest());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM enrollment",Integer.class));mvc.perform(post(path).with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"student_ids\":["+id+","+id+"]}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.added").value(1));mvc.perform(post(path).with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"student_ids\":["+id+"]}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.skipped").value(1));}
 @Test void duplicatePublicationIsRejectedAndClosedTaskCanBeDeleted()throws Exception{
  Map<String,Object>d=new HashMap<>();d.put("lesson_id",lessonId);d.put("opens_at","2026-01-01T00:00");d.put("closes_at","2099-01-01T00:00");mvc.perform(post("/api/business/tasks").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(d))).andExpect(status().isBadRequest());
  mvc.perform(delete("/api/business/tasks/"+taskId).with(user("admin")).with(csrf())).andExpect(status().isBadRequest());submit(answer()).andExpect(status().isOk());jdbc.update("UPDATE evaluation_task SET status='CLOSED' WHERE id=?",taskId);
  mvc.perform(delete("/api/business/tasks/"+taskId).with(user("business")).with(csrf())).andExpect(status().isForbidden());mvc.perform(delete("/api/business/tasks/"+taskId).with(user("admin")).with(csrf())).andExpect(status().isOk());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM answer",Integer.class));assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM evaluation_task WHERE id=?",java.sql.Timestamp.class,taskId));
  mvc.perform(get("/api/business/tasks").with(user("admin"))).andExpect(jsonPath("$.data.length()").value(0));mvc.perform(get("/api/public/task").param("token",token)).andExpect(status().isBadRequest());mvc.perform(get("/api/business/statistics").with(user("admin"))).andExpect(jsonPath("$.data.length()").value(0));
  mvc.perform(post("/api/business/tasks").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(d))).andExpect(status().isOk());
 }
 @Test void concurrentPublicationCreatesOneTaskOnly()throws Exception{
  jdbc.update("UPDATE evaluation_task SET status='REVOKED' WHERE id=?",taskId);mvc.perform(delete("/api/business/tasks/"+taskId).with(user("admin")).with(csrf())).andExpect(status().isOk());Map<String,Object>d=new HashMap<>();d.put("lesson_id",lessonId);d.put("opens_at","2026-01-01T00:00");d.put("closes_at","2099-01-01T00:00");byte[] body=mapper.writeValueAsBytes(d);ExecutorService pool=Executors.newFixedThreadPool(2);try{List<Future<Integer>> futures=new ArrayList<>();for(int i=0;i<2;i++)futures.add(pool.submit(()->mvc.perform(post("/api/business/tasks").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus()));int successes=0;for(Future<Integer> f:futures){int status=f.get();if(status==200)successes++;else assertTrue(status==400||status==409);}assertEquals(1,successes);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_task WHERE deleted_at IS NULL",Integer.class));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM task_publication_guard",Integer.class));}finally{pool.shutdownNow();}
 }
 @Test void dynamicFiveDimensionsDriveEvaluationStatisticsAndExport()throws Exception{
  Map<String,Object> template=new HashMap<>();template.put("name","五维度模板");List<Map<String,Object>> configured=new ArrayList<>();for(int i=1;i<=5;i++){Map<String,Object>d=new HashMap<>();d.put("label","自定义维度"+i);d.put("weight",i);configured.add(d);}template.put("dimensions",configured);
  mvc.perform(post("/api/business/templates").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(template))).andExpect(status().isOk());long templateId=jdbc.queryForObject("SELECT MAX(id) FROM evaluation_template",Long.class);jdbc.update("UPDATE evaluation_task SET template_id=?",templateId);
  List<Long> ids=jdbc.queryForList("SELECT id FROM template_dimension WHERE template_id=? ORDER BY position_no",Long.class,templateId);Map<String,Object> scores=new LinkedHashMap<>();for(int i=0;i<ids.size();i++)scores.put(ids.get(i).toString(),5-i);Map<String,Object>d=answer();d.put("scores",scores);submit(d).andExpect(status().isOk());assertEquals(5,jdbc.queryForObject("SELECT COUNT(*) FROM answer_dimension",Integer.class));
  mvc.perform(get("/api/business/tasks/"+taskId+"/report").with(user("admin"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.dimensions.length()").value(5)).andExpect(jsonPath("$.data.summary.average_score").value(org.hamcrest.Matchers.closeTo(2.333333,0.000001)));
  byte[] bytes=mvc.perform(get("/api/business/tasks/"+taskId+"/excel").with(user("admin"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();try(org.apache.poi.xssf.usermodel.XSSFWorkbook book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))){assertEquals(9,book.getSheetAt(0).getRow(0).getLastCellNum());assertEquals("自定义维度5",book.getSheetAt(0).getRow(0).getCell(4).getStringCellValue());}
 }
 @Test void dynamicDimensionsRejectMissingExtraAndInvalidConfiguration()throws Exception{
  List<Long> ids=jdbc.queryForList("SELECT id FROM template_dimension WHERE template_id=1 ORDER BY position_no",Long.class);Map<String,Object>d=answer(),scores=new LinkedHashMap<>();scores.put(ids.get(0).toString(),5);d.put("scores",scores);submit(d).andExpect(status().isBadRequest());scores.put(ids.get(1).toString(),4);scores.put("9999999",3);submit(d).andExpect(status().isBadRequest());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM answer",Integer.class));
  mvc.perform(post("/api/business/templates").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"空维度\",\"dimensions\":[]}")).andExpect(status().isBadRequest());mvc.perform(post("/api/business/templates").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"重复\",\"dimensions\":[{\"label\":\"相同\",\"weight\":1},{\"label\":\"相同\",\"weight\":1}]}")).andExpect(status().isBadRequest());
 }
 @Test void teacherPortalOnlyShowsOwnTeachingAndAggregates()throws Exception{long teacher=jdbc.queryForObject("SELECT teacher_id FROM lesson WHERE id=?",Long.class,lessonId);jdbc.update("INSERT INTO app_user(username,phone,display_name,password_hash,role,enabled,teacher_id) VALUES('13900139011','13900139011','教师账号',?,'TEACHER',TRUE,?)",DatabaseUsers.hash("teacher-test-password"),teacher);mvc.perform(get("/api/portal/courses").with(user("13900139011"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].lesson_name").value("课程"));mvc.perform(get("/api/business/tasks").with(user("13900139011"))).andExpect(status().isForbidden());}
 @Test void phoneBoundEvaluationRequiresItsOwnStudentAccount()throws Exception{
  long student=jdbc.queryForObject("SELECT id FROM student WHERE code='S01'",Long.class);jdbc.update("INSERT INTO app_user(username,phone,display_name,password_hash,role,enabled,student_id) VALUES('13800138011','13800138011','手机号学员',?,'STUDENT',TRUE,?)",DatabaseUsers.hash("phone-test-password"),student);
  submit(answer()).andExpect(status().isForbidden());
  Map<String,Object> data=answer();data.remove("access_code");mvc.perform(post("/api/portal/evaluate").with(user("13800138011")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(data))).andExpect(status().isOk());
 }
 @Test void batchEnrollmentIsAtomicAndIdempotent()throws Exception{
  jdbc.update("INSERT INTO student(code,name) VALUES('B02','批量学员')");String path="/api/business/classes/"+classId+"/members/batch";
  mvc.perform(post(path).with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"codes\":[\"B02\",\"missing\"]}")).andExpect(status().isBadRequest());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM enrollment",Integer.class));
  mvc.perform(post(path).with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"codes\":[\"B02\",\"B02\",\"S01\"]}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.added").value(1)).andExpect(jsonPath("$.data.skipped").value(1));
  mvc.perform(post(path).with(user("business")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"codes\":[\"B02\"]}")).andExpect(status().isForbidden());
 }
 @Test void copyingTemplatePreservesOriginalAndReadinessUsesActualRoster()throws Exception{
  mvc.perform(get("/api/business/lessons/"+lessonId+"/readiness").with(user("admin"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.students").value(1)).andExpect(jsonPath("$.data.tasks").value(1));
  int count=jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_template",Integer.class);mvc.perform(post("/api/business/templates/1/copy").with(user("admin")).with(csrf())).andExpect(status().isOk());assertEquals(count+1,jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_template",Integer.class));
  mvc.perform(post("/api/business/templates/1/copy").with(user("business")).with(csrf())).andExpect(status().isForbidden());
 }
 @Test void extensionRequiresLaterDateAndReasonAndRecordsAudit()throws Exception{
  String path="/api/business/tasks/"+taskId+"/extend";
  mvc.perform(post(path).with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"closes_at\":\"2100-01-01T00:00\",\"reason\":\"补充评价时间\"}")).andExpect(status().isOk());assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action='TASK_EXTEND'",Integer.class)>0);
  mvc.perform(post(path).with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"closes_at\":\"2099-01-01T00:00\",\"reason\":\"缩短\"}")).andExpect(status().isBadRequest());
  jdbc.update("UPDATE evaluation_task SET status='CLOSED'");mvc.perform(post(path).with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"closes_at\":\"2101-01-01T00:00\",\"reason\":\"恢复\"}")).andExpect(status().isBadRequest());
 }
 @Test void progressSeparatesIdentityFromScores()throws Exception{
  String path="/api/business/tasks/"+taskId+"/progress";String body=mvc.perform(get(path).param("status","pending").with(user("admin"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1)).andReturn().getResponse().getContentAsString();assertFalse(body.contains("access_code"));assertFalse(body.contains("teaching"));
  submit(answer()).andExpect(status().isOk());mvc.perform(get(path).param("status","pending").with(user("admin"))).andExpect(jsonPath("$.data.length()").value(0));mvc.perform(get(path).with(user("viewer"))).andExpect(status().isForbidden());
 }
 @Test void searchableMembersRespectScopeAndExcludeEnrolled()throws Exception{
  jdbc.update("INSERT INTO student(code,name) VALUES('S02','候选学员')");
  mvc.perform(get("/api/business/classes/"+classId+"/candidates").param("keyword","S0").with(user("admin"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].code").value("S02"));
  mvc.perform(get("/api/business/classes/"+classId+"/candidates").param("keyword","S0").with(user("business"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/business/classes/"+classId+"/candidates").param("keyword","S").with(user("admin"))).andExpect(status().isBadRequest());
 }
 @Test void sharingAndImportTemplateHavePermissionChecks()throws Exception{
  mvc.perform(get("/api/business/tasks/"+taskId+"/share").with(user("admin"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.url").exists());
  mvc.perform(get("/api/business/tasks/"+taskId+"/share").with(user("viewer"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/business/students/import-template").with(user("business"))).andExpect(status().isForbidden());
  byte[] bytes=mvc.perform(get("/api/business/students/import-template").with(user("admin"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
  try(org.apache.poi.xssf.usermodel.XSSFWorkbook book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))){assertEquals("学号",book.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());}
 }
 @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper mapper;
 long classId,lessonId,taskId;String token,code;
 @BeforeEach void setup(){
  jdbc.update("UPDATE app_user SET student_id=NULL,teacher_id=NULL");
  for(String table:new String[]{"task_publication_guard","answer_dimension","answer","eligibility","evaluation_task","lesson","enrollment","student","teacher","training_class"})jdbc.update("DELETE FROM "+table);
  jdbc.update("DELETE FROM app_user WHERE username<>'admin'");
  jdbc.update("UPDATE app_user SET role='ADMIN',enabled=TRUE WHERE username='admin'");
  long admin=jdbc.queryForObject("SELECT id FROM app_user WHERE username='admin'",Long.class);
  jdbc.update("INSERT INTO app_user(username,password_hash,role,enabled) VALUES('business',?,'BUSINESS',TRUE)",DatabaseUsers.hash("business-password"));
  jdbc.update("INSERT INTO app_user(username,password_hash,role,enabled) VALUES('viewer',?,'VIEWER',TRUE)",DatabaseUsers.hash("viewer-password"));
  jdbc.update("INSERT INTO training_class(name,owner_id,start_date,end_date) VALUES('测试班',?,'2026-01-01','2026-12-31')",admin);classId=jdbc.queryForObject("SELECT MAX(id) FROM training_class",Long.class);
  jdbc.update("INSERT INTO teacher(name) VALUES('教师')");long teacher=jdbc.queryForObject("SELECT MAX(id) FROM teacher",Long.class);
  jdbc.update("INSERT INTO student(code,name) VALUES('S01','学员')");long student=jdbc.queryForObject("SELECT MAX(id) FROM student",Long.class);
  jdbc.update("INSERT INTO enrollment VALUES(?,?)",classId,student);
  jdbc.update("INSERT INTO lesson(class_id,teacher_id,name,taught_at) VALUES(?,?,'课程',CURRENT_TIMESTAMP)",classId,teacher);lessonId=jdbc.queryForObject("SELECT MAX(id) FROM lesson",Long.class);
  token=UUID.randomUUID().toString();code=UUID.randomUUID().toString();
  jdbc.update("INSERT INTO evaluation_task(lesson_id,token,status,opens_at,closes_at) VALUES(?,?,'OPEN','2020-01-01 00:00:00','2099-01-01 00:00:00')",lessonId,token);taskId=jdbc.queryForObject("SELECT MAX(id) FROM evaluation_task",Long.class);
  jdbc.update("INSERT INTO eligibility(task_id,student_id,access_code) VALUES(?,?,?)",taskId,student,code);
 }
 private Map<String,Object> answer(){Map<String,Object>d=new HashMap<>();d.put("token",token);d.put("access_code",code);d.put("teaching",5);d.put("content",4);d.put("organization",3);d.put("comment","有效反馈");d.put("paper_reference","PAPER-001");return d;}
 private ResultActions submit(Map<String,Object>d)throws Exception{return mvc.perform(post("/api/public/submit").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(d)));}
 @Test void publicTaskDoesNotLeakPersonalCodes()throws Exception{String body=mvc.perform(get("/api/public/task").param("token",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertFalse(body.contains(code));}
 @Test void duplicateSubmissionReturnsSameReceipt()throws Exception{String a=submit(answer()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();String b=submit(answer()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertEquals(a,b);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM answer",Integer.class));}
 @Test void concurrentSubmissionProducesOneAnswer()throws Exception{ExecutorService pool=Executors.newFixedThreadPool(4);try{List<Future<Integer>> futures=new ArrayList<>();for(int i=0;i<4;i++)futures.add(pool.submit(()->submit(answer()).andReturn().getResponse().getStatus()));for(Future<Integer> f:futures)assertEquals(200,f.get().intValue());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM answer",Integer.class));}finally{pool.shutdownNow();}}
 @Test void invalidScoreAndWrongCodeDoNotWrite()throws Exception{Map<String,Object>d=answer();d.put("teaching",6);submit(d).andExpect(status().isBadRequest());d=answer();d.put("access_code","wrong");submit(d).andExpect(status().isBadRequest());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM answer",Integer.class));}
 @Test void closedTaskRejectsOnlineButAllowsPaper()throws Exception{jdbc.update("UPDATE evaluation_task SET status='CLOSED'");submit(answer()).andExpect(status().isBadRequest());mvc.perform(post("/api/business/paper").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(answer()))).andExpect(status().isOk());assertEquals("PAPER",jdbc.queryForObject("SELECT source FROM answer",String.class));}
 @Test void businessCannotReadOtherClassAndViewerCannotWrite()throws Exception{mvc.perform(get("/api/business/tasks/"+taskId+"/report").with(user("business"))).andExpect(status().isForbidden());mvc.perform(post("/api/business/classes").with(user("viewer")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());}
 @Test void referencedDeleteReturnsConflictAndLastAdminProtected()throws Exception{mvc.perform(delete("/api/business/classes/"+classId).with(user("admin")).with(csrf())).andExpect(status().isConflict());long id=jdbc.queryForObject("SELECT id FROM app_user WHERE username='admin'",Long.class);mvc.perform(put("/api/business/users/"+id).with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"admin\",\"role\":\"VIEWER\",\"enabled\":true}")).andExpect(status().isBadRequest());}
 @Test void exportsAreRealFilesAndStatisticsCorrect()throws Exception{submit(answer()).andExpect(status().isOk());mvc.perform(get("/api/business/tasks/"+taskId+"/report").with(user("admin"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.summary.submitted").value(1)).andExpect(jsonPath("$.data.summary.average_score").value(4));for(String kind:new String[]{"qr","excel","pdf"}){byte[] bytes=mvc.perform(get("/api/business/tasks/"+taskId+"/"+kind).with(user("admin"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();assertTrue(bytes.length>100);} }
 @Test void actualLoginCreatesSessionAndDisableInvalidatesIt()throws Exception{MvcResult result=mvc.perform(post("/api/auth/login").with(csrf()).param("username","business").param("password","business-password")).andExpect(status().isOk()).andReturn();MockHttpSession session=(MockHttpSession)result.getRequest().getSession(false);mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());jdbc.update("UPDATE app_user SET enabled=FALSE WHERE username='business'");mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());}
 @Test void publishingSnapshotsMembers()throws Exception{jdbc.update("UPDATE evaluation_task SET status='CLOSED' WHERE id=?",taskId);mvc.perform(delete("/api/business/tasks/"+taskId).with(user("admin")).with(csrf())).andExpect(status().isOk());Map<String,Object>d=new HashMap<>();d.put("lesson_id",lessonId);d.put("opens_at","2026-01-01T00:00");d.put("closes_at","2099-01-01T00:00");mvc.perform(post("/api/business/tasks").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(d))).andExpect(status().isOk());assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM eligibility",Integer.class));}
 @Test void passwordResetAndRoleChangesAffectExistingSession()throws Exception{
  MvcResult result=mvc.perform(post("/api/auth/login").with(csrf()).param("username","business").param("password","business-password")).andExpect(status().isOk()).andReturn();MockHttpSession session=(MockHttpSession)result.getRequest().getSession(false);
  jdbc.update("UPDATE app_user SET role='ADMIN' WHERE username='business'");mvc.perform(get("/api/business/users").session(session)).andExpect(status().isOk());
  jdbc.update("UPDATE app_user SET password_hash=? WHERE username='business'",DatabaseUsers.hash("replacement-password"));mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
 }
 @Test void expiredFutureAndRevokedTasksRejectSubmit()throws Exception{
  jdbc.update("UPDATE evaluation_task SET closes_at='2021-01-01 00:00:00'");submit(answer()).andExpect(status().isBadRequest());
  jdbc.update("UPDATE evaluation_task SET opens_at='2098-01-01 00:00:00',closes_at='2099-01-01 00:00:00'");submit(answer()).andExpect(status().isBadRequest());
  jdbc.update("UPDATE evaluation_task SET status='REVOKED'");mvc.perform(post("/api/business/paper").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(answer()))).andExpect(status().isBadRequest());
 }
 @Test void templateIsFrozenAndStatisticsFollowWeights()throws Exception{
  Map<String,Object> template=new HashMap<>();template.put("name","新模板");template.put("teaching_label","讲授");template.put("content_label","教材");template.put("organization_label","安排");template.put("teaching_weight",3);template.put("content_weight",1);template.put("organization_weight",1);
  mvc.perform(put("/api/business/templates/1").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(template))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/business/templates").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(template))).andExpect(status().isOk());
  long templateId=jdbc.queryForObject("SELECT MAX(id) FROM evaluation_template",Long.class);jdbc.update("UPDATE evaluation_task SET template_id=?",templateId);submit(answer()).andExpect(status().isOk());
  mvc.perform(get("/api/business/statistics?group=teacher").with(user("admin"))).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].average_score").value(4.4));
 }
 @Test void crossChannelDuplicateIsRejected()throws Exception{submit(answer()).andExpect(status().isOk());mvc.perform(post("/api/business/paper").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(answer()))).andExpect(status().isBadRequest());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM answer",Integer.class));}
 @Test void loginLocksAfterRepeatedFailures()throws Exception{for(int i=0;i<5;i++)mvc.perform(post("/api/auth/login").with(csrf()).param("username","business").param("password","incorrect")).andExpect(status().isUnauthorized());mvc.perform(post("/api/auth/login").with(csrf()).param("username","business").param("password","business-password")).andExpect(status().isUnauthorized());assertNotNull(jdbc.queryForObject("SELECT locked_until FROM app_user WHERE username='business'",java.sql.Timestamp.class));}
 @Test void excelImportIsAtomicAndBadFilesAreRejected()throws Exception{
  byte[] bytes;try(org.apache.poi.xssf.usermodel.XSSFWorkbook book=new org.apache.poi.xssf.usermodel.XSSFWorkbook();java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){org.apache.poi.ss.usermodel.Sheet sheet=book.createSheet();sheet.createRow(0).createCell(0).setCellValue("学号");org.apache.poi.ss.usermodel.Row a=sheet.createRow(1);a.createCell(0).setCellValue("S02");a.createCell(1).setCellValue("新增学员");org.apache.poi.ss.usermodel.Row b=sheet.createRow(2);b.createCell(0).setCellValue("S01");b.createCell(1).setCellValue("重复学员");book.write(out);bytes=out.toByteArray();}
  mvc.perform(multipart("/api/business/students/import").file(new org.springframework.mock.web.MockMultipartFile("file","students.xlsx","application/octet-stream",bytes)).with(user("admin")).with(csrf())).andExpect(status().isBadRequest());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM student",Integer.class));
  mvc.perform(multipart("/api/business/students/import").file(new org.springframework.mock.web.MockMultipartFile("file","bad.xlsx","application/octet-stream",new byte[]{1,2,3})).with(user("admin")).with(csrf())).andExpect(status().isBadRequest());
 }
 @Test void selfPasswordChangePersistsAndRequiresNewLogin()throws Exception{
  MvcResult login=mvc.perform(post("/api/auth/login").with(csrf()).param("username","business").param("password","business-password")).andExpect(status().isOk()).andReturn();MockHttpSession session=(MockHttpSession)login.getRequest().getSession(false);
  mvc.perform(post("/api/auth/password").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"old_password\":\"wrong-password\",\"new_password\":\"new-business-password\"}")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/auth/password").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"old_password\":\"business-password\",\"new_password\":\"new-business-password\"}")).andExpect(status().isOk());
  mvc.perform(post("/api/auth/login").with(csrf()).param("username","business").param("password","business-password")).andExpect(status().isUnauthorized());mvc.perform(post("/api/auth/login").with(csrf()).param("username","business").param("password","new-business-password")).andExpect(status().isOk());
 }
 @Test void publicWriteRequiresCsrfAndRevocationCannotBeReversed()throws Exception{
  mvc.perform(post("/api/public/submit").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(answer()))).andExpect(status().isForbidden());
  jdbc.update("UPDATE evaluation_task SET status='REVOKED'");mvc.perform(post("/api/business/tasks/"+taskId+"/status").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}")).andExpect(status().isBadRequest());
 }
}
