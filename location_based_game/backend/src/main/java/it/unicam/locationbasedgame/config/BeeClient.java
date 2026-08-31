package it.unicam.locationbasedgame.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Api for the BEE engine.
 */
@Component
@Slf4j
public class BeeClient {

    @Value("${bee.base-url:http://localhost:8082}")
    private String beeBaseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    /**
     * Writes the new status onto the matching place in BEE.
     *
     * @param placeId the id of the place in the BEE environment
     * @param status the new value, one of neutral, team1, team2
     */
    public void updatePlaceStatus(String placeId, String status) {
        String url = String.format("%s/api/environment/pps/%s/attributes/status",
                beeBaseUrl, placeId);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"value\":\"" + status + "\"}"))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[BeeClient] Place {} set to status {}", placeId, status);
            } else {
                log.warn("[BeeClient] BEE refused the status update for {}: HTTP {}",
                        placeId, response.statusCode());
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("[BeeClient] Could not reach BEE to update {}: {}", placeId, e.getMessage());
        }
    }
}
