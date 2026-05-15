package model;

import java.util.Arrays;

public class Xo {
    public String getPlayerX() {
        return PlayerX;
    }

    public String getPlayerO() {
        return PlayerO;
    }

    private final String PlayerX;
    private final String PlayerO;
    private final char[] cells = new char[9];
    private char turn = 'X';
    private boolean over;
    private boolean draw;
    private String WinnerName;

    public Xo(String inviter, String invitee) {
        this.PlayerX = inviter;
        this.PlayerO = invitee;
        Arrays.fill(cells, ' ');
    }

    /**
     * "X" or "O", or null if not a player in this game.
     */
    public String symbolFor(String playerName) {
        if (playerName.equals(PlayerX)) {
            return "X";
        }
        if (playerName.equals(PlayerO)) {
            return "O";
        }
        return null;
    }


    public int parseCoord(String coord) {
        if (coord == null || coord.isEmpty()) throw new IllegalArgumentException("Coords cannot be null or empty");

        String x = coord.trim();
        if (x.isEmpty()) throw new IllegalArgumentException("Coords cannot be null or empty");
        if (x.length() != 2) throw new IllegalArgumentException("Coords must be 2 characters");
        x = x.toUpperCase();
        char colCh = x.charAt(0);
        char rowCh = x.charAt(1);

        if (colCh < 'A' || colCh > 'C') {
            throw new IllegalArgumentException("Column must be A, B, or C");
        }
        if (rowCh < '1' || rowCh > '3') {
            throw new IllegalArgumentException("Row must be 1, 2, or 3");
        }

        int col = colCh - 'A';
        int row = rowCh - '1';
        return row * 3 + col;

    }


    /**
     * @param coord e.g. "B2"
     * @return null if play continues; winner username if won; {@code "DRAW"} if draw
     */
    public String applyMove(String playerName, String coord) {
        if (over)  throw new IllegalStateException("Game already finished");
        String symStr = symbolFor(playerName);

        if (symStr == null) throw new IllegalStateException("Not a player in this game");

        char sym = symStr.charAt(0);
        if (sym != turn) {
            throw new IllegalStateException("Not your turn (current: " + turn + ")");
        }
        int idx = parseCoord(coord);
        if (cells[idx] != ' ') {
            throw new IllegalStateException("Cell already taken");
        }
        cells[idx] = sym;
        if (hasWin(sym)) {
            over = true;
            WinnerName = (sym == 'X') ? PlayerX : PlayerO;
            return WinnerName;
        }
        if (isFull()) {
            over = true;
            draw = true;
            return "DRAW";
        }
        turn = (turn == 'X') ? 'O' : 'X';
        return null;
    }

    private boolean isFull() {
        for (char c : cells) {
            if (c == ' ') {
                return false;
            }
        }
        return true;
    }

    private boolean hasWin(char sym) {
        int[][] lines = {
                {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
                {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
                {0, 4, 8}, {2, 4, 6}
        };
        for (int[] line : lines) {
            if (cells[line[0]] == sym && cells[line[1]] == sym && cells[line[2]] == sym) {
                return true;
            }
        }
        return false;
    }

    /**
     * Text snapshot for one viewer (must be X or O player).
     */
    public String formatSnapshot(String viewerName) {
        String you = symbolFor(viewerName);
        if (you == null) {
            return "Not in this game.";
        }
        String opp = you.equals("X") ? PlayerO : PlayerX;
        StringBuilder sb = new StringBuilder();
        sb.append("   A   B   C\n");
        for (int row = 0; row < 3; row++) {
            sb.append(row + 1).append(" ");
            for (int col = 0; col < 3; col++) {
                char cell = cells[row * 3 + col];
                sb.append(cell == ' ' ? '.' : cell);
                if (col < 2) {
                    sb.append(" | ");
                }
            }
            sb.append("\n");
            if (row < 2) {
                sb.append("  ---+---+---\n");
            }
        }
        sb.append("You are ").append(you).append(". Opponent: ").append(opp).append(".\n");
        if (over) {
            if (draw) {
                sb.append("Result: DRAW\n");
            } else {
                sb.append("Result: ").append(WinnerName).append(" wins.\n");
            }
        } else {
            sb.append("Current turn: ").append(turn).append(" — enter move like B2\n");
        }
        return sb.toString();
    }

}
