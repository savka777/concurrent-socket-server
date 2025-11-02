package Code;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

import Code.helpers.CustomerClient;
import Code.helpers.Order;
import Code.helpers.prettyPrint;

/*
 * Client Facing Interface, User Communicates through Customer, Customer interacts with Client to send 
 * requests over to the server.
 */
public class Customer {
    private String name;
    private int id;
    private ArrayList<Order> orders;

    public Customer(String name, int id, ArrayList<Order> orders) {
        this.name = name;
        this.id = id;
        this.orders = orders;
    }

    public static void main(String[] args) {
        ArrayList<Order> orders;

        // Get order
        // prettyPrint.welcome();
        System.out.println("Welcome, what do you want? ");
        Scanner scanner = new Scanner(System.in);
        String line = scanner.nextLine();
        orders = cleanOrders(line);
        prettyPrint.clearScreen();

        // if (orders.isEmpty()) {
        // System.out.println("I'm sorry, I did not get that, can you repeat again?");
        // } else {
        // System.out.println("Your orders:");
        // for (Order o : orders){
        // System.out.println("" + o.toString());
        // }
        // }

        // Ask for name
        // prettyPrint.askName();
        System.out.println("What is your name? ");
        String name = scanner.nextLine();
        // prettyPrint.clearScreen();
        // prettyPrint.repeatOrder(name);

        Customer customer = new Customer(name, 1, orders);
        try (CustomerClient client = new CustomerClient(customer)) { // send to client to send to server
            while (true) {
                System.out.println("Options:" + "|order status| " + "|collect| " + "|exit| ");
                String command = scanner.nextLine();

                if (command.equalsIgnoreCase("order status")) {
                    client.getOrderStatus();
                } else if (command.equalsIgnoreCase("collect")) {
                    client.collectOrder();
                } else if (command.equalsIgnoreCase("exit")) {
                    client.terminateSession();
                }
            }
        } catch (Exception e) {

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
}
