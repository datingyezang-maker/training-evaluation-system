package cn.training.evaluation.modules.identity;

import cn.training.evaluation.common.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import javax.servlet.http.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;
import java.util.*;

/** 会话图形验证码：5分钟、一次性、失败也销毁；教师自注册待管理员审核。 */
@RestController
@RequestMapping("/api/auth")
public class RegistrationController {
 private final JdbcTemplate jdbc;private final PhoneAccounts accounts;private final SecureRandom random=new SecureRandom();
 public RegistrationController(JdbcTemplate jdbc,PhoneAccounts accounts){this.jdbc=jdbc;this.accounts=accounts;}
 @GetMapping("/captcha") public void captcha(HttpServletRequest request,HttpServletResponse response)throws Exception{
  HttpSession session=request.getSession();long now=System.currentTimeMillis();synchronized(session){Object last=session.getAttribute("captchaLast");if(last!=null&&now-(Long)last<1000)throw new BusinessException("请稍后刷新验证码");String alphabet="23456789ABCDEFGHJKMNPQRSTUVWXYZ";StringBuilder code=new StringBuilder();for(int i=0;i<5;i++)code.append(alphabet.charAt(random.nextInt(alphabet.length())));session.setAttribute("captchaCode",code.toString());session.setAttribute("captchaAt",now);session.setAttribute("captchaLast",now);
   BufferedImage image=new BufferedImage(180,56,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setColor(new Color(242,247,245));g.fillRect(0,0,180,56);g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,30));for(int i=0;i<5;i++){g.setColor(new Color(25+random.nextInt(70),65+random.nextInt(80),65+random.nextInt(70)));g.drawString(code.substring(i,i+1),12+i*32,36+random.nextInt(8));}for(int i=0;i<12;i++){g.setColor(new Color(130+random.nextInt(100),160,180));g.drawLine(random.nextInt(180),random.nextInt(56),random.nextInt(180),random.nextInt(56));}g.dispose();response.setContentType("image/png");response.setHeader("Cache-Control","no-store");javax.imageio.ImageIO.write(image,"PNG",response.getOutputStream());}
 }
 @PostMapping("/register") @Transactional public ApiResponse<?> register(@RequestBody Map<String,Object> data,HttpServletRequest request){
  HttpSession session=request.getSession(false);if(session==null)throw new BusinessException("请先获取图形验证码");synchronized(session){String expected=(String)session.getAttribute("captchaCode");Long at=(Long)session.getAttribute("captchaAt");session.removeAttribute("captchaCode");session.removeAttribute("captchaAt");String entered=String.valueOf(data.get("captcha")).trim();if(expected==null||at==null||System.currentTimeMillis()-at>300000||!expected.equalsIgnoreCase(entered))throw new BusinessException("验证码错误或已过期，请刷新后重试");}
  String role=String.valueOf(data.get("role"));if(!Arrays.asList("STUDENT","TEACHER").contains(role))throw new BusinessException("自注册仅支持学员或教师");
  Map<String,Object> clean=new HashMap<>();clean.put("phone",data.get("phone"));clean.put("display_name",data.get("display_name"));Map<String,Object> fields=accounts.fields(clean,null,role);String password=PhoneAccounts.password(data.get("password"));boolean enabled="STUDENT".equals(role);
  jdbc.update("INSERT INTO app_user(username,phone,display_name,password_hash,role,enabled,student_id,teacher_id) VALUES(?,?,?,?,?,?,?,?)",fields.get("username"),fields.get("phone"),fields.get("display_name"),DatabaseUsers.hash(password),role,enabled,fields.get("student_id"),fields.get("teacher_id"));if("TEACHER".equals(role))TeacherDefaults.grant(jdbc,jdbc.queryForObject("SELECT id FROM app_user WHERE username=?",Long.class,fields.get("username")));if(!enabled)jdbc.update("UPDATE app_user SET review_status='PENDING' WHERE phone=?",fields.get("phone"));jdbc.update("INSERT INTO audit_log(actor,action,path,http_status) VALUES('self-register','REGISTER',?,200)","role/"+role);return ApiResponse.ok(enabled?"注册成功，请使用手机号登录":"注册已提交，请联系管理员审核后登录");
 }
 @GetMapping("/verification-options") public ApiResponse<?> verification(){Map<String,Object> result=new LinkedHashMap<>();result.put("captcha",true);result.put("sms",false);result.put("phoneOwnershipVerified",false);return ApiResponse.ok(result);}
}
