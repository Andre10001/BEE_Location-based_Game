package it.unicam.locationbasedgame.service;

import it.unicam.locationbasedgame.dto.PlayerDTO;
import it.unicam.locationbasedgame.enums.Team;
import it.unicam.locationbasedgame.model.Player;
import it.unicam.locationbasedgame.repository.PlayerRepository;
import it.unicam.locationbasedgame.service.interfaces.IPlayerService;
import it.unicam.locationbasedgame.service.interfaces.IProcessTimerService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of IPlayerService, supported by PlayerRepository.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PlayerService implements IPlayerService {

    private final PlayerRepository playerRepository;
    private final IProcessTimerService processTimerService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public PlayerDTO createPlayer(PlayerDTO playerDTO) {
        validate(playerDTO);
        if (playerRepository.findByNickname(playerDTO.getNickname()).isPresent()) {
            throw new IllegalArgumentException("This nickname is already taken");
        }
        Player player = toEntity(playerDTO);
        player.setPassword(passwordEncoder.encode(playerDTO.getPassword()));
        return toDto(playerRepository.save(player));
    }

    @Override
    public PlayerDTO getPlayerById(String id) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Player not found with id " + id));
        
        PlayerDTO dto = toDto(player);
        dto.setPenaltySecondsLeft(getPenaltySecondsLeft(id));
        return dto;
    }

    @Override
    public List<PlayerDTO> getAllPlayers() {
        return playerRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PlayerDTO updatePlayerTeam(String id, String team) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Player not found with id " + id));
 
        if (team == null) {
            player.setTeam(null);
            return toDto(playerRepository.save(player));
        }
 
        Team chosen;
        try {
            chosen = Team.valueOf(team);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown team: " + team);
        }
 
        int chosenCount = 0;
        int otherCount = 0;
        for (Player other : playerRepository.findAll()) {
            if (other.getId().equals(id)) continue;
            if (other.getTeam() == null) continue;
            if (other.getTeam() == chosen) {
                chosenCount++;
            } else {
                otherCount++;
            }
        }
 
        if (chosenCount + 1 - otherCount > 1) {
            throw new IllegalArgumentException(
                    "Team " + team + " already has enough players: join the other one");
        }
 
        player.setTeam(chosen);
        return toDto(playerRepository.save(player));
    }

    @Override
    @Transactional
    public PlayerDTO linkParticipant(String id, String participantId) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Player not found with id " + id));
 
        if (participantId == null || participantId.isBlank()) {
            player.setBeeParticipantId(null);
            return toDto(playerRepository.save(player));
        }

        for (Player other : playerRepository.findAll()) {
            if (other.getId().equals(id)) continue;
            if (participantId.equals(other.getBeeParticipantId())) {
                throw new IllegalArgumentException(
                        participantId + " is already taken by " + other.getNickname());
            }
        }
 
        player.setBeeParticipantId(participantId);
        return toDto(playerRepository.save(player));
    }

    @Override
    @Transactional
    public PlayerDTO leaveMatch(String id) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Player not found with id " + id));
        return toDto(playerRepository.save(release(player)));
    }
 
    @Override
    @Transactional
    public int releaseAllPlayers() {
        int released = 0;
        for (Player player : playerRepository.findAll()) {
            if (player.getTeam() == null && player.getBeeParticipantId() == null
                    && !player.isUnderPenalty()) {
                continue;
            }
            playerRepository.save(release(player));
            released++;
        }
        log.info("[PlayerService] " + released + " player(s) released from the match");
        return released;
    }
 
    /** Releases a player from the match. */
    private Player release(Player player) {
        player.setTeam(null);
        player.setBeeParticipantId(null);
        player.clearPenalty();
        return player;
    }

    @Override
    @Transactional
    public PlayerDTO startPenalty(String id) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Player not found with id " + id));
 
        int seconds = processTimerService.getPenaltySeconds(player.getBeeParticipantId());
        player.startPenalty(seconds);
        log.info("[PlayerService] " + player.getNickname() + " cannot attack for " + seconds + "s");
        return toDto(playerRepository.save(player));
    }
 
    @Override
    public int getPenaltySecondsLeft(String id) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Player not found with id " + id));
        return player.getPenaltySecondsLeft();
    }

    @Override
    public void deletePlayer(String id) {
        if (!playerRepository.existsById(id)) {
            throw new EntityNotFoundException("Player not found with id " + id);
        }
        playerRepository.deleteById(id);
    }    
 
    @Override
    public PlayerDTO login(String nickname, String password) {
        Player player = playerRepository.findByNickname(nickname).orElse(null);
        if (player == null || !passwordEncoder.matches(password, player.getPassword())) {
            throw new IllegalArgumentException("Wrong nickname or password");
        }
        return toDto(player);
    }

    /**
     * Checks the data integrity of the provided PlayerDTO.
     *
     * @param dto the player data to validate
     * @throws IllegalArgumentException if any field is invalid
     */
    private void validate(PlayerDTO dto) {
        if (dto.getNickname() == null || dto.getNickname().isBlank()) {
            throw new IllegalArgumentException("nickname must not be empty");
        }
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new IllegalArgumentException("password must not be empty");
        }
    }

    /** Converts a Player entity into its DTO representation. */
    private PlayerDTO toDto(Player player) {
        return new PlayerDTO(player.getId(), player.getNickname(), null,
                player.getRole(), player.getTeam(), player.getBeeParticipantId(), 0);
    }

    /** Converts a PlayerDTO into a new Player entity. */
    private Player toEntity(PlayerDTO dto) {
        Player player = new Player();
        player.setNickname(dto.getNickname());
        player.setPassword(dto.getPassword());
        return player;
    }
}
