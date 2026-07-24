package org.unicam.intermediate.service.environmental;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.unicam.intermediate.models.environmental.Coordinate;
import org.unicam.intermediate.models.pojo.PhysicalPlace;
import org.unicam.intermediate.service.participant.ParticipantPositionService;

import java.util.Optional;

@Service
@Slf4j
@AllArgsConstructor
public class ProximityService {
    
    private final ParticipantPositionService positionService;
    private final EnvironmentDataService environmentDataService;
    
    /**
     * Check if two participants are in the same place
     * They can only bind/unbind if both are in a defined environment place
     */
    public boolean areParticipantsInSamePlace(String participant1Id, String participant2Id) {
        Coordinate pos1 = positionService.getPosition(participant1Id);
        Coordinate pos2 = positionService.getPosition(participant2Id);
        
        if (pos1 == null || pos2 == null) {
            log.debug("[Proximity] Missing position data for participants");
            return false;
        }
        
        // Find which place each participant is in
        Optional<PhysicalPlace> place1 = environmentDataService.findPhysicalPlaceContainingLocation(pos1.lat, pos1.lon);
        Optional<PhysicalPlace> place2 = environmentDataService.findPhysicalPlaceContainingLocation(pos2.lat, pos2.lon);
        
        // Both must be in a place, and it must be the same place
        if (place1.isPresent() && place2.isPresent()) {
            boolean samePlace = place1.get().getId().equals(place2.get().getId());
            
            if (samePlace) {
                log.info("[Proximity] Participants {} and {} are both in place: {} ({})", 
                        participant1Id, participant2Id, 
                        place1.get().getId(), place1.get().getName());
            } else {
                log.info("[Proximity] Participants in different places - P1: {} in {}, P2: {} in {}", 
                        participant1Id, place1.get().getId(),
                        participant2Id, place2.get().getId());
            }
            
            return samePlace;
        }
        
        // One or both participants not in a defined place
        log.info("[Proximity] Cannot bind/unbind - not in defined places. P1 in place: {}, P2 in place: {}", 
                place1.isPresent(), place2.isPresent());
        
        return false;
    }
    
    /**
     * Get the place where binding/unbinding can occur
     * Returns the place if both participants are in the same place, null otherwise
     */
    public PhysicalPlace getBindingPlace(String participant1Id, String participant2Id) {
        Coordinate pos1 = positionService.getPosition(participant1Id);
        Coordinate pos2 = positionService.getPosition(participant2Id);
        
        if (pos1 == null || pos2 == null) {
            return null;
        }
        
        Optional<PhysicalPlace> place1 = environmentDataService.findPhysicalPlaceContainingLocation(pos1.lat, pos1.lon);
        Optional<PhysicalPlace> place2 = environmentDataService.findPhysicalPlaceContainingLocation(pos2.lat, pos2.lon);
        
        if (place1.isPresent() && place2.isPresent() && 
            place1.get().getId().equals(place2.get().getId())) {
            return place1.get();
        }
        
        return null;
    }
    
    /**
     * Simple status check for binding readiness
     */
    public BindingReadiness checkBindingReadiness(String participant1Id, String participant2Id) {
        Coordinate pos1 = positionService.getPosition(participant1Id);
        Coordinate pos2 = positionService.getPosition(participant2Id);
        
        if (pos1 == null || pos2 == null) {
            return new BindingReadiness(false, null, "Missing position data");
        }
        
        Optional<PhysicalPlace> place1 = environmentDataService.findPhysicalPlaceContainingLocation(pos1.lat, pos1.lon);
        Optional<PhysicalPlace> place2 = environmentDataService.findPhysicalPlaceContainingLocation(pos2.lat, pos2.lon);
        
        if (!place1.isPresent()) {
            return new BindingReadiness(false, null, 
                    String.format("Participant %s not in any defined place", participant1Id));
        }
        
        if (!place2.isPresent()) {
            return new BindingReadiness(false, null, 
                    String.format("Participant %s not in any defined place", participant2Id));
        }
        
        if (!place1.get().getId().equals(place2.get().getId())) {
            return new BindingReadiness(false, null, 
                    String.format("Participants in different places: %s vs %s", 
                            place1.get().getName(), place2.get().getName()));
        }
        
        // Both in same place - ready to bind!
        return new BindingReadiness(true, place1.get(), 
                String.format("Ready to bind in %s", place1.get().getName()));
    }
    
    public record BindingReadiness(
            boolean canBind,
            PhysicalPlace place,
            String message
    ) {}
}