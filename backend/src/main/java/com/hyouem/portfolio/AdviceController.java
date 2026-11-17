package com.hyouem.portfolio;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/advice")
public class AdviceController {
  private final FinanceController finance;
  private final ObjectMapper json;
  private final String key;
  private final String model;
  AdviceController(FinanceController finance,ObjectMapper json,
      @Value("${OPENAI_API_KEY:}") String key,@Value("${OPENAI_MODEL:gpt-4.1-mini}") String model){
    this.finance=finance;this.json=json;this.key=key;this.model=model;
  }
  @PostMapping("/ai")
  ResponseEntity<?> ai(Authentication auth,@RequestParam String month) {
    var dashboard=finance.dashboard(auth,month);
    if(key.isBlank())return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error","AI provider not configured; financial dashboard remains available","fallback",dashboard.get("recommendation")));
    try {
      // Minimized payload: only aggregate figures. No names, emails, notes or transactions.
      String prompt="Analise os totais agregados do mes, em portugues, sem prometer retornos e sem recomendar produtos financeiros especificos. Mes: "+dashboard.get("month")+
        "; receitas: "+dashboard.get("income")+"; despesas: "+dashboard.get("expense")+"; resultado: "+dashboard.get("net")+"; taxa poupanca: "+dashboard.get("savings_rate")+"%. Gere 3 acoes prudentes e curtas.";
      String body=json.writeValueAsString(Map.of("model",model,"messages",new Object[]{Map.of("role","user","content",prompt)},"temperature",0.2,"max_tokens",250));
      var request=HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/chat/completions"))
        .header("Authorization","Bearer "+key).header("Content-Type","application/json")
        .timeout(Duration.ofSeconds(12)).POST(HttpRequest.BodyPublishers.ofString(body)).build();
      var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
      if(response.statusCode()!=200)throw new IllegalStateException("AI unavailable: HTTP "+response.statusCode());
      JsonNode parsed=json.readTree(response.body());
      String content=parsed.path("choices").path(0).path("message").path("content").asText("");
      if(content.isBlank())throw new IllegalStateException("Empty AI response");
      return ResponseEntity.ok(Map.of("advice",content,"source","ai","data_shared","Monthly aggregate figures only"));
    }catch(Exception e){
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error","AI temporarily unavailable","fallback",dashboard.get("recommendation")));
    }
  }
}
