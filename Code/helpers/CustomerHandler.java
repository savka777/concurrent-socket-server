package Code.helpers;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import Code.Customer;

/**
 * Handles all communication between the server and a SINGLE connected customer.
 * <p>
 * Each {@code CustomerHandler} runs on its own thread:
 * <ul>
 * <li>Receives the {@link Customer} object when connected</li>
 * <li>Enqueues the customers orders into the waiting area</li>
 * <li>Listens for commands such as ORDER_STATUS, NEW_ORDER, COLLECT_ORDER,
 * TERMINATE</li>
 * <li>Responds to commands via {@link ObjectOutputStream}</li>
 * <li>Sends async notifications</li>
 * </ul>
 *
 * <p>
 * <b>Protocol:</b> ALL messages are sent via object streams.
 */
public class CustomerHandler implements Runnable {

    // Passivae socket, I/O object streams
    private final Socket clientSocket;
    private final ObjectInputStream objectIn;
    private final ObjectOutputStream objectOut;

    // Shared Concurrent data structures
    private final BlockingQueue<OrderTicket> waitingArea;
    private final ConcurrentHashMap<String, String> brewingArea;
    private final BlockingQueue<OrderTicket> trayArea;
    private final ConcurrentHashMap<Integer, Boolean> activeCustomers;
    private final ConcurrentHashMap<Integer, String> idleCustomers;

    // Shared Mutex counter for connect clients
    private final AtomicInteger connectedClients;

    // Domain specific fields
    private ArrayList<Order> customerOrders;
    private String customerName;
    private int customerId;
    private boolean isIdle;
    private boolean isActive;

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
     * @param activeCustomers
     * @param idleCustomers
     * @throws IOException
     */
    public CustomerHandler(Socket clientSocket,
            LinkedBlockingQueue<OrderTicket> waitingArea,
            ConcurrentHashMap<String, String> brewArea,
            LinkedBlockingQueue<OrderTicket> trayArea,
            AtomicInteger connectedClients,
            ConcurrentHashMap<Integer, Boolean> activeCustomers,
            ConcurrentHashMap<Integer, String> idleCustomers) throws IOException {
        this.clientSocket = clientSocket;
        this.objectOut = new ObjectOutputStream(clientSocket.getOutputStream());
        this.objectOut.flush();
        this.objectIn = new ObjectInputStream(clientSocket.getInputStream());
        this.waitingArea = waitingArea;
        this.brewingArea = brewArea;
        this.trayArea = trayArea;
        this.connectedClients = connectedClients;
        this.activeCustomers = activeCustomers;
        this.idleCustomers = idleCustomers;
        this.isActive = true;
        this.isIdle = false;
    }

    /**
     * The Thread's entry point.
     * <p>
     * Connects the customer, then loop reading and handling commands
     * until the session ends or the thread is interrupted.
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
     * Reads the {@link Customer} object from the client and registers
     * their orders to the waiting area.
     * <p>
     * Also checks for abandoned orders in the tray that match this customer’s
     * request,
     * and reassign them if found.
     */
    private void connectCustomer() {
        try {
            Customer customer = (Customer) objectIn.readObject();

            this.customerName = customer.getName();
            this.customerId = customer.getId();
            this.customerOrders = customer.getOrders();
            System.out.println("Customer connected: " + customerName + " (ID: " + customerId + ")");

            activeCustomers.put(customerId, true);

            boolean hasAbandonedOrder = false;

            for (Order order : customer.getOrders()) {
                String orderItem = customerId + ":" + order.toString();

                String abandonedOrder = findAbandonedOrder(order.toString(), false);
                if (abandonedOrder != null) {
                    System.out.println("Found abandoned order, giving it to: " + getCustomerName());
                    hasAbandonedOrder = true;
                    continue;
                }

                OrderTicket ticket = new OrderTicket(customerId, orderItem, this);
                waitingArea.offer(ticket);
                System.out.println("Added " + customer.getName() + " order to waiting area: " + orderItem);
            }

            objectOut.writeObject("CONNECTED");
            objectOut.flush();

            if (hasAbandonedOrder) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                sendNotification("That was fast! We have your order complete :)");
            }

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
            Object requestObj = objectIn.readObject();
            if (requestObj instanceof String) {
                String request = ((String) requestObj).trim();
                System.out.println(request + " from client: " + customerName);

                if (request.equalsIgnoreCase("TERMINATE")) {
                    objectOut.writeObject("TERMINATE_CONFIRMED");
                    objectOut.flush();
                    isActive = false;
                } else if (request.equalsIgnoreCase("ORDER_STATUS")) {
                    objectOut.writeObject("ORDER_STATUS_CONFIRMED");
                    objectOut.flush();
                    if (isIdle) {
                        objectOut.writeObject("No order found for " + customerName + " - customer is idle");
                        objectOut.flush();
                    } else {
                        returnOrderStatus();
                    }
                } else if (request.equalsIgnoreCase("COLLECT_ORDER")) {
                    if (isIdle) {
                        objectOut.writeObject("NO_ORDER_FOUND");
                        objectOut.flush();
                    } else {
                        boolean isReady = true;
                        for (Order order : customerOrders) {
                            String orderStr = customerId + ":" + order.toString();
                            boolean found = trayArea.stream().anyMatch(ticket -> ticket.orderStr().equals(orderStr));
                            if (!found) {
                                isReady = false;
                                break;
                            }
                        }

                        if (isReady) {
                            for (Order order : customerOrders) {
                                String orderStr = customerId + ":" + order.toString();
                                trayArea.removeIf(ticket -> ticket.orderStr().equals(orderStr));
                            }
                            objectOut.writeObject("COLLECT_ORDER_READY");
                            objectOut.flush();
                            isIdle = true;
                            idleCustomers.put(customerId, customerName);
                            System.out
                                    .println("Customer " + customerName + " has collected their order and is now idle");
                        } else {
                            objectOut.writeObject("COLLECT_ORDER_NOT_READY");
                            objectOut.flush();
                        }
                    }
                } else if (request.equalsIgnoreCase("NEW_ORDER")) {
                    objectOut.writeObject("NEW_ORDER_READY");
                    objectOut.flush();

                    Object ordersObj = objectIn.readObject();
                    if (ordersObj instanceof ArrayList<?>) {
                        @SuppressWarnings("unchecked")
                        ArrayList<Order> newOrders = (ArrayList<Order>) ordersObj;

                        customerOrders.addAll(newOrders);
                        for (Order order : newOrders) {
                            String orderItem = customerId + ":" + order.toString();

                            String abandonedOrder = findAbandonedOrder(order.toString(), true);
                            if (abandonedOrder != null) {
                                System.out.println(
                                        "Found abandoned order for new request, giving it to: " + getCustomerName());
                                continue;
                            }

                            OrderTicket ticket = new OrderTicket(customerId, orderItem, this);
                            waitingArea.offer(ticket);
                            System.out.println("Added new order for " + customerName + ": " + orderItem);
                        }

                        isIdle = false;
                        idleCustomers.remove(customerId);

                        objectOut.writeObject("NEW_ORDER_CONFIRMED");
                        objectOut.flush();
                    }
                }
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
            String orderStr = customerId + ":" + order.toString();

            if (isInWaitingArea(orderStr)) {
                sb.append(customerName)
                        .append("'s order \"")
                        .append(order)
                        .append("\" is currently in the WAITING area. Last checked: ")
                        .append(LocalDateTime.now())
                        .append("\n");

            } else if (brewingArea.containsKey(orderStr)) {
                sb.append(customerName)
                        .append("'s order \"")
                        .append(order)
                        .append("\" is being BREWED. Last checked: ")
                        .append(LocalDateTime.now())
                        .append("\n");

            } else if (isInTrayArea(orderStr)) {
                sb.append(customerName)
                        .append("'s order \"")
                        .append(order)
                        .append("\" is READY for collection. Last checked: ")
                        .append(LocalDateTime.now())
                        .append("\n");

            } else {
                sb.append("Order \"")
                        .append(order)
                        .append("\" not found in any area : possible tracking error.\n");
            }
        }

        try {
            objectOut.writeObject(sb.toString());
            objectOut.flush();
        } catch (IOException e) {
            System.err.println("Error sending order status: " + e.getMessage());
        }
    }

