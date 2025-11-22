package Code.helpers;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import Code.Customer;

/**
 * Client-side communication with server.
 * <p>
 * {@code CustomerClient} is responsible for all socket communication with the
 * server. It follows an object ONLY protocol:
 * <ul>
 * <li>Commands are sent as {@link String} objects (e.g., "ORDER_STATUS")</li>
 * <li>Payloads are sent as normal Java objects (e.g., {@link Customer},
 * {@link ArrayList} of {@link Order})</li>
 * </ul>
 *
 * <p>
 * To support asynchronous server notifications, this client also starts a
 * background
 * listener thread that continuously reads from {@link ObjectInputStream}.
 * <ul>
 * <li>Messages with {@code "SERVER:"} are treated as notifications
 * and printed immediately.</li>
 * <li>All other messages are treated as responses to commands and placed into
 * {@code responseQueue} for the main thread to consume.</li>
 * </ul>
 *
 * <p>
 * Supported operations:
 * <ol>
 * <li>Connect and send initial {@link Customer} object</li>
 * <li>Request order status</li>
 * <li>Collect ready orders</li>
 * <li>Place new orders after connecting</li>
 * <li>Terminate the session</li>
 * </ol>
 */
public class CustomerClient implements AutoCloseable {
    Customer customer;

    // Connection port, I/O Streams
    final int PORT = 8888;
    private final ObjectInputStream objectIn;
    private final ObjectOutputStream objectOut;

    // Listens for incoming messages from server
    private Thread listenerThread;

    private volatile boolean running = true;

    // Queues used to store responses that come from server, listenr thread pushes
    // into this queue, main thread takes
    private final BlockingQueue<String> responseQueue = new LinkedBlockingQueue<>();

    /**
     * Creates a new {@code CustomerClient} and open a socket connection to the
     * server.
     *
     * <p>
     * A listener thread is started automatically to handle async notifications and
     * responses.
     *
     * @param customer the customer using this client
     * @throws IOException if the socket or the streams have problems
     */
    public CustomerClient(Customer customer) throws IOException {
        this.customer = customer;
        Socket socket = new Socket("localhost", PORT);
        this.objectOut = new ObjectOutputStream(socket.getOutputStream());
        this.objectOut.flush();
        this.objectIn = new ObjectInputStream(socket.getInputStream());

        startListenerThread();
    }

    /**
     * Starts a background listener thread that reads messages from the
     * server.
     * <p>
     * Behavior:
     * <ul>
     * <li>If message starts with {@code "SERVER:"}, it is printed immediately as a
     * notification.</li>
     * <li>Otherwise, the message is added to {@link #responseQueue} as a normal
     * responseS.</li>
     * </ul>
     */
    private void startListenerThread() {
        listenerThread = new Thread(() -> {
            try {
                while (running) {
                    Object obj = objectIn.readObject();
                    if (!(obj instanceof String))
                        continue;

                    String msg = (String) obj;
                    if (msg.startsWith("SERVER:")) {
                        System.out.println("\n" + msg.substring(5).trim());
                        System.out.print("> ");
                    } else {
                        responseQueue.offer(msg);
                    }
                }
            } catch (Exception e) {
                if (running) {
                    System.err.println("Connection lost: " + e.getMessage());
                }
            }
        });
        listenerThread.start();
    }

