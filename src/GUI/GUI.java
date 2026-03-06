package GUI;

import controller.Client;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.text.*;
import javafx.scene.control.TextInputDialog;

import java.util.Objects;
import java.util.Optional;
import model.Player;

import java.util.ArrayList;
import java.util.List;

public class GUI extends Application {

	public static final int size = 20;
	public static final int scene_height = size * 20 + 100;
	public static final int scene_width = size * 20 + 200;

	public static Image image_floor;
	public static Image image_wall;
	public static Image hero_right, hero_left, hero_up, hero_down;

	public static Player me;
	public static List<Player> players = new ArrayList<>();

	private Label[][] fields;
	private TextArea scoreList;

	private Client client;

    private final String[] board = {    // 20x20
			"wwwwwwwwwwwwwwwwwwww",
			"w        ww        w",
			"w w  w  www w  w  ww",
			"w w  w   ww w  w  ww",
			"w  w               w",
			"w w w w w w w  w  ww",
			"w w     www w  w  ww",
			"w w     w w w  w  ww",
			"w   w w  w  w  w   w",
			"w     w  w  w  w   w",
			"w ww ww        w  ww",
			"w  w w    w    w  ww",
			"w        ww w  w  ww",
			"w         w w  w  ww",
			"w        w     w  ww",
			"w  w              ww",
			"w  w www  w w  ww ww",
			"w w      ww w     ww",
			"w   w   ww  w      w",
			"wwwwwwwwwwwwwwwwwwww"
	};

	@Override
	public void start(Stage primaryStage) {
		try {
			GridPane grid = new GridPane();
			grid.setHgap(10);
			grid.setVgap(10);
			grid.setPadding(new Insets(0, 10, 0, 10));

			Text mazeLabel = new Text("Maze:");
			mazeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));