    /**
     * Check for abandoned orders in tray area that match the requested order type
     * 
     * @param orderType the order to look for
     * @return orderItem string if found and reassigned, null otherwise
     */
    private String findAbandonedOrder(String orderType, boolean sendNotification) {
        for (OrderTicket ticket : trayArea) {
            if (ticket.orderStr().contains(":")) {
                String[] parts = ticket.orderStr().split(":", 2);
                try {
                    int originalCustomerId = Integer.parseInt(parts[0]);
                    String order = parts[1];

                    if (!activeCustomers.containsKey(originalCustomerId) && order.equals(orderType)) {
                        if (trayArea.remove(ticket)) {
                            String newOrderItem = customerId + ":" + order;
                            OrderTicket newTicket = new OrderTicket(customerId, newOrderItem, this);
                            trayArea.offer(newTicket);
                            System.out.println("Reassigning abandoned order '" + order + "' from customer "
                                    + originalCustomerId + " to customer " + customerId);

                            if (sendNotification) {
                                sendNotification("That was fast! We have your order complete :)");
                            }
                            return order;
                        }
                    }
                } catch (NumberFormatException e) {
                    continue;
                }
            }
        }
        return null;
    }

    /**
     * Sends an asynchronous notification to the client.
     * <p>
     * Notifications are prefixed with the {@code NOTE: } so the client listener
     * can tell them apart from normal command responses.
     *
     * @param text notification text to send
     */
    public synchronized void sendNotification(String text) {
        try {
            objectOut.writeObject("SERVER: " + text);
            objectOut.flush();
        } catch (IOException e) {
            System.err.println("Failed to send notification to " + customerName + ": " + e.getMessage());
        }
    }

    private void cleanup() {
        try {
            if (objectOut != null)
                objectOut.close();
            if (objectIn != null)
                objectIn.close();
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
            connectedClients.decrementAndGet();
            if (customerId != 0) {
                activeCustomers.remove(customerId);
                idleCustomers.remove(customerId);
                System.out.println("Customer " + customerName + " (ID: " + customerId
                        + ") disconnected and removed from active/idle lists.");
            } else {
                System.out.println("Customer " + customerName + " disconnected.");
            }
        } catch (IOException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }

    /** Helper: Returns true if a matching ticket is in waiting area. */
    private boolean isInWaitingArea(String orderStr) {
        for (OrderTicket t : waitingArea) {
            if (t.orderStr().equals(orderStr)) {
                return true;
            }
        }
        return false;
    }

    /** Helper: Returns true if a matching ticket is in tray area. */
    private boolean isInTrayArea(String orderStr) {
        for (OrderTicket t : trayArea) {
            if (t.orderStr().equals(orderStr)) {
                return true;
            }
        }
        return false;
    }

    /** Get customer name */
    public String getCustomerName() {
        return customerName;
    }

    /** Get customer ID */
    public int getCustomerId() {
        return customerId;
    }
}