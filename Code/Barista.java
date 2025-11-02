package Code;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import Code.helpers.CustomerHandler;

// Server Implementation
public class Barista {
    private final static int PORT = 8888; // server port, any client can connect that is communicating on this port
    // thread pool that handles client connections, at most letting in 20 clients at
    // a time into the coffee shop
    private final ExecutorService clientHandlers = Executors.newFixedThreadPool(20);
    // thread pool for barista workers, there are two workers that can handle the
    // orders, when an order is submitted it will go in to waiting
    // area and a barista that is idle will pick up that order and work on it.
    private final ExecutorService baristaWorkers = Executors.newFixedThreadPool(2);

    // conccurent data structures, only block certain parts of the data structure
    // when a worker is using it.
    private final LinkedBlockingQueue<String> waitingArea = new LinkedBlockingQueue<>(); // client places order -> waiting area -> free barista picks up order
    private final ConcurrentHashMap<String, String> brewArea = new ConcurrentHashMap<>(); // barista picks up order -> order is in brew area
    private final LinkedBlockingQueue<String> trayArea = new LinkedBlockingQueue<>(); // barista completes tasks -> order gets placed in tray area

    public static void main(String[] args) {
        Barista cafe = new Barista();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            cafe.shutdown();
        }));

        cafe.runServer();
    }

    private void runServer() {
        ServerSocket serverSocket = null; // passive socket to allow clients to connect
        try {
            serverSocket = new ServerSocket(PORT); // standard socket for clients to connect
            System.out.println("Cafe is Open!");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New Customer Connected: " + clientSocket.getRemoteSocketAddress());

                // Create CustomerHandler and submit to thread pool
                try {
                    CustomerHandler handler = new CustomerHandler(
                        clientSocket, 
                        waitingArea, 
                        brewArea, 
                        trayArea, 
                        baristaWorkers
                    );
                    
                    clientHandlers.submit(handler);
                    
                } catch (IOException e) {
                    System.err.println("Failed to create customer handler: " + e.getMessage());
                    try {
                        clientSocket.close();
                    } catch (IOException ex) {
                        System.err.println("Error closing client socket: " + ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private void shutdown() {
        // to do
    }

}
