package org.unicam.intermediate.models.pojo;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.Setter;
import org.unicam.intermediate.models.environmental.LocationArea;
import org.unicam.intermediate.models.pojo.deserializer.AttributesMapDeserializer;

import java.util.List;
import java.util.Map;

@Setter
@Getter
public class PhysicalPlace {
    private String id;
    private String name;
    private String temperature;
    private List<List<Double>> coordinates;
    @JsonDeserialize(using = AttributesMapDeserializer.class)
    private Map<String, Object> attributes;

    private transient LocationArea locationArea;

    public LocationArea getLocationArea() {
        if (locationArea == null && coordinates != null && !coordinates.isEmpty()) {
            locationArea = new LocationArea(coordinates);
        }
        return locationArea;
    }
}
