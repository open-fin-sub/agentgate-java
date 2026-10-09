package com.abchina.llmalf.agentgate;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AgentGate 应用入口.
 *
 * <p>扫描 DAO 接口包,遵循后端分层规范(Controller -> Service -> Logic -> DAO).</p>
 */
@EnableScheduling
@SpringBootApplication
@MapperScan("com.abchina.llmalf.**.dao")
public class AgentGateApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentGateApplication.class, args);
    }
}
