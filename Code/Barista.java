package Code;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import Code.helpers.CustomerHandler;

// Server Implementation
public class Barista {
    private final static int PORT = 8888; // server port, any client can connect that is communicating on this port
    // thread pool that handles client connections, at most letting in 20 clients at
    // a time into the coffee shop
    private final ExecutorService clientHandlers = Executors.newFixedThreadPool(20);
    // thread pool for barista workers, can handle up to 4 workers (2 tea + 2 coffee
    // max)
    private final ExecutorService baristaWorkers = Executors.newFixedThreadPool(4);

    // smart scheduler thread that assigns work based on capacity
    private final Thread orderScheduler;

    // conccurent data structures, only block certain parts of the data structure
    // when a worker is using it.
    private final LinkedBlockingQueue<String> waitingArea = new LinkedBlockingQueue<>(); // client places order ->
                                                                                         // waiting area -> free barista
                                                                                         // picks up order
    private final ConcurrentHashMap<String, String> brewArea = new ConcurrentHashMap<>(); // barista picks up order ->
                                                                                          // order is in brew area
    private final LinkedBlockingQueue<String> trayArea = new LinkedBlockingQueue<>(); // barista completes tasks ->
                                                                                      // order gets placed in tray area

    // tracking variables for cafe stats
    // volatile, because changes made in one thread, affect all other threads
    // read by all threads, but writin only once in timer thread (which calls check stats)
    // Sufficient since only 1 thread updates these, no need for atomic objects
    private volatile int ordersInWaiting;
    private volatile int ordersInBrew;
    private volatile int orderInTray;

    // capacity counters for tea and coffee, read and mofified by all threads
    private final AtomicInteger countTeaBrewing = new AtomicInteger(0);
    private final AtomicInteger countCoffeeBrewing = new AtomicInteger(0);
    
    // track connected clients
    private final AtomicInteger connectedClients = new AtomicInteger(0);

    // shutdown flag for scheduler
    private volatile boolean isShuttingDown = false;

    public Barista() {
        // Initialize the smart scheduler
        this.orderScheduler = new Thread(this::runOrderScheduler); // redirector, instance method
        this.orderScheduler.setDaemon(true); // when cafe closes we stop service
        this.orderScheduler.start();
    }

    public static void main(String[] args) {
        Barista cafe = new Barista();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            cafe.shutdown();
        }));

        Timer timer = new Timer(); // print stats every x seconds
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                cafe.printStats();
            }
        }, 0, 1000); // Update every 1 second instead of 5

        cafe.runServer(); // as soon as server stops running, orderScheduler is done (daemon thread)
    }

    private void runServer() {
        ServerSocket serverSocket = null; // passive socket to allow clients to connect
        try {
            serverSocket = new ServerSocket(PORT);
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
                            connectedClients); // Pass the counter

                    connectedClients.incrementAndGet(); // Client connected
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

    public void checkStats() {
        ordersInWaiting = waitingArea.size();
        ordersInBrew = brewArea.size();
        orderInTray = trayArea.size();
    }

    public void printStats() {
        checkStats();
        
        // Save cursor position, move to top, print status, restore cursor
        System.out.print("\033[s");        // Save cursor position
        System.out.print("\033[H");        // Move to top-left
        System.out.print("\033[K");        // Clear current line
        
        // Print compact status bar
        System.out.printf("CAFE STATUS | Clients: %d | Waiting: %d | Brewing: %d (T:%d C:%d) | Tray: %d | Port: %d%n",
                connectedClients.get(), ordersInWaiting, ordersInBrew, 
                countTeaBrewing.get(), countCoffeeBrewing.get(), orderInTray, PORT);
        
        System.out.print("─".repeat(80)); // Separator line
        System.out.print("\033[u");        // Restore cursor position
    }

    private void runOrderScheduler() {
        while (!isShuttingDown) {
            try {
                String order = waitingArea.take(); // Take next order, if empty (no order) WAIT until someone adds
                                                   // order, do nothing if no work, efficient because wasted cpu cycles
                                                   // checking
                String type = getOrderType(order);

                // Check if we can brew this type
                if (canBrew(type)) {
                    baristaWorkers.submit(() -> brewOrder(order, type));
                } else {
                    // Put back and try again later
                    waitingArea.offer(order);
                    Thread.sleep(100); // Brief pause to prevent busy waiting
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private String getOrderType(String order) {
        String[] parts = order.split(" ");
        return parts.length > 1 ? parts[1].toLowerCase() : "";
    }

    private boolean canBrew(String type) {
        if (type.equalsIgnoreCase("tea")) {
            return countTeaBrewing.get() < 2;
        } else if (type.equalsIgnoreCase("coffee")) {
            return countCoffeeBrewing.get() < 2;
        }
        return false;
    }

    private void brewOrder(String orderItem, String type) {
        try {
            System.out.println("Barista started to brew: " + orderItem);
            brewArea.put(orderItem, "BREWING");

            // Increment brewing counter
            if (type.equalsIgnoreCase("tea")) {
                countTeaBrewing.incrementAndGet();
            } else if (type.equalsIgnoreCase("coffee")) {
                countCoffeeBrewing.incrementAndGet();
            }

            // Brew time based on type
            int timeToBrew = type.equalsIgnoreCase("tea") ? 30000 : 45000;
            Thread.sleep(timeToBrew);

            // Move to tray
            brewArea.remove(orderItem);
            trayArea.offer(orderItem);

            // Decrement brewing counter
            if (type.equalsIgnoreCase("tea")) {
                countTeaBrewing.decrementAndGet();
            } else if (type.equalsIgnoreCase("coffee")) {
                countCoffeeBrewing.decrementAndGet();
            }

            System.out.println("Order ready for pickup in tray: " + orderItem);
        } catch (Exception e) {
            System.err.println("Error brewing order: " + e.getMessage());
        }
    }

    private void shutdown() {
        isShuttingDown = true;
        if (orderScheduler != null) {
            orderScheduler.interrupt();
        }
        clientHandlers.shutdown();
        baristaWorkers.shutdown();
    }

}
