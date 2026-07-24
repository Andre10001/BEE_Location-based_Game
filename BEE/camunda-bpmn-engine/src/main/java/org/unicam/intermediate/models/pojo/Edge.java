package org.unicam.intermediate.models.pojo;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.Setter;
import org.unicam.intermediate.models.pojo.deserializer.AttributesMapDeserializer;

import java.util.Map;

@Setter
@Getter
public class Edge {
    private String id;
    private String name;
    private String source;
    private String target;
    @JsonDeserialize(using = AttributesMapDeserializer.class)
    private Map<String, Object> attributes;
}
