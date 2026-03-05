package model;

public class Player {
	private final String name;
	private int xpos;
	private int ypos;
	private int points;
	private String direction;

	public Player(String name, int xpos, int ypos, String direction) {
		this.name = name;
		this.xpos = xpos;
		this.ypos = ypos;
		this.direction = direction;
		this.points = 0;
	}

	public String getName() { return name; }

	public int getXpos() { return xpos; }
	public void setXpos(int xpos) { this.xpos = xpos; }

	public int getYpos() { return ypos; }
	public void setYpos(int ypos) { this.ypos = ypos; }

	public String getDirection() { return direction; }
	public void setDirection(String direction) { this.direction = direction; }

	public int getPoints() { return points; }
	public void setPoints(int points) { this.points = points; }
	public void addPoints(int p) { this.points += p; }

	@Override
	public String toString() {
		return name + ":   " + points;
	}
}