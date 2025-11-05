package Code.helpers;

import java.io.PrintWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import Code.Customer;

/**
 * Handles individual customer connections and communication.
 * Each CustomerHandler runs in its own thread from the client handler pool.
 * Responsible for:
 * - Receiving customer orders
 * - Providing order status updates
 * - Handling order collection
 * - Managing customer session termination
 */
public class CustomerHandler implements Runnable {
    private final Socket clientSocket; // standard socket for client - server communication
    private final Scanner reader; // stream input from client
    private final PrintWriter writer; // stream output to client
    private final ObjectInputStream objectIn; // for receiving objects
    private final ObjectOutputStream objectOut; // for sending objects

    private final BlockingQueue<String> waitingArea; // concurrent waiting area
    private final ConcurrentHashMap<String, String> brewingArea; // concurrent brewing area
    private final BlockingQueue<String> trayArea; // concurrent tray area

    private final ExecutorService baristaWorkers; // thread pool of barista's

    private ArrayList<Order> customerOrders; // will need to check status on orders later in the server
    private String customerName; // get the customer name
    private int customerId; // get the customer id
    private boolean isActive; // check is session is active

    private static final AtomicInteger countTeaBrewing = new AtomicInteger(0);
    private static final AtomicInteger countCoffeeBrewing = new AtomicInteger(0);


    public CustomerHandler(Socket clientSocket,
            LinkedBlockingQueue<String> waitingArea,
            ConcurrentHashMap<String, String> brewArea,
            LinkedBlockingQueue<String> trayArea,
            ExecutorService baristaWorkers) throws IOException {
        this.clientSocket = clientSocket;
        // Create object streams first (order matters!)
        this.objectIn = new ObjectInputStream(clientSocket.getInputStream());
        this.objectOut = new ObjectOutputStream(clientSocket.getOutputStream());

        // Create text streams for simple communication
        this.reader = new Scanner(clientSocket.getInputStream());
        this.writer = new PrintWriter(clientSocket.getOutputStream(), true);

        this.waitingArea = waitingArea;
        this.brewingArea = brewArea;
        this.trayArea = trayArea;
        this.baristaWorkers = baristaWorkers;
        this.isActive = true;
    }

    @Override
    public void run() {
        try {
            connectCustomer();
            // while connection is active and there is no interuption than handle customer
            // order
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
     * Handles initial customer connection - receives customer info and sends
     * confirmation
     */
    private void connectCustomer() {
        try {
            // Read customer object from client
            Customer customer = (Customer) objectIn.readObject();

            // Extract customer info
            this.customerName = customer.getName();
            this.customerId = customer.getId();
            this.customerOrders = customer.getOrders();
            System.out.println("Customer connected: " + customerName + " (ID: " + customerId + ")");


            for (Order order : customer.getOrders()) { // add customer order to the waiting order for barista to pick up
                String orderItem = order.toString();
                waitingArea.offer(orderItem);
                System.out.println("Added " + customer.getName() + " order to waiting area");
                Thread.sleep(200);
                baristaWorkers.submit(() -> {
                    brewOrder(orderItem);
                });
            }

            // Send connection confirmation (1 = success)
            objectOut.writeInt(1);
            objectOut.flush();

        } catch (Exception e) {
            System.err.println("Error connecting customer: " + e.getMessage());
            try {
                objectOut.writeInt(0); // 0 = failure
                objectOut.flush();
            } catch (IOException ex) {
                System.err.println("Failed to send error response: " + ex.getMessage());
            }
            isActive = false;
        }
    }

    private void handleCustomerRequest() {
        try {
            if (reader.hasNextLine()) {
                String request = reader.nextLine().trim();
                System.out.println(request + " from client: " + customerName);

                if (request.equalsIgnoreCase("TERMINATE")) {
                    writer.println("ACK");
                    isActive = false;
                }
                else if (request.equalsIgnoreCase("ORDER_STATUS")){
                    writer.println("ACK");
                    returnOrderStatus();
                }
                else if(request.equalsIgnoreCase("COLLECT_ORDER")){
                    // DO SOMETHING
                }
            } else {
                // // No input available, sleep briefly to prevent busy waiting
                // Thread.sleep(100);
            }
        } catch (Exception e) {
            System.err.println("Error handling request: " + e.getMessage());
            isActive = false;
        }
    }

    private void brewOrder(String orderItem) {
        try {
            String[] parts = orderItem.split(" ");
            int quntity = Integer.parseInt(parts[0]);
            String type = parts[1].toLowerCase();

            if(type.equalsIgnoreCase("tea") && countTeaBrewing.get() >= 2){
                // can only brew two teas at once!
                
            }

            if(type.equalsIgnoreCase("coffee") && countCoffeeBrewing.get() >= 2){
                // can only brew two coffees at once!
            }

            System.out.println("Barista started to brew: " + orderItem);
            waitingArea.remove(orderItem);
            brewingArea.put(orderItem, "BREWING");


            if(type.equalsIgnoreCase("tea")){
                countTeaBrewing.incrementAndGet();
            }

            if(type.equalsIgnoreCase("coffee")){
                countCoffeeBrewing.incrementAndGet();
            }

            int timeToBrew = 0;
            if (type.equalsIgnoreCase("tea")) {timeToBrew = 30000;}
            if (type.equalsIgnoreCase("coffee")) {timeToBrew = 45000;}
            Thread.sleep(timeToBrew);

            brewingArea.remove(orderItem);
            trayArea.offer(orderItem);

            if (type.equalsIgnoreCase("tea")) {countTeaBrewing.decrementAndGet();}
            if (type.equalsIgnoreCase("coffee")) {countCoffeeBrewing.decrementAndGet();}

            System.out.println(customerName + " order ready for pick up in the Tray Area!");
        } catch (Exception e) {
            // to do
        }
    }

    private void returnOrderStatus() {

        StringBuilder sb = new StringBuilder();

        for(Order order : customerOrders){
            String orderStr = order.toString();

            if (waitingArea.contains(orderStr)){
                sb.append(getCustomerName() + "'s" + "is in the WAITING area" + ". Last Checked: " + LocalDateTime.now());
            }else if (brewingArea.containsKey(orderStr)){
                sb.append(getCustomerName() + "'s" + "is in the BREWING..." + ". Last Checked: " + LocalDateTime.now());
            }else if(trayArea.contains(orderStr)){
                sb.append(getCustomerName() + "'s" + "is in the TRAY area, ready for collection" + ". Last Checked: " + LocalDateTime.now());
            }else{
                sb.append("IT HAS TO BE SOMEWHERE SO THIS IS A MISTAKE IF IT GET'S HERE");
            }
        }
        writer.println(sb.toString());
        writer.flush();
    }

    private void terminateSession() {
        // DOES SERVER NEED TO TERMINATE ALL CLIENT SESSIONS? 
    }

    private void cleanup() {
        try {
            if (writer != null)
                writer.close();
            if (reader != null)
                reader.close();
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
            System.out.println("Customer " + customerName + " disconnected.");
        } catch (IOException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }

    public String getCustomerName() {
        return customerName;
    }

    public int getCustomerId() {
        return customerId;
    }
}