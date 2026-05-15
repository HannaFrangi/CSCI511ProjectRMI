package model;

public class PlayerScore {
    String Username;
    int wins;
    int losses;
    int draws;

    public String getUsername() {
        return Username;
    }

    public void setUsername(String username) {
        Username = username;
    }

    public int getLosses() {
        return losses;
    }

    public void setLosses(int losses) {
        this.losses = losses;
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    public int getDraws() {
        return draws;
    }

    public void setDraws(int draws) {
        this.draws = draws;
    }

    public PlayerScore(String username, int draws, int losses, int wins) {
        Username = username;
        this.draws = draws;
        this.losses = losses;
        this.wins = wins;
    }


    public String toDisplayLine() {
        return "[Score]:" +  this.Username + " has won : " + this.wins + " .Lost " + this.losses + " Draws " + this.draws;
    }
}
