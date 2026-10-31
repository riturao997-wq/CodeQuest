import java.io.Serializable;

public class Player implements Serializable {
    private static final long serialVersionUID = 1L;
    private String username;
    private String password;
    private int health;
    private int xp;
    private int currentLevel;
    private int solvedCount;

    public Player(String username, String password) {
        this.username = username;
        this.password = password;
        this.health = 100;
        this.xp = 0;
        this.currentLevel = 1;
        this.solvedCount = 0;
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    
    public int getHealth() { return health; }
    public void setHealth(int health) { this.health = health; }
    
    public int getXp() { return xp; }
    public void addXp(int xp) { this.xp += xp; }
    
    public int getCurrentLevel() { return currentLevel; }
    public void setCurrentLevel(int currentLevel) { this.currentLevel = currentLevel; }
    
    public int getSolvedCount() { return solvedCount; }
    public void setSolvedCount(int solvedCount) { this.solvedCount = solvedCount; }

    public void resetProgress() {
        this.xp = 0;
        this.currentLevel = 1;
        this.solvedCount = 0;
    }
}