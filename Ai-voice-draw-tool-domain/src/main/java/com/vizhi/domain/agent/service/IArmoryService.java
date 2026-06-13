package com.vizhi.domain.agent.service;

import com.vizhi.domain.agent.model.valobj.AiAgentConfigTableVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * @author vizhi
 * 描述 :
 * @create 2026-06-13 16:11
 */
public interface IArmoryService {
    void acceptArmoryAgents(ArrayList<AiAgentConfigTableVO> aiAgentConfigTableVOS) throws Exception;
}
