package com.otboo.virtualtryon.client;

import com.otboo.common.http.ExternalApiClient;
import com.otboo.common.http.ExternalApiClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FashnClient {

    private final ExternalApiClient api;

    public FashnClient(ExternalApiClientFactory factory,
                       @Value("${otboo.fashn.api-key}") String apiKey) {
        this.api = factory.create("virtual-try-on", builder -> builder
            .baseUrl("https://api.fashn.ai")
            .defaultHeader("Authorization", "Bearer " + apiKey));
    }

    public String predict(String modelImage, String productImage) {
        FashnRunResponse response = api.post("/v1/run",
            FashnRunRequest.of(modelImage, productImage), FashnRunResponse.class);
        return response.id();
    }

    public FashnStatusResponse getStatus(String predictionId) {
        return api.get("/v1/status/" + predictionId, FashnStatusResponse.class);
    }
}
