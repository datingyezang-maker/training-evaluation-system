package cn.training.evaluation.modules.report;

import cn.training.evaluation.common.*;
import cn.training.evaluation.modules.training.TrainingService;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.io.*;

/** 所有导出均由本机生成，检查班次权限并记录审计；Excel 单元格按字符串写入以避免公式注入。 */
@RestController
@RequestMapping("/api/business")
public class ReportController {
    private final TrainingService service;
    private final String baseUrl;
    @GetMapping("/tasks/{id}/pending/excel") public void pendingExcel(@PathVariable long id,HttpServletResponse response)throws Exception{service.write("TASK_MANAGE");service.task(id);service.audit("PENDING_EXPORT","tasks/"+id);List<Map<String,Object>> rows=service.db().queryForList("SELECT s.code,s.name FROM eligibility e JOIN student s ON s.id=e.student_id LEFT JOIN answer a ON a.eligibility_id=e.id WHERE e.task_id=? AND a.id IS NULL ORDER BY s.code",id);writeWorkbook(rows,new String[]{"学号","姓名"},new String[]{"code","name"},"pending-students.xlsx",response);}
    @GetMapping("/students/import-template") public void importTemplate(HttpServletResponse response)throws Exception{service.admin();writeWorkbook(Collections.emptyList(),new String[]{"学号","姓名"},new String[]{"code","name"},"student-import-template.xlsx",response);}
    public ReportController(TrainingService service,@Value("${app.public-base-url:http://localhost:8080}")String baseUrl){this.service=service;this.baseUrl=baseUrl;}
    @GetMapping("/tasks/{id}/report") public ApiResponse<?> report(@PathVariable long id){
        Map<String,Object> task=service.task(id);Map<String,Object> result=new LinkedHashMap<>(task);
        result.put("expected",service.db().queryForObject("SELECT COUNT(*) FROM eligibility WHERE task_id=?",Integer.class,id));
        result.put("template",service.one("SELECT t.* FROM evaluation_template t JOIN evaluation_task e ON e.template_id=t.id WHERE e.id=?",id));
        result.put("summary",service.db().queryForMap("SELECT COUNT(a.id) submitted,AVG(a.teaching) teaching,AVG(a.content) content,AVG(a.organization) organization,AVG(a.total_score) average_score FROM eligibility e JOIN evaluation_task task ON task.id=e.task_id JOIN evaluation_template t ON t.id=task.template_id LEFT JOIN answer a ON a.eligibility_id=e.id WHERE e.task_id=?",id));
        List<Map<String,Object>> dimensions=service.db().queryForList("SELECT d.id,d.label,d.weight,d.position_no,AVG(ad.score) average_score FROM template_dimension d LEFT JOIN answer_dimension ad ON ad.dimension_id=d.id AND ad.answer_id IN(SELECT a.id FROM answer a JOIN eligibility e ON e.id=a.eligibility_id WHERE e.task_id=?) WHERE d.template_id=? GROUP BY d.id,d.label,d.weight,d.position_no ORDER BY d.position_no",id,task.get("template_id"));result.put("dimensions",dimensions);
        List<Map<String,Object>> answers=service.db().queryForList("SELECT a.id,a.total_score,a.comment,a.source,a.created_at FROM answer a JOIN eligibility e ON e.id=a.eligibility_id WHERE e.task_id=? ORDER BY a.id",id);// Load all answer dimensions in one query instead of one query per answer.
        Map<Long,Map<String,Object>> pointsByAnswer=new HashMap<>();
        for(Map<String,Object> point:service.db().queryForList("SELECT ad.answer_id,ad.dimension_id,ad.score FROM answer_dimension ad JOIN answer a ON a.id=ad.answer_id JOIN eligibility e ON e.id=a.eligibility_id WHERE e.task_id=?",id)){
            pointsByAnswer.computeIfAbsent(TrainingService.num(point.get("answer_id")),key->new LinkedHashMap<>()).put("d"+point.get("dimension_id"),point.get("score"));
        }
        for(Map<String,Object> answer:answers){answer.putAll(pointsByAnswer.getOrDefault(TrainingService.num(answer.get("id")),Collections.emptyMap()));answer.remove("id");}
        result.put("answers",answers);return ApiResponse.ok(result);
    }
    @GetMapping("/tasks/{id}/qr") public void qr(@PathVariable long id,HttpServletResponse response)throws Exception{
        service.write("TASK_MANAGE");Map<String,Object> task=service.task(id);String url=baseUrl.replaceAll("/$","")+"/h5/?token="+task.get("token");
        BitMatrix matrix=new MultiFormatWriter().encode(url,BarcodeFormat.QR_CODE,400,400);response.setContentType("image/png");service.audit("QR_EXPORT","tasks/"+id);MatrixToImageWriter.writeToStream(matrix,"PNG",response.getOutputStream());
    }
    @GetMapping("/tasks/{id}/excel") public void excel(@PathVariable long id,HttpServletResponse response)throws Exception{
        service.task(id);service.audit("EXCEL_EXPORT","tasks/"+id);
        Map<String,Object> report=(Map<String,Object>)report(id).data;
        List<Map<String,Object>> rows=(List<Map<String,Object>>)report.get("answers"),dims=(List<Map<String,Object>>)report.get("dimensions");
        try(XSSFWorkbook book=new XSSFWorkbook()){
            Sheet sheet=book.createSheet("评价明细");List<String> headers=new ArrayList<>(),keys=new ArrayList<>();for(Map<String,Object> dim:dims){headers.add(dim.get("label").toString());keys.add("d"+dim.get("id"));}headers.addAll(Arrays.asList("综合分","意见","来源","提交时间"));keys.addAll(Arrays.asList("total_score","comment","source","created_at"));
            Row title=sheet.createRow(0);for(int i=0;i<headers.size();i++)title.createCell(i).setCellValue(headers.get(i));int index=1;
            for(Map<String,Object> data:rows){Row row=sheet.createRow(index++);for(int i=0;i<keys.size();i++)row.createCell(i).setCellValue(String.valueOf(data.get(keys.get(i))));}
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");response.setHeader("Content-Disposition","attachment; filename=evaluation-"+id+".xlsx");book.write(response.getOutputStream());
        }
    }
    @GetMapping("/tasks/{id}/pdf") public void pdf(@PathVariable long id,HttpServletResponse response)throws Exception{
        Map<String,Object> task=service.task(id);service.audit("PDF_EXPORT","tasks/"+id);
        // 使用随依赖交付的亚洲字体映射，无需访问在线字体服务。
        Map<String,Object> template=service.one("SELECT t.* FROM evaluation_template t JOIN evaluation_task e ON e.template_id=t.id WHERE e.id=?",id);
        BaseFont base=BaseFont.createFont("STSong-Light","UniGB-UCS2-H",BaseFont.NOT_EMBEDDED);
        com.lowagie.text.Font font=new com.lowagie.text.Font(base,12);
        response.setContentType("application/pdf");response.setHeader("Content-Disposition","attachment; filename=evaluation-"+id+".pdf");
        Document doc=new Document();PdfWriter.getInstance(doc,response.getOutputStream());doc.open();
        doc.add(new Paragraph("培训课程纸质评价表",new com.lowagie.text.Font(base,20)));
        for(String key:Arrays.asList("class_name","lesson_name","teacher_name"))doc.add(new Paragraph(String.valueOf(task.get(key)),font));
        doc.add(new Paragraph("个人评价码：____________________________",font));
        doc.add(new Paragraph("每项选择1至5分，1为最低，5为最高。模板："+template.get("name"),font));
        for(Map<String,Object> dimension:service.db().queryForList("SELECT label,weight FROM template_dimension WHERE template_id=? ORDER BY position_no",task.get("template_id")))doc.add(new Paragraph(dimension.get("label")+"（权重 "+dimension.get("weight")+"）： 1 / 2 / 3 / 4 / 5",font));
        doc.add(new Paragraph("意见建议：\n\n\n\n____________________________",font));doc.add(new Paragraph("纸表编号：________    补录人员：________",font));doc.close();
    }
    @PostMapping("/students/import") @Transactional public ApiResponse<?> importStudents(@RequestParam("file")MultipartFile file)throws Exception{
        service.admin();if(file.isEmpty()||file.getSize()>2*1024*1024)throw new BusinessException("请上传不超过2MB的xlsx文件");
        // 两阶段处理：先校验全部行，任何错误不写入，避免半成功导入。
        List<Map<String,Object>> pending=new ArrayList<>();Set<String> codes=new HashSet<>();
        try(XSSFWorkbook book=new XSSFWorkbook(file.getInputStream())){
            Sheet sheet=book.getSheetAt(0);if(sheet.getLastRowNum()>5000)throw new BusinessException("单次最多5000行");
            DataFormatter formatter=new DataFormatter();
            for(int i=1;i<=sheet.getLastRowNum();i++){Row row=sheet.getRow(i);if(row==null)continue;String code=formatter.formatCellValue(row.getCell(0)).trim(),name=formatter.formatCellValue(row.getCell(1)).trim();if(code.isEmpty()&&name.isEmpty())continue;
                if(name.isEmpty()||code.length()>80||name.length()>80||(!code.isEmpty()&&!codes.add(code)))throw new BusinessException("第"+(i+1)+"行学号姓名为空、过长或学号重复");
                if(!code.isEmpty()&&service.db().queryForObject("SELECT COUNT(*) FROM student WHERE code=?",Integer.class,code)>0)throw new BusinessException("第"+(i+1)+"行学号已存在");
                Map<String,Object> data=new HashMap<>();data.put("code",code);data.put("name",name);pending.add(data);
            }
        }catch(BusinessException e){throw e;}catch(Exception e){throw new BusinessException("无法读取xlsx文件，请使用学号、姓名两列，首行为表头");}
        for(Map<String,Object> data:pending)if(!data.get("code").toString().isEmpty())service.importStudent(data);
        for(Map<String,Object> data:pending)if(data.get("code").toString().isEmpty())service.importStudent(data);return ApiResponse.ok(pending.size());
    }
    /** 按班次、授课、教师汇总：答卷加权，不将未提交视为零分。 */
    @GetMapping("/statistics") public ApiResponse<?> statistics(@RequestParam(defaultValue="lesson")String group){
        return ApiResponse.ok(statisticRows(group));
    }
    private List<Map<String,Object>> statisticRows(String group){
        String id,name;switch(group){case "teacher":id="te.id";name="te.name";break;case "class":id="c.id";name="c.name";break;case "lesson":id="l.id";name="l.name";break;default:throw new BusinessException("统计维度无效");}
        Map<String,Object> user=service.user();boolean all="ADMIN".equals(user.get("role"));
        return service.db().queryForList("SELECT "+id+" id,"+name+" name,COUNT(e.id) expected,COUNT(a.id) submitted,AVG(a.total_score) average_score FROM evaluation_task task JOIN lesson l ON l.id=task.lesson_id JOIN training_class c ON c.id=l.class_id JOIN teacher te ON te.id=l.teacher_id JOIN evaluation_template t ON t.id=task.template_id LEFT JOIN eligibility e ON e.task_id=task.id LEFT JOIN answer a ON a.eligibility_id=e.id WHERE task.status<>'REVOKED' AND task.deleted_at IS NULL"+(all?"":" AND c.owner_id=?")+" GROUP BY "+id+","+name+" ORDER BY "+id,all?new Object[]{}:new Object[]{user.get("id")});
    }
    @GetMapping("/statistics/excel") public void statisticExcel(@RequestParam(defaultValue="lesson")String group,HttpServletResponse response)throws Exception{
        List<Map<String,Object>> rows=statisticRows(group);service.audit("STATS_EXPORT",group);writeWorkbook(rows,new String[]{"名称","应评人次","有效答卷","均分"},new String[]{"name","expected","submitted","average_score"},"statistics.xlsx",response);
    }
    @GetMapping("/tasks/{id}/codes/excel") public void codeExcel(@PathVariable long id,HttpServletResponse response)throws Exception{
        service.write("TASK_MANAGE");service.task(id);service.audit("EXPORT_CODES","tasks/"+id);
        List<Map<String,Object>> rows=service.db().queryForList("SELECT s.code,s.name,e.access_code FROM eligibility e JOIN student s ON s.id=e.student_id WHERE e.task_id=?",id);
        writeWorkbook(rows,new String[]{"学号","姓名","个人评价码（请单独发放）"},new String[]{"code","name","access_code"},"evaluation-codes.xlsx",response);
    }
    private void writeWorkbook(List<Map<String,Object>> rows,String[] headers,String[] keys,String filename,HttpServletResponse response)throws Exception{
        try(XSSFWorkbook book=new XSSFWorkbook()){Sheet sheet=book.createSheet("数据");Row title=sheet.createRow(0);for(int i=0;i<headers.length;i++)title.createCell(i).setCellValue(headers[i]);int index=1;for(Map<String,Object> data:rows){Row row=sheet.createRow(index++);for(int i=0;i<keys.length;i++)row.createCell(i).setCellValue(data.get(keys[i])==null?"":data.get(keys[i]).toString());}response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");response.setHeader("Content-Disposition","attachment; filename="+filename);book.write(response.getOutputStream());}
    }
}
