package it.unicam.locationbasedgame.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Api for the BEE engine.
 */
@Component
@Slf4j
public class BeeClient {

    @Value("${bee.base-url:http://localhost:8082}")
    private String beeBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Represents a place in the BEE environment, with its attributes.
     */
    @Getter
    public static class BeePlace {

        private final String id;
        private final String name;
        private final Map<String, String> attributes;

        public BeePlace(String id, String name, Map<String, String> attributes) {
            this.id = id;
            this.name = name;
            this.attributes = attributes;
        }

        /**
         * Reads an attribute as a number.
         *
         * @param key the name of the attribute
         * @param fallback the value to use when it is missing
         * @return the value of the attribute, or the fallback
         */
        public int getInt(String key, int fallback) {
            String value = attributes.get(key);
            if (value == null || value.isBlank()) {
                return fallback;
            }
            try {
                return (int) Double.parseDouble(value.trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        /**
         * Reads an attribute as text.
         *
         * @param key the name of the attribute
         * @param fallback the value to use when it is missing
         * @return the value of the attribute, or the fallback
         */
        public String getText(String key, String fallback) {
            String value = attributes.get(key);
            return (value == null || value.isBlank()) ? fallback : value.trim();
        }
    }

    /**
     * Writes one attribute onto the matching place in BEE.
     *
     * @param placeId the id of the place in the BEE environment
     * @param key the name of the attribute
     * @param value the new value of the attribute
     */
    public void updatePlaceAttribute(String placeId, String key, String value) {
        String url = beeBaseUrl + "/api/environment/pps/" + placeId + "/attributes/" + key;
        try {
            restTemplate.put(url, Map.of("value", value));
            log.info("[BeeClient] Place " + placeId + " attribute " + key + " set to " + value);
        } catch (RestClientException e) {
            log.warn("[BeeClient] Could not set " + key + " on " + placeId + ": " + e.getMessage());
        }
    }

    /**
     * Writes the new status onto the matching place in BEE.
     *
     * @param placeId the id of the place in the BEE environment
     * @param status the new value, one of neutral, team1, team2
     */
    public void updatePlaceStatus(String placeId, String status) {
        updatePlaceAttribute(placeId, "status", status);
    }

    /**
     * Reads the places that the deployed map puts in the given view.
     *
     * @param viewName the name of the view listing the outposts
     * @return the places of that view, with their attributes
     */
    public List<BeePlace> getPlacesInView(String viewName) {
        List<Object> placeIds = readList(
                beeBaseUrl + "/api/environment/maps/views/" + viewName + "/places");
        List<Object> places = readList(beeBaseUrl + "/api/environment/pp");

        List<BeePlace> result = new ArrayList<>();
        for (Object item : places) {
            Map<String, Object> place = asMap(item);
            Object id = place.get("id");
            if (id == null || !placeIds.contains(id)) {
                continue;
            }

            Map<String, String> attributes = new HashMap<>();
            for (Map.Entry<String, Object> entry : asMap(place.get("attributes")).entrySet()) {
                attributes.put(entry.getKey(), String.valueOf(entry.getValue()));
            }

            Object name = place.get("name");
            result.add(new BeePlace(id.toString(),
                    name == null ? id.toString() : name.toString(), attributes));
        }
        return result;
    }

    /**
     * Says which place a participant is standing in.
     *
     * @param participantId the pool whose token is being located
     * @return the id of the place, or null when it is nowhere known
     */
    public String getParticipantPlaceId(String participantId) {
        if (participantId == null || participantId.isBlank()) {
            return null;
        }

        Object position = readData(
                beeBaseUrl + "/api/environment/participants/" + participantId + "/position");
        if (position == null) {
            return null;
        }

        String where = position.toString();
        for (Object item : readList(beeBaseUrl + "/api/environment/pp")) {
            Map<String, Object> place = asMap(item);
            Object id = place.get("id");
            Object name = place.get("name");
            if (id != null && (where.equals(id.toString())
                    || (name != null && where.equals(name.toString())))) {
                return id.toString();
            }
        }
        return null;
    }

    /** Reads the data part of a BEE response, whatever shape it has. */
    private Object readData(String url) {
        Map<String, Object> body = readEngineMap(url);
        if (!Boolean.TRUE.equals(body.get("success"))) {
            throw new IllegalStateException("BEE refused " + url + ": " + body.get("message"));
        }
        return body.get("data");
    }

    /**
     * Reads a BEE response and returns the data as a list.
     */
    private List<Object> readList(String url) {
        Map<String, Object> body = readEngineMap(url);
        if (!Boolean.TRUE.equals(body.get("success"))) {
            throw new IllegalStateException("BEE refused " + url + ": " + body.get("message"));
        }
        Object data = body.get("data");
        return data instanceof List ? castList(data) : List.of();
    }

    /** Reads a JSON object from BEE. */
    private Map<String, Object> readEngineMap(String url) {
        try {
            Map<String, Object> body = castMap(restTemplate.getForObject(url, Map.class));
            if (body == null) {
                throw new IllegalStateException("Empty answer from " + url);
            }
            return body;
        } catch (RestClientException e) {
            throw new IllegalStateException("Could not read " + url + ": " + e.getMessage());
        }
    }

    /** Reads a value that is expected to be a JSON object. */
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? castMap(value) : Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Object> castList(Object value) {
        return (List<Object>) value;
    }
}