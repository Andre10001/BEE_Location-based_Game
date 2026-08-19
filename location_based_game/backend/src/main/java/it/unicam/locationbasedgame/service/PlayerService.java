package it.unicam.locationbasedgame.service;

import it.unicam.locationbasedgame.dto.PlayerDTO;
import it.unicam.locationbasedgame.enums.Team;
import it.unicam.locationbasedgame.model.Player;
import it.unicam.locationbasedgame.repository.PlayerRepository;
import it.unicam.locationbasedgame.service.interfaces.IPlayerService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of IPlayerService, supported by PlayerRepository.
 */
@Service
@RequiredArgsConstructor
public class PlayerService implements IPlayerService {

    private final PlayerRepository playerRepository;

    @Override
    public PlayerDTO createPlayer(PlayerDTO playerDTO) {
        validate(playerDTO);
        Player player = toEntity(playerDTO);
        if (playerRepository.findByNickname(playerDTO.getNickname()).isPresent()) {
            throw new IllegalArgumentException("This nickname is already taken");
        }

        Player saved = playerRepository.save(player);
        return toDto(saved);
    }

    @Override
    public PlayerDTO getPlayerById(String id) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Player not found with id " + id));
        return toDto(player);
    }

    @Override
    public List<PlayerDTO> getAllPlayers() {
        return playerRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public PlayerDTO updatePlayerTeam(String id, String team) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Player not found with id " + id));
        player.setTeam(team == null ? null : Team.valueOf(team));
        Player saved = playerRepository.save(player);
        return toDto(saved);
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
        Player player = playerRepository.findByNickname(nickname)
                .orElseThrow(() -> new EntityNotFoundException("Player not found with nickname " + nickname));
        if (!player.getPassword().equals(password)) {
            throw new IllegalArgumentException("Wrong password");
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
            throw new IllegalArgumentException("name must not be empty");
        }
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new IllegalArgumentException("password must not be empty");
        }
    }

    /** Converts a Player entity into its DTO representation. */
    private PlayerDTO toDto(Player player) {
        return new PlayerDTO(player.getId(), player.getNickname(), player.getPassword(), player.getRole(), player.getTeam());
    }

    /** Converts a PlayerDTO into a new Player entity. */
    private Player toEntity(PlayerDTO dto) {
        Player player = new Player();
        player.setNickname(dto.getNickname());
        player.setPassword(dto.getPassword());
        player.setTeam(dto.getTeam());
        return player;
    }
}
