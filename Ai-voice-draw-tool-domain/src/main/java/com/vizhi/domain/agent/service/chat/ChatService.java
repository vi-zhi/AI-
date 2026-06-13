package com.vizhi.domain.agent.service.chat;

import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.vizhi.domain.agent.model.entity.ChatCommandEntity;
import com.vizhi.domain.agent.model.valobj.AiAgentConfigTableVO;
import com.vizhi.domain.agent.model.valobj.AiAgentRegisterVO;
import com.vizhi.domain.agent.model.valobj.properties.AiAgentAutoConfigProperties;
import com.vizhi.domain.agent.service.IChatService;
import com.vizhi.domain.agent.service.armory.factory.DefaultArmoryFactory;
import com.vizhi.types.enums.ResponseCode;
import com.vizhi.types.exception.AppException;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author vizhi
 * 描述 :
 * @create 2026-05-08 20:08
 */
@Service
@Slf4j
public class ChatService implements IChatService {

    @Resource
    private AiAgentAutoConfigProperties aiAgentAutoConfigProperties;

    @Resource
    private DefaultArmoryFactory defaultArmoryFactory;

    private final Map<String , String> userSession = new HashMap<>();

    private String buildUserSessionKey(String userId , String agentId){
        return userId + ":" + agentId;
    }
    @Override
    public List<AiAgentConfigTableVO.Agent> queryAiAgentConfigList() {

        Map<String, AiAgentConfigTableVO> tables = aiAgentAutoConfigProperties.getTables();

        List<AiAgentConfigTableVO.Agent> agentList = new ArrayList<>();

        if (tables != null) {
            for (AiAgentConfigTableVO table : tables.values()) {
                if (table != null) {
                    agentList.add(table.getAgent());
                }
            }
        }

        return agentList;
    }

    @Override
    public String createSession(String agentId, String userId) {

        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);

        if(aiAgentRegisterVO == null){
            throw new AppException(ResponseCode.E0001.getCode() , ResponseCode.E0001.getInfo());
        }

        InMemoryRunner runner = aiAgentRegisterVO.getRunner();
        String appName = aiAgentRegisterVO.getAppName();

        return userSession.computeIfAbsent(buildUserSessionKey(userId,agentId) , uid -> {
            Session session = runner.sessionService().createSession(appName , userId).blockingGet();
            return session.id();
        });
    }

    @Override
    public List<String> handleMessage(String agentId, String userId, String message) {
        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);

        if(aiAgentRegisterVO == null){
            throw new AppException(ResponseCode.E0001.getCode() , ResponseCode.E0001.getInfo());
        }

        String sessionId = createSession(agentId, userId);

        return handleMessage(agentId, userId, sessionId, message);
    }

    @Override
    public List<String> handleMessage(String agentId, String userId, String sessionId, String message) {
        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);

        if(aiAgentRegisterVO == null){
            throw new AppException(ResponseCode.E0001.getCode() , ResponseCode.E0001.getInfo());
        }

        InMemoryRunner runner = aiAgentRegisterVO.getRunner();

        Content content = Content.fromParts(Part.fromText(message));
        Flowable<Event> events = runner.runAsync(userId, sessionId, content);

        List<String> outputs = new ArrayList<>();
        events.blockingForEach(event -> outputs.add(event.stringifyContent()));

        return outputs;
    }

    @Override
    public Flowable<Event> handleMessageStream(String agentId, String userId, String sessionId, String message) {
        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(agentId);

        if(aiAgentRegisterVO == null){
            throw new AppException(ResponseCode.E0001.getCode() , ResponseCode.E0001.getInfo());
        }

        InMemoryRunner runner = aiAgentRegisterVO.getRunner();

        Content content = Content.fromParts(Part.fromText(message));

        return runner.runAsync(userId, sessionId, content);
    }

    @Override
    public List<String> handleMessage(ChatCommandEntity chatCommandEntity) {
        AiAgentRegisterVO aiAgentRegisterVO = defaultArmoryFactory.getAiAgentRegisterVO(chatCommandEntity.getAgentId());

        if (null == aiAgentRegisterVO) {
            throw new AppException(ResponseCode.E0001.getCode());
        }

        List<Part> parts = new ArrayList<>();

        List<ChatCommandEntity.Context.Text> texts = chatCommandEntity.getTexts();
        if(texts != null && !texts.isEmpty()){
            for(ChatCommandEntity.Context.Text text :texts){
                parts.add(Part.fromText(text.getMessage()));
            }
        }

        List<ChatCommandEntity.Context.File> files = chatCommandEntity.getFiles();
        if(files != null && !files.isEmpty()){
            for(ChatCommandEntity.Context.File file : files){
                parts.add(Part.fromUri(file.getFileUri() , file.getMimeType()));
            }
        }

        List<ChatCommandEntity.Context.InlineData> InlineDatas = chatCommandEntity.getInlineDatas();
        if(InlineDatas != null && !InlineDatas.isEmpty()){
            for(ChatCommandEntity.Context.InlineData InlineData : InlineDatas){
                parts.add(Part.fromBytes(InlineData.getBytes() , InlineData.getMimeType()));
            }
        }

        String userId = chatCommandEntity.getUserId();
        String sessionId = chatCommandEntity.getSessionId();

        Content content = Content.builder().role("user").parts(parts).build();

        InMemoryRunner runner = aiAgentRegisterVO.getRunner();

        Flowable<Event> eventFlowable = runner.runAsync(userId, sessionId, content);

        List<String> outputs = new ArrayList<>();
        eventFlowable.blockingForEach(event -> outputs.add(event.stringifyContent()));
        return outputs;
    }
}
