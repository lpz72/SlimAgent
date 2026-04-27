package org.example.agent.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@Configuration
public class ToolConfig {

    @Bean
    public ToolCallback[] toolCallback(
            ToolCallback[] localTools,
            ToolCallbackProvider mcpTools
    ) {

        ToolCallback[] mcpArray = mcpTools.getToolCallbacks();

        return Stream.concat(
                Arrays.stream(localTools),
                Arrays.stream(mcpArray)
        ).toArray(ToolCallback[]::new);
    }
}