package cn.training.evaluation;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties={"app.security.admin-password=integration-only-password","spring.datasource.url=jdbc:h2:mem:numbers;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc
class StudentNumberIntegrationTest {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc;
 @BeforeEach void reset(){jdbc.update("DELETE FROM app_user WHERE username<>'admin'");jdbc.update("DELETE FROM student");jdbc.update("UPDATE student_number_sequence SET next_value=1 WHERE sequence_id=1");}
 int create()throws Exception{return mvc.perform(post("/api/business/students").with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"新学员\",\"code\":\"任意输入不会覆盖系统编号\"}")).andReturn().getResponse().getStatus();}
 @Test void startsAtFiveDigitsAndEditingOrDeletingDoesNotResetSequence()throws Exception{
  assertEquals(200,create());assertEquals("00001",jdbc.queryForObject("SELECT code FROM student",String.class));
  long id=jdbc.queryForObject("SELECT id FROM student",Long.class);
  mvc.perform(put("/api/business/students/"+id).with(user("admin")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"改名\",\"code\":\"99999\"}")).andExpect(status().isOk());
  assertEquals("00001",jdbc.queryForObject("SELECT code FROM student",String.class));
  mvc.perform(delete("/api/business/students/"+id).with(user("admin")).with(csrf())).andExpect(status().isOk());
  assertEquals(200,create());assertEquals("00002",jdbc.queryForObject("SELECT code FROM student",String.class));
 }
 @Test void concurrentCreatesHaveDistinctSequentialNumbers()throws Exception{
  ExecutorService pool=Executors.newFixedThreadPool(4);
  try{List<Future<Integer>> results=new ArrayList<>();for(int i=0;i<8;i++)results.add(pool.submit(this::create));for(Future<Integer> result:results)assertEquals(200,result.get().intValue());
   assertEquals(Arrays.asList("00001","00002","00003","00004","00005","00006","00007","00008"),jdbc.queryForList("SELECT code FROM student ORDER BY code",String.class));
  }finally{pool.shutdownNow();}
 }
 @Test void registrationAndAdminCreationShareOneSequence()throws Exception{
  org.springframework.mock.web.MockHttpSession session=(org.springframework.mock.web.MockHttpSession)mvc.perform(get("/api/auth/captcha")).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
  String payload="{\"phone\":\"13800000999\",\"display_name\":\"注册学员\",\"role\":\"STUDENT\",\"password\":\"student-test-password\",\"captcha\":\""+session.getAttribute("captchaCode")+"\"}";
  mvc.perform(post("/api/auth/register").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isOk());
  assertEquals("00001",jdbc.queryForObject("SELECT code FROM student",String.class));assertEquals(200,create());
  assertEquals(Arrays.asList("00001","00002"),jdbc.queryForList("SELECT code FROM student ORDER BY code",String.class));
 }
 @Test void importReservesExplicitCodesBeforeGeneratingMissingCodes()throws Exception{
  byte[] bytes;try(org.apache.poi.xssf.usermodel.XSSFWorkbook book=new org.apache.poi.xssf.usermodel.XSSFWorkbook();java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
   org.apache.poi.ss.usermodel.Sheet sheet=book.createSheet();sheet.createRow(0);sheet.createRow(1).createCell(1).setCellValue("自动编号");org.apache.poi.ss.usermodel.Row row=sheet.createRow(2);row.createCell(0).setCellValue("00001");row.createCell(1).setCellValue("保留学号");book.write(out);bytes=out.toByteArray();
  }
  mvc.perform(multipart("/api/business/students/import").file(new org.springframework.mock.web.MockMultipartFile("file","students.xlsx","application/octet-stream",bytes)).with(user("admin")).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.data").value(2));
  assertEquals("00002",jdbc.queryForObject("SELECT code FROM student WHERE name='自动编号'",String.class));
  assertEquals("00001",jdbc.queryForObject("SELECT code FROM student WHERE name='保留学号'",String.class));
 }
}
