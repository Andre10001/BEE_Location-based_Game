package it.unicam.locationbasedgame.service;

import it.unicam.locationbasedgame.config.EngineClient;
import it.unicam.locationbasedgame.service.interfaces.IProcessTimerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of IProcessTimerService.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProcessTimerService implements IProcessTimerService {

    private static final String PENALTY_TIMER = "penality";

    private static final String MATCH_TIMER = "game time";

    private final EngineClient engineClient;

    private record Timers(int penaltySeconds, String gameTimeActivityId) { }

    private final Map<String, Timers> timersByParticipant = new ConcurrentHashMap<>();

    @Override
    public int getPenaltySeconds(String participantId) {
        return timersOf(participantId).penaltySeconds();
    }

    @Override
    public String getGameTimeActivityId(String participantId) {
        return timersOf(participantId).gameTimeActivityId();
    }

    @Override
    public void forget() {
        timersByParticipant.clear();
    }

    /** The timers of one pool. */
    private Timers timersOf(String participantId) {
        if (participantId == null || participantId.isBlank()) {
            throw new IllegalStateException(
                    "This player has no pool, so no process says when their timers run out");
        }

        Timers timers = readAll().get(participantId);
        if (timers == null) {
            throw new IllegalStateException(
                    "The deployed process has no timers for pool " + participantId);
        }
        return timers;
    }

    /** The timers of every pool, read once and kept. */
    private Map<String, Timers> readAll() {
        if (timersByParticipant.isEmpty()) {
            timersByParticipant.putAll(readEveryTimer());
        }
        return timersByParticipant;
    }

    /** Parses the deployed collaboration and finds both timers per pool. */
    private Map<String, Timers> readEveryTimer() {
        Document document = parse(engineClient.getDeployedBpmn());

        Map<String, Element> processesById = new HashMap<>();
        for (Element process : elementsNamed(document, "process")) {
            processesById.put(process.getAttribute("id"), process);
        }

        Map<String, Timers> found = new HashMap<>();
        for (Element participant : elementsNamed(document, "participant")) {
            String participantId = participant.getAttribute("id");
            Element process = processesById.get(participant.getAttribute("processRef"));
            if (process == null) {
                log.warn("[ProcessTimer] Pool " + participantId + " points at a process that is not here");
                continue;
            }

            Element penalty = timerNamed(process, PENALTY_TIMER);
            Element match = timerNamed(process, MATCH_TIMER);
            if (penalty == null || match == null) {
                log.warn("[ProcessTimer] The process of pool " + participantId + " is missing a timer called "
                        + "'" + PENALTY_TIMER + "' or '" + MATCH_TIMER + "'");
                continue;
            }

            Timers timers = new Timers(secondsOf(penalty), match.getAttribute("id"));
            log.info("[ProcessTimer] Pool " + participantId + ": penalty is " + timers.penaltySeconds()
                    + ", game time is " + timers.gameTimeActivityId());
            found.put(participantId, timers);
        }

        if (found.isEmpty()) {
            throw new IllegalStateException("No pool of the deployed process has timers called '"
                    + PENALTY_TIMER + "' and '" + MATCH_TIMER + "': there is no way to tell "
                    + "which timer holds a player back and which one ends the match");
        }
        return found;
    }

    /** The timer event of a process carrying the given name. */
    private Element timerNamed(Element process, String name) {
        for (Element event : elementsNamed(process, "intermediateCatchEvent")) {
            if (name.equalsIgnoreCase(event.getAttribute("name").trim())
                    && !elementsNamed(event, "timerEventDefinition").isEmpty()) {
                return event;
            }
        }
        return null;
    }

    /** Converts the duration of a timer into seconds. */
    private int secondsOf(Element timerEvent) {
        List<Element> durations = elementsNamed(timerEvent, "timeDuration");
        if (durations.isEmpty()) {
            throw new IllegalStateException(
                    "The timer " + timerEvent.getAttribute("id") + " has no fixed duration");
        }

        String written = durations.get(0).getTextContent().trim();
        try {
            return (int) Duration.parse(written).getSeconds();
        } catch (Exception e) {
            throw new IllegalStateException("The timer " + timerEvent.getAttribute("id")
                    + " is set to '" + written
                    + "', which is not a duration this backend can read");
        }
    }

    /** Reads the BPMN text into a document. */
    private Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the deployed BPMN: " + e.getMessage());
        }
    }

    /** Every descendant with the given local name. */
    private List<Element> elementsNamed(Object parent, String localName) {
        NodeList nodes = parent instanceof Document document
                ? document.getElementsByTagNameNS("*", localName)
                : ((Element) parent).getElementsByTagNameNS("*", localName);

        List<Element> found = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            found.add((Element) nodes.item(i));
        }
        return found;
    }
}