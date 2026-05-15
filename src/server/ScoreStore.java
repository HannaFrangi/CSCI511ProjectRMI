package server;

import model.PlayerScore;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScoreStore {

    private final Path filePath;
    private final Map<String, PlayerScore> scores = new HashMap<>();
    private final Object lock = new Object();

    public ScoreStore(String fileName) {
        this.filePath = Paths.get(fileName);
    }

    public void load() {
        synchronized (lock) {
            scores.clear();
            if (!Files.exists(filePath)) {
                return;
            }
            try {
                for (String line : Files.readAllLines(filePath, StandardCharsets.UTF_8)) {
                    if (line == null || line.isBlank()) {
                        continue;
                    }
                    String[] parts = line.split(",");
                    if (parts.length < 4) {
                        continue;
                    }
                    String username = parts[0].trim();
                    int wins = Integer.parseInt(parts[1].trim());
                    int losses = Integer.parseInt(parts[2].trim());
                    int draws = Integer.parseInt(parts[3].trim());
                    scores.put(username, new PlayerScore(username, draws, losses, wins));
                }
            } catch (IOException | NumberFormatException e) {
                System.err.println("Failed to load scores from " + filePath + ": " + e.getMessage());
            }
        }
    }

    public void recordGameEnd(String moveResult, String playerX, String playerO) {
        if (moveResult == null || playerX == null || playerO == null) {
            return;
        }
        synchronized (lock) {
            PlayerScore sx = getOrCreate(playerX);
            PlayerScore so = getOrCreate(playerO);
            if ("DRAW".equals(moveResult)) {
                sx.setDraws(sx.getDraws() + 1);
                so.setDraws(so.getDraws() + 1);
            } else {
                PlayerScore winner = moveResult.equals(playerX) ? sx : so;
                PlayerScore loser = moveResult.equals(playerX) ? so : sx;
                winner.setWins(winner.getWins() + 1);
                loser.setLosses(loser.getLosses() + 1);
            }
            save();
        }
    }

    public List<String> getLeaderboardLines() {
        synchronized (lock) {
            List<PlayerScore> sorted = new ArrayList<>(scores.values());
            sorted.sort(Comparator
                    .comparingInt(PlayerScore::getWins).reversed()
                    .thenComparingInt(PlayerScore::getLosses)
                    .thenComparing(PlayerScore::getUsername, String.CASE_INSENSITIVE_ORDER));
            List<String> lines = new ArrayList<>();
            for (PlayerScore ps : sorted) {
                lines.add(ps.toDisplayLine());
            }
            return lines;
        }
    }

    private PlayerScore getOrCreate(String username) {
        return scores.computeIfAbsent(username, u -> new PlayerScore(u, 0, 0, 0));
    }

    private void save() {
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
                for (PlayerScore ps : scores.values()) {
                    writer.write(ps.getUsername() + ","
                            + ps.getWins() + ","
                            + ps.getLosses() + ","
                            + ps.getDraws());
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to save scores to " + filePath + ": " + e.getMessage());
        }
    }
}