			Text scoreLabel = new Text("Score:");
			scoreLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));

			scoreList = new TextArea();
			scoreList.setEditable(false);

			GridPane boardGrid = new GridPane();

			image_wall = new Image(Objects.requireNonNull(getClass().getResourceAsStream("Image/wall4.png")), size, size, false, false);
			image_floor = new Image(Objects.requireNonNull(getClass().getResourceAsStream("Image/floor1.png")), size, size, false, false);

			hero_right = new Image(Objects.requireNonNull(getClass().getResourceAsStream("Image/heroRight.png")), size, size, false, false);
			hero_left = new Image(Objects.requireNonNull(getClass().getResourceAsStream("Image/heroLeft.png")), size, size, false, false);
			hero_up = new Image(Objects.requireNonNull(getClass().getResourceAsStream("Image/heroUp.png")), size, size, false, false);
			hero_down = new Image(Objects.requireNonNull(getClass().getResourceAsStream("Image/heroDown.png")), size, size, false, false);

			fields = new Label[20][20];
			for (int j = 0; j < 20; j++) {
				for (int i = 0; i < 20; i++) {
					switch (board[j].charAt(i)) {
						case 'w' -> fields[i][j] = new Label("", new ImageView(image_wall));
						case ' ' -> fields[i][j] = new Label("", new ImageView(image_floor));
						default -> throw new Exception("Illegal field value: " + board[j].charAt(i));
					}
					boardGrid.add(fields[i][j], i, j);
				}
			}

			grid.add(mazeLabel, 0, 0);
			grid.add(scoreLabel, 1, 0);
			grid.add(boardGrid, 0, 1);
			grid.add(scoreList, 1, 1);

			Scene scene = new Scene(grid, scene_width, scene_height);
			primaryStage.setScene(scene);
			primaryStage.show();

			// ==== Netværk ====
			// Skift evt. navn pr. klient her:
			String myName = "Orville";
			me = new Player(myName, 0, 0, "up"); // position sættes af server MOVE besked
			players.add(me);

			// Skift localhost ud med socket ip til server :)
			client = new Client("localhost", 8080);

			client.startReader(line -> {
				// Alle GUI-opdateringer på JavaFX-tråden:
				Platform.runLater(() -> handleServerLine(line));
			});

			askForNameAndHello();

			// ==== Input: send til server (ingen lokal flyt) ====
			scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
				switch (event.getCode()) {
					case UP -> client.sendDirection("up");
					case DOWN -> client.sendDirection("down");
					case LEFT -> client.sendDirection("left");
					case RIGHT -> client.sendDirection("right");
					default -> { }
				}
			});

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void askForNameAndHello() {
		TextInputDialog dialog = new TextInputDialog("Orville");
		dialog.setTitle("Player name");
		dialog.setHeaderText("Enter your player name");
		dialog.setContentText("Name:");

		Optional<String> result = dialog.showAndWait();

		if (result.isEmpty()) {
			// Brugeren annullerede/lukkede -> luk app
			Platform.exit();
			return;
		}

		String candidate = result.get().trim();
		if (candidate.isBlank()) {
			showErrorAndRetry();
			return;
		}

		// Sæt lokal 'me' (position kommer fra serverens MOVE)

        // Sørg for at vi har 'me' i players med korrekt navn
		// (nem løsning: fjern gammel me og lav ny)
		if (me != null) {
			players.remove(me);
		}
		me = new model.Player(candidate, 0, 0, "up");
		players.add(me);
		client.sendLine("HELLO " + candidate);
	}

	private void showErrorAndRetry() {
		Alert alert = new Alert(Alert.AlertType.ERROR, "Name cannot be empty.", ButtonType.OK);
		alert.setHeaderText(null);
		alert.showAndWait();
		askForNameAndHello();
	}

	private void handleServerLine(String line) {
		// Protokol:
		// MOVE <name> <x> <y> <dir>
		// POINT <name> <total>
		// MSG <text...>
		if (line == null || line.isBlank()) return;

		String[] p = line.split("\\s+");
		if (p.length == 0) return;

		switch (p[0]) {
			case "MOVE" -> {
				if (p.length < 5) return;
				String name = p[1];
				int x = Integer.parseInt(p[2]);
				int y = Integer.parseInt(p[3]);
				String dir = p[4];
				applyMove(name, x, y, dir);
			}
			case "POINT" -> {
				if (p.length < 3) return;
				String name = p[1];
				int total = Integer.parseInt(p[2]);
				applyPoint(name, total);
			}
			case "MSG" -> {
				String msg = line.length() >= 4 ? line.substring(4) : "";
				if (msg.toLowerCase().contains("name already taken")) {
					// server afviste navnet -> spørg igen
					askForNameAndHello();
					return;
				}
			}
			default -> {
				// ignorer
			}
		}

		scoreList.setText(getScoreList());
	}

	private void applyMove(String name, int x, int y, String direction) {
		Player p = getPlayerByName(name);
		if (p == null) {
			p = new Player(name, x, y, direction);
			players.add(p);
		}

		// Fjern gammel sprite (kun hvis vi havde en position i boardet)
		int oldX = p.getXpos();
		int oldY = p.getYpos();

		// Hvis spilleren allerede var placeret et sted, ryd feltet
		if (within(oldX, oldY)) {
			fields[oldX][oldY].setGraphic(new ImageView(image_floor));
		}

		p.setXpos(x);
		p.setYpos(y);
		p.setDirection(direction);

		// Tegn ny sprite
		if (within(x, y)) {
			fields[x][y].setGraphic(new ImageView(spriteFor(direction)));
		}
	}

	private void applyPoint(String name, int totalPoints) {
		Player p = getPlayerByName(name);
		if (p == null) {
			// Hvis POINT kommer før MOVE (sjældent), opret midlertidigt
			p = new Player(name, 0, 0, "up");
			players.add(p);
		}
		p.setPoints(totalPoints);
	}

	private Image spriteFor(String direction) {
		return switch (direction) {
			case "right" -> hero_right;
			case "left" -> hero_left;
			case "up" -> hero_up;
			case "down" -> hero_down;
			default -> hero_up;
		};
	}

	private boolean within(int x, int y) {
		return x >= 0 && x < 20 && y >= 0 && y < 20;
	}

	public String getScoreList() {
		StringBuilder b = new StringBuilder(100);
		for (Player p : players) {
			b.append(p).append("\r\n");
		}
		return b.toString();
	}

	private Player getPlayerByName(String name) {
		for (Player p : players) {
			if (p.getName().equals(name)) return p;
		}
		return null;
	}
}