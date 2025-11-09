package Code.helpers;

import java.io.PrintWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import Code.Customer;

/**
 * Handles communication between the server and a single connected client.
 * <p>
 * Each {@code CustomerHandler} instance runs on its own thread, maintaining an
 * active session with one customer. It processes all client requests
 * (e.g., placing orders, checking status, collecting orders) until the customer
 * disconnects or the session is terminated.
 */
public class CustomerHandler implements Runnable {
    private final Socket clientSocket; // Standard socket for communication
    private final Scanner reader; // reader to stream input from client
    private final PrintWriter writer; // writer to stream output to client
    private final ObjectInputStream objectIn; // objectIn to stream objects from client

    private final BlockingQueue<String> waitingArea; // Thread-Safe Queue, following FIFO principles, use internal
                                                     // synchronization to coordinate threads
    private final ConcurrentHashMap<String, String> brewingArea; // Thread-Safe Hashmap, Key: Order String, Value :
                                                                 // Customer ID (NOT YET IMPLEMENTED)
    private final BlockingQueue<String> trayArea; // Thread-Safe Queue, tray area represents order's that have been
                                                  // fulfilled are waiting to be collected

    private final AtomicInteger connectedClients; // Clinet counter, used for the server to keep track of how many
                                                  // clients are connected

    private ArrayList<Order> customerOrders; // Keep's track of the order placed by the customer
    private String customerName;
    private int customerId;

    private boolean isActive; // Check if client session is still active

    /**
     * Constructs a new {@code CustomerHandler} for managing communication with a
     * connected client.
     * Initializes all required I/O streams and shared server resources.
     * 
     * @param clientSocket
     * @param waitingArea
     * @param brewArea
     * @param trayArea
     * @param connectedClients
     * @throws IOException
     */
    public CustomerHandler(Socket clientSocket,
            LinkedBlockingQueue<String> waitingArea,
            ConcurrentHashMap<String, String> brewArea,
            LinkedBlockingQueue<String> trayArea,
            AtomicInteger connectedClients) throws IOException {
        this.clientSocket = clientSocket;
        this.objectIn = new ObjectInputStream(clientSocket.getInputStream());
        this.reader = new Scanner(clientSocket.getInputStream());
        this.writer = new PrintWriter(clientSocket.getOutputStream(), true);
        this.waitingArea = waitingArea;
        this.brewingArea = brewArea;
        this.trayArea = trayArea;
        this.connectedClients = connectedClients;
        this.isActive = true;
    }

