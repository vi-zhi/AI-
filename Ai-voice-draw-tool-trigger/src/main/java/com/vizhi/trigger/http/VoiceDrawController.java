package com.vizhi.trigger.http;

import com.vizhi.api.response.Response;
import com.vizhi.domain.agent.model.entity.ChatCommandEntity;
import com.vizhi.domain.agent.service.IChatService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/draw/")
@Configuration
public class VoiceDrawController implements WebMvcConfigurer {
    @Value("${baidu.app-id}")
    private String baiduAppId;

    @Value("${baidu.api-key}")
    private String baiduApiKey;

    @Value("${baidu.secret-key}")
    private String baiduSecretKey;

    @Autowired
    private IChatService chatService;

    /**
     * 添加 CORS 支持
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    // ==================== 语音/文本画图接口 ====================

    @PostMapping("voice")
    public Response<Map<String, Object>> voiceDraw(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile voiceFile,
            @RequestParam(value = "agentId", defaultValue = "100001") String agentId,
            @RequestParam(value = "userId", defaultValue = "default") String userId) {

        Map<String, Object> result = new HashMap<>();

        String text = sttRecognize(voiceFile);
        if (!org.springframework.util.StringUtils.hasText(text)) {
            return Response.<Map<String, Object>>builder()
                    .code("0002")
                    .info("语音识别失败，未获取到文本")
                    .data(result)
                    .build();
        }
        log.info("语音识别结果: {}", text);

        List<String> replies = chatService.handleMessage(agentId, userId, text);
        log.info("Agent回复条数: {}", replies.size());

        result.put("replies", replies);
        result.put("inputText", text);
        result.put("type", "voice");

        return Response.<Map<String, Object>>builder()
                .code("0000")
                .info("成功")
                .data(result)
                .build();
    }

    @PostMapping("text")
    public Response<Map<String, Object>> textDraw(
            @RequestParam("text") String text,
            @RequestParam(value = "agentId", defaultValue = "100001") String agentId,
            @RequestParam(value = "userId", defaultValue = "default") String userId) {

        Map<String, Object> result = new HashMap<>();

        if (!org.springframework.util.StringUtils.hasText(text)) {
            return Response.<Map<String, Object>>builder()
                    .code("0002")
                    .info("文本不能为空")
                    .data(result)
                    .build();
        }

        List<String> replies = chatService.handleMessage(agentId, userId, text);

        result.put("replies", replies);
        result.put("inputText", text);
        result.put("type", "text");

        return Response.<Map<String, Object>>builder()
                .code("0000")
                .info("成功")
                .data(result)
                .build();
    }

    // ==================== 聊天接口 ====================

    /**
     * 创建会话
     */
    @PostMapping("chat/session")
    public Response<Map<String, Object>> createSession(
            @RequestBody Map<String, String> req) {
        String agentId = req.getOrDefault("agentId", "100001");
        String userId = req.getOrDefault("userId", "default");

        String sessionId = chatService.createSession(agentId, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("agentId", agentId);
        result.put("userId", userId);

        return Response.<Map<String, Object>>builder()
                .code("0000")
                .info("成功")
                .data(result)
                .build();
    }

    /**
     * 发送消息
     */
    @PostMapping("chat/message")
    public Response<Map<String, Object>> sendMessage(
            @RequestBody Map<String, String> req) {
        String agentId = req.getOrDefault("agentId", "100001");
        String userId = req.getOrDefault("userId", "default");
        String sessionId = req.get("sessionId");
        String message = req.get("message");

        if (!org.springframework.util.StringUtils.hasText(message)) {
            return Response.<Map<String, Object>>builder()
                    .code("0002")
                    .info("消息不能为空")
                    .data(new HashMap<>())
                    .build();
        }

        List<String> replies;
        if (org.springframework.util.StringUtils.hasText(sessionId)) {
            replies = chatService.handleMessage(agentId, userId, sessionId, message);
        } else {
            replies = chatService.handleMessage(agentId, userId, message);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("replies", replies);
        result.put("sessionId", sessionId);

        return Response.<Map<String, Object>>builder()
                .code("0000")
                .info("成功")
                .data(result)
                .build();
    }

    /**
     * STT语音识别
     */
    private String sttRecognize(org.springframework.web.multipart.MultipartFile file) {
        try {
            String APP_ID = System.getProperty(baiduAppId);
            String API_KEY = System.getProperty(baiduApiKey);
            String SECRET_KEY = System.getProperty(baiduSecretKey);

            com.baidu.aip.speech.AipSpeech client = new com.baidu.aip.speech.AipSpeech(APP_ID, API_KEY, SECRET_KEY);

            client.setConnectionTimeoutInMillis(2000);
            client.setSocketTimeoutInMillis(60000);

            byte[] audioData = file.getBytes();
            java.util.HashMap<String, Object> options = new java.util.HashMap<>();
            options.put("dev_pid", 1537);

            org.json.JSONObject result = client.asr(audioData, "pcm", 16000, options);

            log.info("result: {}", result);

            return result.getJSONArray("result").getString(0);

        } catch (Exception e) {
            log.error("STT识别失败", e);
            return null;
        }
    }
}
