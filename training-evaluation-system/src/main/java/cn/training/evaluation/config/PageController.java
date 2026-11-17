package cn.training.evaluation.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.IOException;

/** 显式映射目录入口；Spring Boot 默认不会为任意静态子目录自动查找 index.html。 */
@Controller
public class PageController {
    @GetMapping(value={"/account","/account/","/account/index.html"},produces="text/html;charset=UTF-8") @ResponseBody public String account()throws IOException{return resource("static/account/index.html").replace("<link rel=\"stylesheet\" href=\"/assets/app.css\">","<style>"+resource("static/assets/app.css")+"</style>").replace("<script defer src=\"/assets/account.js\"></script>","").replace("</body>","<script>"+resource("static/assets/account.js")+"</script></body>");}
    @GetMapping({"/admin", "/admin/"})
    public String admin() {
        return "forward:/admin/index.html";
    }

    @GetMapping(value={"/h5", "/h5/", "/h5/index.html"}, produces="text/html;charset=UTF-8")
    @ResponseBody
    public String learner() throws IOException {
        // 一次响应交付关键资源，避免临时隧道拦截子资源使页面停留在初始状态。
        return resource("static/h5/index.html")
                .replace("<link rel=\"stylesheet\" href=\"/assets/app.css\">", "<style>"+resource("static/assets/app.css")+"</style>")
                .replace("<script defer src=\"/assets/h5.js\"></script>", "")
                .replace("</body>", "<script>"+resource("static/assets/h5.js")+"</script></body>");
    }
    private String resource(String path) throws IOException {
        try(InputStream input=new ClassPathResource(path).getInputStream()) {
            String content=StreamUtils.copyToString(input,StandardCharsets.UTF_8);
            if(path.endsWith("index.html"))content=content.replace("<link rel=\"stylesheet\" href=\"/assets/public-ui.css?v=11\">","<style>"+resource("static/assets/public-ui.css")+"</style>");
            return content;
        }
    }
}
