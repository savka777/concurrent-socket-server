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
    private final ObjectOutputStream objectOut; // for sending objects (order object)
    private final ObjectInputStream objectIn; // for receiving objects

    public CustomerClient(Customer customer) throws IOException {
        this.customer = customer;
        Socket socket = new Socket("localhost", PORT);

        // Create object streams for serialization, convert object to x, take x convert
        // back to object
        this.objectOut = new ObjectOutputStream(socket.getOutputStream());
        this.objectIn = new ObjectInputStream(socket.getInputStream());

        // Text streams for order status etc...
        this.reader = new Scanner(socket.getInputStream());
        this.writer = new PrintWriter(socket.getOutputStream(), true);
    }

    public boolean connect() {
        try {
            // Send customer object to server
            objectOut.writeObject(customer);
            objectOut.flush();

            int connectionResponse = objectIn.readInt();
            return connectionResponse == 1; // true if success, false if failure
        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
            return false;
        }
    }

    public void getOrderStatus() {
        try{
            writer.println("ORDER_STATUS"); // send request to server, ask for order status
            writer.flush();

            String ack = reader.nextLine();
            if (ack.equalsIgnoreCase("ACK")) {
                System.out.println("Server got the ORDER STATUS REQUEST, recieving status now....");
                String status = reader.nextLine(); // get order message
                System.out.println(status); // print
            }else{
                /////////////
            }
        }catch(Exception e){
            System.err.println(e.getMessage());
        }
    
    }

    public void collectOrder() {
        // TODO Auto-generated method stub
        // why do we need to collect orders???
        throw new UnsupportedOperationException("Unimplemented method 'collectOrder'");
    }

    public void terminateSession() {
        try {
            writer.println("TERMINATE");
            writer.flush();

            // get ack
            if (reader.hasNextLine()) {
                reader.nextLine(); // get response
            }
        } catch (Exception e) {
            System.err.println("Error Terminating Session " + e.getMessage());
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
