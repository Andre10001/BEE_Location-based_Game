package org.unicam.intermediate.models.pojo;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.Setter;
import org.unicam.intermediate.models.pojo.deserializer.AttributesMapDeserializer;

import java.util.List;
import java.util.Map;

@Setter
@Getter
public class View {
    private String id;
    private String name;
    private List<String> logicalPlaces;
    @JsonDeserialize(using = AttributesMapDeserializer.class)
    private Map<String, Object> attributes;
}
