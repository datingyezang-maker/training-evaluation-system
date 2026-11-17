package cn.training.evaluation.modules.identity;

import cn.training.evaluation.common.*;
import cn.training.evaluation.modules.training.TrainingService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

@RestController
@RequestMapping("/api/business/accounts")
public class AccountImportController {
 private final TrainingService service;public AccountImportController(TrainingService service){this.service=service;}
 @GetMapping("/template")public void template(HttpServletResponse response)throws Exception{service.admin();try(XSSFWorkbook book=new XSSFWorkbook()){Row row=book.createSheet("账号导入").createRow(0);String[] titles={"手机号","姓名","角色(STUDENT/TEACHER/BUSINESS/VIEWER/ADMIN)","初始密码(至少12位)"};for(int i=0;i<titles.length;i++)row.createCell(i).setCellValue(titles[i]);response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");response.setHeader("Content-Disposition","attachment; filename=accounts-template.xlsx");book.write(response.getOutputStream());}}
 @PostMapping("/import")@Transactional public ApiResponse<?> upload(@RequestParam("file")MultipartFile file)throws Exception{
  service.admin();if(file.isEmpty()||file.getSize()>2097152)throw new BusinessException("账号文件须为不超过2MB的xlsx");List<Map<String,Object>> pending=new ArrayList<>();Set<String> phones=new HashSet<>();
  try(XSSFWorkbook book=new XSSFWorkbook(file.getInputStream())){Sheet sheet=book.getSheetAt(0);if(sheet.getLastRowNum()>200)throw new BusinessException("一次最多导入200个账号");DataFormatter format=new DataFormatter();for(int i=1;i<=sheet.getLastRowNum();i++){Row row=sheet.getRow(i);if(row==null)continue;String phone=format.formatCellValue(row.getCell(0)).trim(),name=format.formatCellValue(row.getCell(1)).trim(),role=format.formatCellValue(row.getCell(2)).trim(),password=format.formatCellValue(row.getCell(3));if(phone.isEmpty()&&name.isEmpty()&&role.isEmpty()&&password.isEmpty())continue;PhoneAccounts.phone(phone);PhoneAccounts.password(password);if(name.isEmpty()||name.length()>80)throw new BusinessException("第"+(i+1)+"行姓名无效");if(!Arrays.asList("STUDENT","TEACHER","ADMIN","BUSINESS","VIEWER").contains(role))throw new BusinessException("第"+(i+1)+"行角色无效");if(!phones.add(phone)||service.db().queryForObject("SELECT COUNT(*) FROM app_user WHERE phone=? OR username=?",Integer.class,phone,phone)>0)throw new BusinessException("第"+(i+1)+"行手机号重复，整批未导入");Map<String,Object> d=new HashMap<>();d.put("phone",phone);d.put("display_name",name);d.put("role",role);d.put("password",password);d.put("enabled",true);pending.add(d);}}
  catch(BusinessException error){throw error;}catch(Exception error){throw new BusinessException("无法读取账号文件，请使用下载的xlsx模板");}
  if(pending.isEmpty())throw new BusinessException("文件中没有可导入的账号");for(Map<String,Object>d:pending)service.save("users",null,d);return ApiResponse.ok(pending.size());
 }
}
