package com.vizhi.trigger.http;

import com.baidu.aip.speech.AipSpeech;
import com.vizhi.api.response.Response;
import com.vizhi.domain.agent.service.IChatService;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/draw/")
public class VoiceDrawController {

    @Autowired
    private IChatService chatService;

    @Value("${baidu.app-id}")
    private String APP_ID;

    @Value("${baidu.api-key}")
    private String API_KEY;

    @Value("${baidu.secret-key}")
    private String SECRET_KEY;

    /**
     * 语音画图接口
     * 流程: 语音文件 → STT识别 → 文本 → Agent → XML
     */
    @PostMapping("voice")
    public Response<Map<String, Object>> voiceDraw(
            @RequestParam("file") MultipartFile voiceFile,
            @RequestParam(value = "agentId", defaultValue = "100001") String agentId,
            @RequestParam(value = "userId", defaultValue = "default") String userId) {

        Map<String, Object> result = new HashMap<>();

        // 1. 语音识别(STT)
        String text = sttRecognize(voiceFile);
        if (!StringUtils.hasText(text)) {
            return Response.<Map<String, Object>>builder()
                    .code("0002")
                    .info("语音识别失败，未获取到文本")
                    .data(result)
                    .build();
        }
        log.info("语音识别结果: {}", text);

        // 2. 调用ChatService (有session状态,支持多轮对话)
        List<String> replies = chatService.handleMessage(agentId, userId, text);
        log.info("Agent回复条数: {}", replies.size());

        result.put("replies", replies);
        result.put("inputText", text);
        result.put("type", "chat");

        return Response.<Map<String, Object>>builder()
                .code("0000")
                .info("成功")
                .data(result)
                .build();
    }

    /**
     * 纯文本画图接口（前端可能直接传文字）
     */
    @PostMapping("text")
    public Response<Map<String, Object>> textDraw(
            @RequestParam("text") String text,
            @RequestParam(value = "agentId", defaultValue = "100001") String agentId,
            @RequestParam(value = "userId", defaultValue = "default") String userId) {

        Map<String, Object> result = new HashMap<>();

        if (!StringUtils.hasText(text)) {
            return Response.<Map<String, Object>>builder()
                    .code("0002")
                    .info("文本不能为空")
                    .data(result)
                    .build();
        }

        List<String> replies = chatService.handleMessage(agentId, userId, text);

        result.put("replies", replies);
        result.put("inputText", text);
        result.put("type", "chat");

        return Response.<Map<String, Object>>builder()
                .code("0000")
                .info("成功")
                .data(result)
                .build();
    }

    /**
     * STT语音识别
     */
    private String sttRecognize(MultipartFile file) {
        try {

            // 1. 初始化客户端
            AipSpeech client = new AipSpeech(APP_ID, API_KEY, SECRET_KEY);

            // 2. (可选) 设置网络参数，防止请求超时
            client.setConnectionTimeoutInMillis(2000);
            client.setSocketTimeoutInMillis(60000);

            // 3. 从 MultipartFile 中获取音频字节数组
            byte[] audioData = file.getBytes();

            HashMap<String, Object> options = new HashMap<>();
            options.put("dev_pid", 1537);  // 1537 = 普通话(纯中文识别，有标点)
            JSONObject result = client.asr(audioData, "pcm", 16000, options);

            // 5. 从返回的 JSON 中提取识别结果
            return result.getJSONArray("result").getString(0);

        } catch (Exception e) {
            log.error("STT识别失败", e);
            return null;
        }
    }

    /**
     * 清理可能存在的 Markdown 代码块标记 (保留,供后续需要时使用)
     */
    private String cleanXml(String xml) {
        if (xml == null) return null;
        xml = xml.trim();
        // 去掉 ```xml ... ``` 或 ``` ... ```
        if (xml.startsWith("```")) {
            xml = xml.substring(3).trim();
            if (xml.endsWith("```")) {
                xml = xml.substring(0, xml.length() - 3).trim();
            }
        }
        return xml;
    }
}