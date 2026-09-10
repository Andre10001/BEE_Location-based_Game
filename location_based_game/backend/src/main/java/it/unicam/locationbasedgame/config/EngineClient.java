package it.unicam.locationbasedgame.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Api for the process engine.
 */
@Component
@Slf4j
public class EngineClient {

    private static final DateTimeFormatter ENGINE_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    private static final String ENGINE_BASE_URL = "http://localhost:8082";

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * The running instance of a player, or null when they have none.
     *
     * @param businessKey the business key the instance was started with
     * @return the id of the instance, or null
     */
    public String findInstanceId(String businessKey) {
        try {
            List<Object> instances = castList(restTemplate.getForObject(
                    ENGINE_BASE_URL + "/engine-rest/process-instance?active=true&businessKey="
                            + businessKey, List.class));
            if (instances == null || instances.isEmpty()) {
                return null;
            }
            Object id = asMap(instances.get(0)).get("id");
            return id == null ? null : id.toString();
        } catch (RestClientException e) {
            throw new IllegalStateException("Could not ask the engine about " + businessKey
                    + ": " + e.getMessage());
        }
    }

    /**
     * How long a token still has to sit on a given timer.
     *
     * @param businessKey the business key of the instance
     * @param activityId the id of the timer event in the diagram
     * @return the seconds left, or 0 when that timer is not running
     */
    public int secondsLeftOn(String businessKey, String activityId) {
        if (activityId == null) {
            return 0;
        }
        String instanceId = findInstanceId(businessKey);
        if (instanceId == null) {
            return 0;
        }

        List<Object> jobs;
        try {
            jobs = castList(restTemplate.getForObject(
                    ENGINE_BASE_URL + "/engine-rest/job?timers=true&processInstanceId=" + instanceId
                            + "&activityId=" + activityId,
                    List.class));
        } catch (RestClientException e) {
            throw new IllegalStateException("Could not read the timers of " + businessKey
                    + ": " + e.getMessage());
        }
        if (jobs == null) {
            return 0;
        }

        for (Object item : jobs) {
            Map<String, Object> job = asMap(item);
            Object dueDate = job.get("dueDate");
            if (dueDate == null) {
                continue;
            }
            long left = toInstant(dueDate.toString()).getEpochSecond()
                    - Instant.now().getEpochSecond();
            log.info("[EngineClient] " + activityId + " dueDate= " + dueDate);
            return left > 0 ? (int) left : 0;
        }
        return 0;
    }


    /**
     * Reads the BPMN currently deployed.
     *
     * @return the BPMN as text
     */
    public String getDeployedBpmn() {
        List<Object> definitions;
        try {
            definitions = castList(restTemplate.getForObject(
                    ENGINE_BASE_URL + "/engine-rest/process-definition?latestVersion=true",
                    List.class));
        } catch (RestClientException e) {
            throw new IllegalStateException("Could not reach the engine: " + e.getMessage());
        }
        if (definitions == null || definitions.isEmpty()) {
            throw new IllegalStateException("No process is deployed in the engine");
        }

        Object definitionId = asMap(definitions.get(0)).get("id");
        try {
            Map<String, Object> answer = castMap(restTemplate.getForObject(
                    ENGINE_BASE_URL + "/engine-rest/process-definition/" + definitionId + "/xml",
                    Map.class));
            Object xml = answer == null ? null : answer.get("bpmn20Xml");
            if (xml == null) {
                throw new IllegalStateException("The engine returned no BPMN for " + definitionId);
            }
            return xml.toString();
        } catch (RestClientException e) {
            throw new IllegalStateException("Could not read the deployed BPMN: " + e.getMessage());
        }
    }

    /** Reads a date the way the engine writes it. */
    private Instant toInstant(String text) {
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (Exception ignored) {
            return OffsetDateTime.parse(text, ENGINE_DATE).toInstant();
        }
    }

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