package Code;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Scanner;

import Code.helpers.CustomerClient;
import Code.helpers.Order;
import Code.helpers.prettyPrint;

/*
 * Client Facing Interface, User Communicates through Customer, Customer interacts with Client to send 
 * requests over to the server.
 */
public class Customer implements Serializable {
    private static final long serialVersionUID = 1L; // unique in order to identify the serialized object
    private String name; 
    private int id;
    private ArrayList<Order> orders;

    public Customer(String name, int id, ArrayList<Order> orders) {
        this.name = name;
        this.id = id;
        this.orders = orders;
    }

    public static void main(String[] args) {
        ArrayList<Order> orders; // collect orders for this customer, send to client to send to server to process

        // GET ORDERS
        System.out.println("Welcome, what do you want? ");
        
        Scanner scanner = new Scanner(System.in); // take input from console
        String line = scanner.nextLine(); // store input in string
        orders = cleanOrders(line); // process input

        // STREAM ORDERS TO SERVER:
  
        System.out.println("What is your name? ");
        String name = scanner.nextLine();
        // CONFIRM ORDER HERE        

        Customer customer = new Customer(name, 1, orders); // construct object to be sent to server
        try (CustomerClient client = new CustomerClient(customer)) { // setup client, send customer 

            // Problems with connection to server
            if (!client.connect()) {
                System.err.println("Failed to connect to cafe server. Please try again later.");
                return;
            }
            System.err.println("We have placed your order, please standby, " + customer.getName()); 

            // Main loop, after placing order, client interacts with barista
            while (true) {
                System.err.println("Options:" + "--|order status|-- " + "--|collect| " + "--|exit|-- ");

                String command = scanner.nextLine(); // store command

                // tell client to send command over to server
                if (command.equalsIgnoreCase("order status")) {
                    client.getOrderStatus();
                } else if (command.equalsIgnoreCase("collect")) {
                    client.collectOrder(); // to do
                } else if (command.equalsIgnoreCase("exit")) {
                    client.terminateSession(); // done
                    break; // Exit the loop
                }
            }
        } catch (Exception e) {
            System.err.println("Error communicating with cafe: " + e.getMessage());
        }
    }

    public static ArrayList<Order> cleanOrders(String in) {
        ArrayList<Order> orders = new ArrayList<>();

        String cleanText = in.replaceFirst("^order\\s+", "").trim();

        String[] items = cleanText.split("\\s+and\\s+");

        for (String item : items) {
            String[] orderAndQuantity = item.trim().split("\\s+");
            if (orderAndQuantity.length >= 2) {
                try {
                    int quantity = Integer.parseInt(orderAndQuantity[0]);
                    String type = orderAndQuantity[1];
                    orders.add(new Order(quantity, type));
                } catch (NumberFormatException e) {
                    System.out.println("Sorry I dont not understand your order");
                }
            }
        }
        return orders;
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    public ArrayList<Order> getOrders() {
        return orders;
    }
}
