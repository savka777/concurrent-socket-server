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
import Code.helpers.OrderTicket;

public class Barista {
    private final static int PORT = 8888;

    // Threads
    private final ExecutorService clientHandlers = Executors.newFixedThreadPool(10);
    private final ExecutorService baristaWorkers = Executors.newFixedThreadPool(4);
    private final Thread orderScheduler;

    // Concurrent Data Structures
    private final LinkedBlockingQueue<OrderTicket> waitingArea = new LinkedBlockingQueue<>();
    private final ConcurrentHashMap<String, String> brewArea = new ConcurrentHashMap<>();
    private final LinkedBlockingQueue<OrderTicket> trayArea = new LinkedBlockingQueue<>();
    private final ConcurrentHashMap<Integer, Boolean> activeCustomers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> idleCustomers = new ConcurrentHashMap<>();

    // Snapshots counts consistant across all threads
    private volatile int ordersInWaiting;
    private volatile int ordersInBrew;
    private volatile int orderInTray;

    // Mutex variables, thread safe updates across
    private final AtomicInteger countTeaBrewing = new AtomicInteger(0);
    private final AtomicInteger countCoffeeBrewing = new AtomicInteger(0);
    private final AtomicInteger connectedClients = new AtomicInteger(0);

    private volatile boolean isShuttingDown = false;

    /**
     * Main server class.
     * <p>
     * {@code Barista} is responsible for:
     * <ul>
     * <li>Accepting incoming client connections</li>
     * <li>Creating a {@link CustomerHandler} for each connected customer</li>
     * <li>Scheduling and brewing orders using scheduler and worker threads</li>
     * <li>Moving orders from WAITING → BREWING → TRAY areas</li>
     * <li>Sending async notifications to customers when orders are ready</li>
     * <li>Displaying live stats to the terminal</li>
     * </ul>
     *
     */
    public Barista() {
        this.orderScheduler = new Thread(this::runOrderScheduler);
        this.orderScheduler.setDaemon(true);
        this.orderScheduler.start();
    }

    public static void main(String[] args) {
        Barista cafe = new Barista();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            cafe.shutdown();
        }));

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                cafe.printStats();
            }
        }, 0, 1000);

        cafe.runServer();
    }

    private void runServer() {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Cafe is Open!");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New Customer Connected: " + clientSocket.getRemoteSocketAddress());

                try {
                    CustomerHandler handler = new CustomerHandler(
                            clientSocket,
                            waitingArea,
                            brewArea,
                            trayArea,
                            connectedClients,
                            activeCustomers,
                            idleCustomers);

                    connectedClients.incrementAndGet();
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

        System.out.print("\033[s");
        System.out.print("\033[H");
        System.out.print("\033[K");
        System.out.printf(
                "CAFE STATUS | Clients: %d | Idle: %d | Waiting: %d | Brewing: %d (T:%d C:%d) | Tray: %d | Port: %d%n",
                connectedClients.get(), idleCustomers.size(), ordersInWaiting, ordersInBrew,
                countTeaBrewing.get(), countCoffeeBrewing.get(), orderInTray, PORT);

        System.out.print("─".repeat(80));
        System.out.print("\033[u");
    }

    /**
     * Scheduler loop that takes orders from the waiting area.
     * <p>
     * Continuously takes the next {@link OrderTicket} from {@code waitingArea}.
     * If the brewing capacity allows it, submits work to {@code baristaWorkers}.
     * Otherwise, re queue the ticket and wait.
     */
    private void runOrderScheduler() {
        while (!isShuttingDown) {
            try {
                OrderTicket ticket = waitingArea.take();
                String type = getOrderType(ticket.orderStr());

                if (canBrew(type)) {
                    baristaWorkers.submit(() -> brewOrder(ticket, type));
                } else {
                    waitingArea.offer(ticket);
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Helper: Extracts the order type ("tea", "coffee") from an order string.
     *
     * @param order order string
     * @return order type
     */
    private String getOrderType(String order) {
        String orderItem = order.contains(":") ? order.substring(order.indexOf(":") + 1) : order;
        String[] parts = orderItem.split(" ");
        return parts.length > 1 ? parts[1].toLowerCase() : "";
    }

    /**
     * Helper: Checks if a new order of the given type can be brewed right now.
     * <p>
     * Capacity rule:
     * max 2 teas brewing and max 2 coffees brewing concurrently.
     *
     * @param type order type ("tea" or "coffee")
     * @return true if a barista slot is free for that order type
     */
    private boolean canBrew(String type) {
        if (type.equalsIgnoreCase("tea")) {
            return countTeaBrewing.get() < 2;
        } else if (type.equalsIgnoreCase("coffee")) {
            return countCoffeeBrewing.get() < 2;
        }
        return false;
    }

    /**
     * Brew an order.
     * <p>
     * Steps:
     * <ol>
     * <li>Mark order as BREWING in {@code brewArea}</li>
     * <li>Increment brewing count</li>
     * <li>Sleep to simulate brew time</li>
     * <li>Move order to {@code trayArea}</li>
     * <li>Decrement brewing count</li>
     * <li>Notify the customer (via handler reference)</li>
     * </ol>
     *
     * @param ticket the order ticket (includes handler for current customer, order
     *               string and customer ID)
     * @param type   "tea" or "coffee"
     */
    private void brewOrder(OrderTicket ticket, String type) {
        try {
            System.out.println("Barista started to brew: " + ticket.orderStr());
            brewArea.put(ticket.orderStr(), "BREWING");

            if (type.equalsIgnoreCase("tea")) {
                countTeaBrewing.incrementAndGet();
            } else if (type.equalsIgnoreCase("coffee")) {
                countCoffeeBrewing.incrementAndGet();
            }

            int timeToBrew = type.equalsIgnoreCase("tea") ? 30000 : 45000;
            Thread.sleep(timeToBrew);

            brewArea.remove(ticket.orderStr());
            trayArea.offer(ticket);

            if (type.equalsIgnoreCase("tea")) {
                countTeaBrewing.decrementAndGet();
            } else if (type.equalsIgnoreCase("coffee")) {
                countCoffeeBrewing.decrementAndGet();
            }

            System.out.println("Order ready for the pickup in tray: " + ticket.orderStr());

            ticket.handler()
                    .sendNotification("Your " + extractOrderDescription(ticket.orderStr()) + " is ready for pickup!");

        } catch (Exception e) {
            System.err.println("Error brewing order: " + e.getMessage());
        }
    }

    /**
     * Helper: Converts an order string into a readable description.
     *
     * @param orderStr full order string
     * @return order description
     */
    private String extractOrderDescription(String orderStr) {
        String orderItem = orderStr.contains(":") ? orderStr.substring(orderStr.indexOf(":") + 1) : orderStr;
        return orderItem;
    }

    /**
     * Gracefully shuts down the server.
     * <p>
     * Stops the scheduler thread and shuts down both thread pools.
     * This is triggered by a shutdown hook.
     */
    private void shutdown() {
        isShuttingDown = true;
        if (orderScheduler != null) {
            orderScheduler.interrupt();
        }
        clientHandlers.shutdown();
        baristaWorkers.shutdown();
    }

}
