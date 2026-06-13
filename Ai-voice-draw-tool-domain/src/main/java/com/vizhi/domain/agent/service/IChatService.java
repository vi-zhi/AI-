package com.vizhi.domain.agent.service;

import com.google.adk.events.Event;
import com.vizhi.domain.agent.model.entity.ChatCommandEntity;
import com.vizhi.domain.agent.model.valobj.AiAgentConfigTableVO;
import io.reactivex.rxjava3.core.Flowable;

import java.util.List;

/**
 * @author vizhi
 * 描述 :
 * @create 2026-05-08 20:07
 */
public interface IChatService {

    List<AiAgentConfigTableVO.Agent> queryAiAgentConfigList();

    String createSession(String agentId , String userId);

    List<String> handleMessage(String agentId , String userId , String message);

    List<String> handleMessage(String agentId , String userId , String sessionId , String message);

    Flowable<Event> handleMessageStream (String agentId , String userId , String sessionId , String message);

    List<String> handleMessage(ChatCommandEntity chatCommandEntity);


}
