package org.example.context;

import com.alibaba.cloud.ai.graph.agent.interceptor.*;
import org.example.context.budget.*;
import org.springframework.ai.chat.messages.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContextBudgetInterceptorTest {
    @Test
    void capsToolPayloadButPreservesToolCallIdentity() {
        ContextManagerConfig config = new ContextManagerConfig();
        config.setToolReserve(1000);
        ContextBudgetInterceptor interceptor = new ContextBudgetInterceptor(config, new TokenEstimator());
        ModelRequest request = ModelRequest.builder().systemMessage(new SystemMessage("base"))
                .messages(List.of(new UserMessage("question"), ToolResponseMessage.builder().responses(List.of(
                        new ToolResponseMessage.ToolResponse("call-a", "readFile", "x".repeat(20000)))).build())).build();
        ModelCallHandler handler = mock(ModelCallHandler.class);
        interceptor.interceptModel(request, handler);
        var captor = org.mockito.ArgumentCaptor.forClass(ModelRequest.class);
        verify(handler).call(captor.capture());
        assertEquals("base", captor.getValue().getSystemMessage().getText());
        var response = ((ToolResponseMessage) captor.getValue().getMessages().get(1)).getResponses().get(0);
        assertEquals("call-a", response.id());
        assertEquals("readFile", response.name());
        assertTrue(response.responseData().contains("TOOL_RESULT_TRUNCATED"));
        assertTrue(new TokenEstimator().estimate(response.responseData()) < 1000);
    }

    @Test
    void refusesOversizedNonToolContextInsteadOfDroppingInstructions() {
        ModelCallHandler handler = mock(ModelCallHandler.class);
        ModelRequest request = ModelRequest.builder().systemMessage(new SystemMessage("x".repeat(40000)))
                .messages(List.of(new UserMessage("question"))).build();
        assertThrows(IllegalStateException.class, () -> new ContextBudgetInterceptor(new ContextManagerConfig(),
                new TokenEstimator()).interceptModel(request, handler));
        verifyNoInteractions(handler);
    }
}
