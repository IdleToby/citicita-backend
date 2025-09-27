package com.citacita.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatRequest {
    private List<ChatMessage> messages;
    private String model;
    private boolean stream;
    private String language;
    private RagConfig ragConfig;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ChatMessage {
        private String role;
        private String content;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RagConfig {
        private boolean enabled;
        private String knowledgeBase;
        private RetrievalContext retrievalContext;
        private List<String> retrievalFilters;

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class RetrievalContext {
            private String resumeFile;
            private String userQuery;
            private List<ChatMessage> previousContext;
        }
    }
}