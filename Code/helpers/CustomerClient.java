package Code.helpers;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Scanner;

import Code.Customer;

/**
 * Client server responsible for communicating with the server.
 * Client can:
 * 1. Sends a request over for tea or coffee
 * 2. Request order status
 * 3. Collect order
 * 4. Terminate session (leave cafe)
 */
public class CustomerClient implements AutoCloseable {
    Customer customer;
    final int PORT = 8888;
    private final Scanner reader; // stream input from server
    private final PrintWriter writer; // stream output to server

    /// MIGHT NEED TO CHANGE, THEY USE THE SAME STREAM

    private final ObjectOutputStream objectOut; // for sending objects (order object)

    public CustomerClient(Customer customer) throws IOException {
        this.customer = customer;
        Socket socket = new Socket("localhost", PORT);

        // VERY FRAGILE, could do better but lazy for now (they share the same stream
        // which = problematic)
        this.objectOut = new ObjectOutputStream(socket.getOutputStream());
        this.reader = new Scanner(socket.getInputStream());
        this.writer = new PrintWriter(socket.getOutputStream(), true);
    }

    public boolean connect() {
        try {
            // Send customer object to server
            objectOut.writeObject(customer);
            objectOut.flush();

            // int connectionResponse = objectIn.readInt();
            String response = reader.nextLine();
            if (response.equalsIgnoreCase("CONNECTED")) {
                return true;
            }
            return false;
            // return connectionResponse == 1; // true if success, false if failure
        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
            return false;
        }
    }

    public void getOrderStatus() {
        try {
            writer.println("ORDER_STATUS"); // send request to server, ask for order status
            writer.flush();

            String response = reader.nextLine();

            if (response.equalsIgnoreCase("ORDER_STATUS_CONFIRMED")) {
                System.out.println("Server got the ORDER STATUS REQUEST, recieving status now....");
                String status = reader.nextLine(); // get order message
                System.out.println(status); // print
            } else {
                System.err.println("Servor ERROR, proccessing request");
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }

    }

    public boolean collectOrder() {
        try {
            writer.println("COLLECT_ORDER");
            writer.flush();

            String response = reader.nextLine();

            if (response.equalsIgnoreCase("COLLECT_ORDER_READY")) {
                System.out.println("Server got the COLLECT ORDER REQUEST");
                System.out.println("Thank you and have a nice day:)");
                return true; // Indicate successful collection
            } else if (response.equalsIgnoreCase("COLLECT_ORDER_NOT_READY")) {
                System.out.println("ORDER IS NOT READY FOR COLLECTION");
                return false; // Indicate order not ready
            } else if (response.equalsIgnoreCase("NO_ORDER_FOUND")) {
                System.out.println("No order found for " + customer.getName() + " - customer is idle");
                return false; // Indicate no order to collect
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        return false; // default to not ready
    }

    public void terminateSession() {
        try {
            writer.println("TERMINATE");
            writer.flush();

            // Confimation on our end
            if (reader.hasNextLine()) {
                String response = reader.nextLine().trim();
                if (response.equalsIgnoreCase("TERMINATE_CONFIRMED")) {
                    System.out.println("Server confirmed session termination.");
                } else {
                    System.err.println("Unexpected response during termination: " + response);
                }
            } else {
                System.err.println("No response from server during termination.");
            }

        } catch (Exception e) {
            System.err.println("Error terminating session: " + e.getMessage());
        } finally {
            close();
        }
    }


    @Override
    public void close() {
        try {
            if (writer != null) {
                writer.close();
            }

            if (reader != null) {
                reader.close();
            }
        } catch (Exception e) {
            System.err.println("Error closing resources: " + e.getMessage());
        }
    }
}
