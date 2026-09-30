package com.otboo.virtualtryon.client;

import com.otboo.common.http.ExternalApiClient;
import com.otboo.common.http.ExternalApiClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FashnClient {

    private final ExternalApiClient api;
    private final String webhookUrl;

    public FashnClient(ExternalApiClientFactory factory,
                       @Value("${otboo.fashn.api-key}") String apiKey,
                       @Value("${otboo.virtual-try-on.webhook-base-url:}") String webhookBaseUrl,
                       @Value("${otboo.virtual-try-on.webhook-token:}") String webhookToken) {
        this.api = factory.create("virtual-try-on", builder -> builder
            .baseUrl("https://api.fashn.ai")
            .defaultHeader("Authorization", "Bearer " + apiKey));
        this.webhookUrl = webhookBaseUrl.isBlank() || webhookToken.isBlank()
            ? null
            : webhookBaseUrl + "/api/fittings/webhook/" + webhookToken;
    }

    public String predict(String modelImage, String productImage, String prompt) {
        FashnRunRequest body = FashnRunRequest.of(modelImage, productImage, prompt);
        if (webhookUrl == null) {
            return api.post("/v1/run", body, FashnRunResponse.class).id();
        }
        // 웹훅 URL 에 토큰이 들어 있어서 로그에는 경로만 남긴다
        FashnRunResponse response = api.exchange("POST /v1/run", client -> client.post()
            .uri(uri -> uri.path("/v1/run").queryParam("webhook_url", "{url}").build(webhookUrl))
            .body(body)
            .retrieve()
            .body(FashnRunResponse.class));
        return response.id();
    }

    public FashnStatusResponse getStatus(String predictionId) {
        return api.get("/v1/status/" + predictionId, FashnStatusResponse.class);
    }
}
