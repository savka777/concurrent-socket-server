package Code.helpers;

import java.io.PrintWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

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

    private String customerName; // get the customer name
    private int customerId; // get the customer id
    private boolean isActive; // check is session is active

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
            
            System.out.println("Customer connected: " + customerName + " (ID: " + customerId + ")");

            for (Order order : customer.getOrders()){ // add customer order to the waiting order for barista to pick up
                String orderItem = order.toString();
                waitingArea.offer(orderItem);
                System.out.println("Added " + customer.getName() + " order to waiting area");

                baristaWorkers.submit(() ->{
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
       try{
            if(reader.hasNextLine()){
                String request = reader.nextLine().trim();
                System.out.println(request + " from client: " + customerName);
                
                if(request.equalsIgnoreCase("TERMINATE")){
                    writer.println("ACK");
                    isActive = false;
                }
            } else {
                // No input available, sleep briefly to prevent busy waiting
                Thread.sleep(100);
            }
       }catch(Exception e){ 
            System.err.println("Error handling request: " + e.getMessage());
            isActive = false;
       }
    }

    private void brewOrder(String orderItem){
        try{
            System.out.println("Barista started to brew: " + orderItem);
            waitingArea.remove(orderItem);
            brewingArea.put(orderItem, "BREWING");
            
            // Simulate brewing time (2-5 seconds)
            Thread.sleep(1000000 + (int)(Math.random() * 3000));

            brewingArea.remove(orderItem);
            trayArea.offer(orderItem);

            System.out.println(customerName + " order ready for pick up!");
        }catch(Exception e){
            //
        }
    }

    private void processOrder(String orderData) {

    }

    private void getOrderStatus() {

    }

    private void collectOrder() {

    }

    private void terminateSession() {

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