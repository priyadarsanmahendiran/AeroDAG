package com.aerodag.core.service.executor;

import java.util.Map;
import java.util.function.Function;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.web.client.RestClient;

@Configuration
public class AeroDagToolRegistry {

  private static final Logger log = LoggerFactory.getLogger(AeroDagToolRegistry.class);

  private final RestClient restClient;

  @Value("${aerodag.tools.serper.api-key:}")
  private String serperApiKey;

  public AeroDagToolRegistry(RestClient.Builder restClientBuilder) {
    this.restClient = restClientBuilder.baseUrl("https://google.serper.dev").build();
  }

  public record WebSearchRequest(String query) {}

  public record CalculatorRequest(String expression) {}

  @Bean
  @Description("Searches the web for the given query.")
  public Function<WebSearchRequest, String> webSearchTool() {
    return request -> {
      try {
        return restClient
            .post()
            .uri("/search")
            .header("X-API-KEY", serperApiKey)
            .header("Content-Type", "application/json")
            .body(Map.of("q", request.query()))
            .retrieve()
            .body(String.class);
      } catch (Exception e) {
        log.error("Web search failed for query '{}': {}", request.query(), e.getMessage());
        return "Error fetching search results";
      }
    };
  }

  @Bean
  @Description("Evaluates mathematical expressions.")
  public Function<CalculatorRequest, String> calculatorTool() {
    return request -> {
      try {
        double result = new ExpressionBuilder(request.expression()).build().evaluate();
        return String.valueOf(result);
      } catch (IllegalArgumentException e) {
        log.warn("Invalid math expression '{}': {}", request.expression(), e.getMessage());
        return "Error: Invalid math expression";
      }
    };
  }
}
