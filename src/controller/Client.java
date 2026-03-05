package controller;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class Client {
    private final Socket socket;
    private final PrintWriter out;
    private final BufferedReader in;

    private Thread readerThread;

    public Client(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.out = new PrintWriter(new OutputStreamWriter(this.socket.getOutputStream()), true);
        this.in  = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
    }

    public void startReader(Consumer<String> onLine) {
        readerThread = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    onLine.accept(line);
                }
            } catch (IOException ignored) {
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public void sendLine(String line) {
        out.println(line);
    }

    public void sendDirection(String direction) {
        // forventer: up/down/left/right
        out.println("MOVE " + direction);
    }

    public void close() throws IOException {
        try { socket.close(); } finally {
            if (readerThread != null) readerThread.interrupt();
        }
    }
}