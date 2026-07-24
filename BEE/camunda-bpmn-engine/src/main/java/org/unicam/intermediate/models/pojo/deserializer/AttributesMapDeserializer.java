package org.unicam.intermediate.models.pojo.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Tolerant deserializer for "attributes" maps.
 * Accepts both JSON objects ({}) and empty arrays ([]), mapping [] to an empty map.
 */
public class AttributesMapDeserializer extends JsonDeserializer<Map<String, Object>> {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Override
    public Map<String, Object> deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonToken token = parser.currentToken();

        if (token == JsonToken.START_OBJECT) {
            return parser.readValueAs(MAP_TYPE);
        }

        if (token == JsonToken.START_ARRAY) {
            // Consume array content (if any) and treat it as empty object-like attributes.
            parser.skipChildren();
            return new HashMap<>();
        }

        if (token == JsonToken.VALUE_NULL) {
            return new HashMap<>();
        }

        return new HashMap<>();
    }
}
