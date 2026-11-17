package cn.training.evaluation.modules.evaluation;

import cn.training.evaluation.common.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class EvaluationController {
    private final EvaluationService service;
    public EvaluationController(EvaluationService service){this.service=service;}
    @GetMapping("/api/public/task") public ApiResponse<?> task(@RequestParam String token){return ApiResponse.ok(service.publicTask(token));}
    @PostMapping("/api/public/submit") public ApiResponse<?> submit(@RequestBody Map<String,Object> data){return ApiResponse.ok(service.submit(data,false));}
    @PostMapping("/api/business/paper") public ApiResponse<?> paper(@RequestBody Map<String,Object> data){return ApiResponse.ok(service.submit(data,true));}
}
