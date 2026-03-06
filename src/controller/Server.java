package controller;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    private final int port;

    // name -> handler (til at sende ud)
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();

    // Central spiltilstand (server authoritative)
    private final Object gameLock = new Object();
    private final Map<String, PlayerState> stateByName = new HashMap<>();

    // Samme board som i GUI
    private final String[] board = {
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

    // Enkle startpositioner (Ikke randomized)
    private final int[][] spawns = {
            {9, 4},
            {14, 15},
            {1, 1},
            {18, 18},
            {1, 18},
            {18, 1}
    };
    private int nextSpawnIdx = 0;

    public Server(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server IP: " + serverSocket.getInetAddress() + ":" + serverSocket.getLocalPort());
            System.out.println("Waiting for connection...");
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new ClientHandler(socket), "client-handler").start();
            }
        }
    }

    private boolean isWall(int x, int y) {
        return board[y].charAt(x) == 'w';
    }

    private String findPlayerAt(int x, int y) {
        for (var e : stateByName.entrySet()) {
            PlayerState ps = e.getValue();
            if (ps.x == x && ps.y == y) return e.getKey();
        }
        return null;
    }

    private int[] allocateSpawn() {
        // Find næste spawn der ikke er væg og ikke er optaget
        for (int tries = 0; tries < spawns.length; tries++) {
            int[] s = spawns[nextSpawnIdx % spawns.length];
            nextSpawnIdx++;

            int x = s[0], y = s[1];
            if (isWall(x, y)) continue;
            if (findPlayerAt(x, y) != null) continue;
            return new int[]{x, y};
        }
        // fallback (burde ikke ske)
        return new int[]{1, 1};
    }

    private void broadcast(String message) {
        for (ClientHandler ch : clients.values()) {
            ch.send(message);
            System.out.println(message);
        }
    }

    private void sendTo(String name, String message) {
        ClientHandler ch = clients.get(name);
        if (ch != null) ch.send(message);
        System.out.println(message + " : " + name);
    }
    // TODO Test når denne gameLock ikke er synchronized. Altså at den kritiske sektion fejler.
    private void handleMove(String moverName, String direction) {
        synchronized (gameLock) {
            PlayerState mover = stateByName.get(moverName);
            if (mover == null) return;

            mover.dir = direction;

            int dx = 0, dy = 0;
            switch (direction) {
                case "up" -> dy = -1;
                case "down" -> dy = +1;
                case "left" -> dx = -1;
                case "right" -> dx = +1;
                default -> { return; }
            }

            int nx = mover.x + dx;
            int ny = mover.y + dy;

            // 1) væg
            if (isWall(nx, ny)) {
                mover.points -= 1;
                sendTo(moverName, "MSG You hit a wall going " + direction + " (-1)");
                broadcast("POINT " + moverName + " " + mover.points);
                return;
            }

            // 2) spiller-kollision (uanset hvem der går ind i hvem)
            String otherName = findPlayerAt(nx, ny);
            if (otherName != null && !otherName.equals(moverName)) {
                PlayerState other = stateByName.get(otherName);

                mover.points += 10;
                other.points -= 10;

                sendTo(moverName, "MSG You bumped into " + otherName + " going " + direction + " (+10)");
                sendTo(otherName, "MSG " + moverName + " bumped into you (-10)");

                broadcast("POINT " + moverName + " " + mover.points);
                broadcast("POINT " + otherName + " " + other.points);
                return;
            }

            // 3) normal move
            mover.x = nx;
            mover.y = ny;
            mover.points += 1;

            broadcast("MOVE " + moverName + " " + mover.x + " " + mover.y + " " + mover.dir);
            broadcast("POINT " + moverName + " " + mover.points);
        }
    }

    private static class PlayerState {
        int x, y;
        int points;
        String dir;

        PlayerState(int x, int y, String dir) {
            this.x = x;
            this.y = y;
            this.dir = dir;
            this.points = 0;
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String name;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        void send(String message) {
            if (out != null) out.println(message);
        }

        @Override
        public void run() {
            try (Socket s = socket;
                 BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                 PrintWriter out = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true)) {

                this.in = in;
                this.out = out;

                // Forvent første linje: HELLO <name>
                String hello = in.readLine();
                if (hello == null) return;

                String[] parts = hello.trim().split("\\s+");
                if (parts.length < 2 || !parts[0].equalsIgnoreCase("HELLO")) {
                    out.println("MSG Expected: HELLO <name>");
                    return;
                }

                String proposed = parts[1].trim();
                if (proposed.isEmpty()) return;

                // Registrér client navn unikt
                if (clients.putIfAbsent(proposed, this) != null) {
                    out.println("MSG Name already taken");
                    return;
                }
                this.name = proposed;
                // TODO gameLock skal testes for fejl når vi ikke har en synchronized kritisk sektion (såsom fx Thread.sleep(30))
                // Spawn + init state
                synchronized (gameLock) {
                    int[] spawn = allocateSpawn();
                    PlayerState ps = new PlayerState(spawn[0], spawn[1], "up");
                    stateByName.put(name, ps);

                    // Send "snapshot" til den nye client: alle eksisterende spillere (MOVE + POINT)
                    for (var e : stateByName.entrySet()) {
                        String n = e.getKey();
                        PlayerState st = e.getValue();
                        out.println("MOVE " + n + " " + st.x + " " + st.y + " " + st.dir);
                        out.println("POINT " + n + " " + st.points);
                    }
                }

                broadcast("MSG ** " + name + " joined **");

                // Main loop: MOVE <dir>
                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    if (line.equalsIgnoreCase("/quit")) break;

                    String[] p = line.split("\\s+");
                    if (p.length == 2 && p[0].equalsIgnoreCase("MOVE")) {
                        handleMove(name, p[1].toLowerCase());
                    } else {
                        send("MSG Unknown command. Use: MOVE up|down|left|right");
                    }
                }

            } catch (IOException ignored) {
            } finally {
                if (name != null) {
                    clients.remove(name);
                    // TODO skal teste når vi ikke har synchronized med
                    synchronized (gameLock) {
                        stateByName.remove(name);
                    }
                    broadcast("MSG ** " + name + " left **");
                }
            }
        }
    }
}