    /**
     * Sends the initial {@link Customer} object to the server to establish the
     * start of session.
     *
     * @return true if the server replies {@code "CONNECTED"}
     */
    public boolean connect() {
        try {
            objectOut.writeObject(customer);
            objectOut.flush();

            String response = responseQueue.take();
            if (response.equalsIgnoreCase("CONNECTED")) {
                return true;
            }
            return false;
        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Requests a status update for orders that belonging to this customer.
     * <p>
     * Protocol:
     * <ol>
     * <li>Send {@code "ORDER_STATUS"}</li>
     * <li>Wait for {@code "ORDER_STATUS_CONFIRMED"}</li>
     * <li>Read and print the status message</li>
     * </ol>
     */
    public void getOrderStatus() {
        try {
            objectOut.writeObject("ORDER_STATUS");
            objectOut.flush();

            String response = responseQueue.take();
            if (response.equalsIgnoreCase("ORDER_STATUS_CONFIRMED")) {
                System.out.println("Server got the ORDER STATUS REQUEST, receiving status now....");
                String status = responseQueue.take();
                System.out.println(status);
            } else {
                System.err.println("Server ERROR, processing request");
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    /**
     * Attempts to collect the customers orders.
     * <p>
     * Protocol:
     * <ol>
     * <li>Send {@code "COLLECT_ORDER"}</li>
     * <li>Wait for one of:
     * <ul>
     * <li>{@code "COLLECT_ORDER_READY"}</li>
     * <li>{@code "COLLECT_ORDER_NOT_READY"}</li>
     * <li>{@code "NO_ORDER_FOUND"}</li>
     * </ul>
     * </li>
     * </ol>
     *
     * @return true if collection succeeded
     */
    public boolean collectOrder() {
        try {
            objectOut.writeObject("COLLECT_ORDER");
            objectOut.flush();

            String response = responseQueue.take();
            if (response.equalsIgnoreCase("COLLECT_ORDER_READY")) {
                System.out.println("Server got the COLLECT ORDER REQUEST");
                System.out.println("Thank you and have a nice day:)");
                return true;
            } else if (response.equalsIgnoreCase("COLLECT_ORDER_NOT_READY")) {
                System.out.println("ORDER IS NOT READY FOR COLLECTION");
                return false;
            } else if (response.equalsIgnoreCase("NO_ORDER_FOUND")) {
                System.out.println("No order found for " + customer.getName() + " - customer is idle");
                return false;
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        return false;
    }

    /**
     * Terminates the session with server.
     * <p>
     * Protocol:
     * <ol>
     * <li>Send {@code "TERMINATE"}</li>
     * <li>Wait for {@code "TERMINATE_CONFIRMED"}</li>
     * </ol>
     */
    public void terminateSession() {
        try {
            objectOut.writeObject("TERMINATE");
            objectOut.flush();

            String response = responseQueue.take();
            if (response.equalsIgnoreCase("TERMINATE_CONFIRMED")) {
                System.out.println("Server confirmed session termination.");
            } else {
                System.err.println("Unexpected response during termination: " + response);
            }

        } catch (Exception e) {
            System.err.println("Error terminating session: " + e.getMessage());
        } finally {
            close();
        }
    }

    /**
     * Sends a new set of orders to the server after the initial connection.
     * <p>
     * Protocol:
     * <ol>
     * <li>Send {@code "NEW_ORDER"}</li>
     * <li>Wait for {@code "NEW_ORDER_READY"}</li>
     * <li>Send {@code ArrayList<Order>} payload</li>
     * <li>Wait for {@code "NEW_ORDER_CONFIRMED"}</li>
     * </ol>
     *
     * @param newOrders list of new orders to place
     */
    public void placeNewOrder(ArrayList<Order> newOrders) {
        try {
            objectOut.writeObject("NEW_ORDER");
            objectOut.flush();

            String response = responseQueue.take();
            if (response.equalsIgnoreCase("NEW_ORDER_READY")) {
                objectOut.writeObject(newOrders);
                objectOut.flush();

                String confirmation = responseQueue.take();
                if (confirmation.equalsIgnoreCase("NEW_ORDER_CONFIRMED")) {
                    System.out.println("New orders placed successfully!");
                } else {
                    System.err.println("Error placing new orders");
                }
            } else {
                System.err.println("Server not ready for new orders");
            }
        } catch (Exception e) {
            System.err.println("Error placing new order: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        try {
            running = false;

            if (objectOut != null) {
                objectOut.close();
            }

            if (objectIn != null) {
                objectIn.close();
            }

            if (listenerThread != null) {
                listenerThread.interrupt();
            }
        } catch (Exception e) {
            System.err.println("Error closing resources: " + e.getMessage());
        }
    }
}
