package org.example.context.model;

public record ContextMessage(long sequence, String role, String content) {
    public String render() {
        return role + ": " + content + "\n";
    }
}
