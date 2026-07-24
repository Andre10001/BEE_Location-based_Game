package org.unicam.intermediate.service.environmental;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.unicam.intermediate.models.pojo.PhysicalPlace;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SensorDataService {

    private static final String LIGHT_ATTR = "light";
    private static final String LIGHT_ON = "on";
    private static final String LIGHT_OFF = "off";
    private static final double LIGHT_THRESHOLD_LUMEN = 300.0;

    private final EnvironmentDataService environmentDataService;

    /**
     * Converts a raw lumen reading from a light sensor into an on/off state
     * and stores it in the place's {@code light} attribute.
     *
     * @param placeId  id of the physical place where the sensor is installed
     * @param lumen    raw lumen value read by the sensor
     * @return the resolved on/off string, or empty if the place does not exist
     */
    public Optional<String> updateLightSensor(String placeId, double lumen) {
        Optional<PhysicalPlace> placeOpt = environmentDataService.getPhysicalPlace(placeId);
        if (placeOpt.isEmpty()) {
            return Optional.empty();
        }

        PhysicalPlace place = placeOpt.get();
        String lightState = lumen >= LIGHT_THRESHOLD_LUMEN ? LIGHT_ON : LIGHT_OFF;

        if (place.getAttributes() != null) {
            place.getAttributes().put(LIGHT_ATTR, lightState);
        }

        log.info("[SensorData] Place '{}' light sensor: {} lumen -> '{}'", placeId, lumen, lightState);
        return Optional.of(lightState);
    }
}