    /**
     * Entry point for the CustomerHandler thread.
     * <p>
     * Continuously manages communication with a SINGLE connected customer.
     * Establishes initial connection, processes client requests in a loop
     * until the session ends or the thread is interrupted, and then performs
     * cleanup.
     */
    @Override
    public void run() {
        try {
            connectCustomer();
            while (isActive && !Thread.currentThread().isInterrupted()) {
                handleCustomerRequest();
            }
        } catch (Exception e) {
            System.err.println("Error handling customer: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    /**
     * Establishes the initial connection with a newly connected customer.
     * <p>
     * Receives the {@link Customer} object sent from the client, extracts
     * identification and order details, and places each order into the shared
     * waiting area queue for processing by the scheduler.
     * <p>
     * Sends a confirmation signal back to the client (1 for success, 0 for failure)
     * to acknowledge the connection status.
     */
    private void connectCustomer() {
        try {
            Customer customer = (Customer) objectIn.readObject(); // CHANGE

            this.customerName = customer.getName();
            this.customerId = customer.getId();
            this.customerOrders = customer.getOrders();
            System.out.println("Customer connected: " + customerName + " (ID: " + customerId + ")");

            for (Order order : customer.getOrders()) {
                String orderItem = order.toString();
                waitingArea.offer(orderItem);
                System.out.println("Added " + customer.getName() + " order to waiting area: " + orderItem);
            }

            writer.println("CONNECTED");
            writer.flush();

        } catch (Exception e) {
            System.err.println("Error connecting customer: " + e.getMessage());
            isActive = false;
        }
    }

    /**
     * Listens for and processes incoming requests from the connected customer.
     * <p>
     * This method runs continuously while the session is active. It reads
     * commands that are sent by the client (such as {@code ORDER_STATUS},
     * {@code COLLECT_ORDER},
     * or {@code TERMINATE}), performs the corresponding server-side actions, and
     * sends appropriate acknowledgements or responses back to the client.
     */
    private void handleCustomerRequest() {
        try {
            if (reader.hasNextLine()) {
                String request = reader.nextLine().trim();
                System.out.println(request + " from client: " + customerName);

                if (request.equalsIgnoreCase("TERMINATE")) {
                    writer.println("TERMINATE_CONFIRMED");
                    isActive = false;
                } else if (request.equalsIgnoreCase("ORDER_STATUS")) {
                    writer.println("ORDER_STATUS_CONFIRMED");
                    returnOrderStatus();
                } else if (request.equalsIgnoreCase("COLLECT_ORDER")) {
                    // check if order can be collected
                    boolean isReady = true;
                    for (Order order : customerOrders) {
                        String orderStr = order.toString();
                        if (!trayArea.contains(orderStr)) {
                            isReady = false;
                        } else {
                            trayArea.remove(orderStr);
                        }
                    }

                    if (isReady == true) {
                        writer.println("COLLECT_ORDER_READY");
                    } else {
                        writer.println("COLLECT_ORDER_NOT_READY");
                    }
                }
            } else {
                ////////////////////////////////////////
            }
        } catch (Exception e) {
            System.err.println("Error handling request: " + e.getMessage());
            isActive = false;
        }
    }

    /**
     * Builds and sends a summary of the customer's order status.
     * <p>
     * Checks each order against the server’s shared areas (waiting, brewing, tray)
     * and returns an update back to the client.
     */
    private void returnOrderStatus() {
        StringBuilder sb = new StringBuilder();

        for (Order order : customerOrders) {
            String orderStr = order.toString();

            if (waitingArea.contains(orderStr)) {
                sb.append(getCustomerName())
                        .append("'s order \"")
                        .append(orderStr)
                        .append("\" is currently in the WAITING area. Last checked: ")
                        .append(LocalDateTime.now())
                        .append(System.lineSeparator());
            } else if (brewingArea.containsKey(orderStr)) {
                sb.append(getCustomerName())
                        .append("'s order \"")
                        .append(orderStr)
                        .append("\" is being BREWED. Last checked: ")
                        .append(LocalDateTime.now())
                        .append(System.lineSeparator());
            } else if (trayArea.contains(orderStr)) {
                sb.append(getCustomerName())
                        .append("'s order \"")
                        .append(orderStr)
                        .append("\" is READY for collection. Last checked: ")
                        .append(LocalDateTime.now())
                        .append(System.lineSeparator());
            } else {
                sb.append("⚠️ Order \"")
                        .append(orderStr)
                        .append("\" not found in any area — possible tracking error.")
                        .append(System.lineSeparator());
            }
        }

        writer.println(sb.toString());
        writer.flush();
    }

    /**
     * 
     */
    private void terminateSession() {
        // DOES SERVER NEED TO TERMINATE ALL CLIENT SESSIONS?
        // yes, they pick up there order.... and release thread for server
    }

    /**
     * 
     */
    private void cleanup() {
        try {
            if (writer != null)
                writer.close();
            if (reader != null)
                reader.close();
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
            connectedClients.decrementAndGet(); // Client disconnected
            System.out.println("Customer " + customerName + " disconnected.");
        } catch (IOException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }

    /**
     * @return
     */
    public String getCustomerName() {
        return customerName;
    }

    /**
     * @return
     */
    public int getCustomerId() {
        return customerId;
    }
}