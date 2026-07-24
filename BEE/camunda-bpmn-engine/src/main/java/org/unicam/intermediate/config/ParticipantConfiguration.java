package org.unicam.intermediate.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.unicam.intermediate.models.pojo.Participant;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration class for loading participants from participants-config.json
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParticipantConfiguration {

    @JsonProperty("scenario")
    private String scenario;

    @JsonProperty("participants")
    private List<ParticipantDTO> participants = new ArrayList<>();

    /**
     * DTO for participant configuration
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantDTO {
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("position")
        private String position;

        /**
         * Convert to Participant POJO
         */
        public Participant toParticipant() {
            Participant participant = new Participant();
            participant.setId(id);
            participant.setName(name);
            participant.setPosition(position);
            return participant;
        }
    }

    /**
     * Convert all DTOs to Participant POJOs
     */
    public List<Participant> toParticipants() {
        List<Participant> result = new ArrayList<>();
        if (participants != null) {
            for (ParticipantDTO dto : participants) {
                result.add(dto.toParticipant());
            }
        }
        return result;
    }
}
