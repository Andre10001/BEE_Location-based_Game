package org.unicam.intermediate.service.environmental;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;

@Service
@Slf4j
@AllArgsConstructor
public class TemperatureService {

    private final EnvironmentDataService environmentDataService;

    public double fetchTemperatureFromEndpoint(String endpoint) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");

        int status = con.getResponseCode();
        if (status != 200) {
            throw new IllegalStateException("HTTP status " + status);
        }

        try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            StringBuilder content = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }

            String response = content.toString().replaceAll("[\\[\\]]", "").trim();
            return Double.parseDouble(response);
        }
    }

    public double getTemperatureFromPlace(String placeId){
        try {
            Optional<Object> temperatureValue = environmentDataService.getPhysicalPlaceAttribute(placeId, "temperature");
            if (temperatureValue.isEmpty()) {
                throw new IllegalStateException("Temperature attribute not found for place with id " + placeId + " not found");
            }

            String temperature = String.valueOf(temperatureValue.get());

            if (temperature.startsWith("http://") || temperature.startsWith("https://") || temperature.isBlank()) {
                return fetchTemperatureFromEndpoint(temperature);
            } else {
                return Double.parseDouble(temperature);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


}
