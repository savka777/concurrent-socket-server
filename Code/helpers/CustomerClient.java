package Code.helpers;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
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

    public CustomerClient(Customer customer) throws IOException {
        this.customer = customer;
        Socket socket = new Socket("localhost", PORT);
        this.reader = new Scanner(socket.getInputStream());
        this.writer = new PrintWriter(socket.getOutputStream());
        this.writer.println(customer);

        int connectionResponse = reader.nextInt(); // get response from server
        if (connectionResponse != 1){throw new IOException();}
    }

    public void getOrderStatus() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getOrderStatus'");
    }

    public void collectOrder() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'collectOrder'");
    }

    public void terminateSession() {
        try{
            writer.println("TERMINATE");
            writer.flush();

            // get ack
            if (reader.hasNextLine()){
                reader.nextLine(); // get response
            }
        }catch(Exception e){
            System.err.println("Error Terminating Session " + e.getMessage());
        }finally{
            close();
        }
    }

    @Override
    public void close(){
        try{
            if(writer != null){
                writer.close();
            }

            if(reader != null){
                reader.close();
            }
        }catch(Exception e){
            System.err.println("Error closing resources: " + e.getMessage());
        }
    }
}
