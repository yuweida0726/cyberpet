package com.example.project1.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ChatService {
    //日志工具
    Logger logger = LoggerFactory.getLogger(ChatService.class);
    //创建SpringAI的顶层客户端，封装所有对话逻辑
    private final ChatClient chatClient;
    public ChatService(ChatClient.Builder builder, @Value("${bailian.system-prompt}")String systemPrompt) {
    //修改一下传入Ai的人设
        this.chatClient = builder
                .defaultSystem(systemPrompt)
                .build();
    }
    //核心业务，完成对话功能 响应式流 流式
    public Flux<String> chatStream(String userText) {
        return chatClient.prompt()//构建对话请求处理
                .user(userText)//传入用户的消息
                .stream()//开启流式
                .content()//只提取Ai的回复的片段
                .doOnError((Throwable e) -> logger.error("AI请求异常", e));
    }
}
