package com.example.project1.controller;

import com.example.project1.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@CrossOrigin
public class ChatController {
    private final ChatService chatService;
    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }
    @PostMapping(value = "/api/text-chat",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> textChat(@RequestBody Map<String, String> map){
        String text = map.get("text");
        // ★ 与前端 App.vue SSE 解析对齐：data: [DONE] 表示流结束
        return chatService.chatStream(text).concatWithValues("[DONE]");
    }
}
