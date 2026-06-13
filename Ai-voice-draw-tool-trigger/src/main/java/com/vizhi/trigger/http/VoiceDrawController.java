package com.vizhi.trigger.http;

import com.baidu.aip.speech.AipSpeech;
import com.vizhi.api.response.Response;

import com.vizhi.domain.agent.service.IArmoryService;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/draw/")
public class VoiceDrawController {

    @Autowired
    private IArmoryService armoryService;
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
            @RequestParam(value = "agentId", defaultValue = "100001") String agentId) {

        Map<String, Object> result = new HashMap<>();

        // 1. 语音识别(STT) — 这里替换成实际的STT服务
        String text = sttRecognize(voiceFile);
        if (!StringUtils.hasText(text)) {
            return Response.<Map<String, Object>>builder()
                    .code("0002")
                    .info("语音识别失败，未获取到文本")
                    .data(result)
                    .build();
        }
        log.info("语音识别结果: {}", text);

        // 2. 调用Agent（含子Agent纠错 + 生成XML）
        String xml = armoryService.runAgent(agentId, text);
        log.info("Agent输出XML长度: {}", xml.length());

        // 3. 清理可能的 Markdown 代码块标记
        xml = cleanXml(xml);

        result.put("xml", xml);
        result.put("inputText", text);

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
            @RequestParam(value = "agentId", defaultValue = "100001") String agentId) {

        Map<String, Object> result = new HashMap<>();

        if (!StringUtils.hasText(text)) {
            return Response.<Map<String, Object>>builder()
                    .code("0002")
                    .info("文本不能为空")
                    .data(result)
                    .build();
        }

        String xml = armoryService.runAgent(agentId, text);
        xml = cleanXml(xml);

        result.put("xml", xml);
        result.put("inputText", text);

        return Response.<Map<String, Object>>builder()
                .code("0000")
                .info("成功")
                .data(result)
                .build();
    }

    /**
     * STT语音识别 — 替换成你的STT服务
     * 示例：阿里云、讯飞、Whisper等
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
     * 清理可能存在的 Markdown 代码块标记
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