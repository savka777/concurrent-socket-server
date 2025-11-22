package Code;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.UUID;
import Code.helpers.CustomerClient;
import Code.helpers.Order;
import Code.helpers.prettyPrint;

/**
 * Client-facing interface for interacting with the Barista.
 * <p>
 * A {@code Customer} represents a single user connected to the server.
 * This class is responsible for:
 * <ul>
 * <li>Collecting user input from the CLI</li>
 * <li>Packaging the user's name, ID, and orders into a {@link Customer}
 * object</li>
 * <li>Using {@link CustomerClient} to communicate with the server</li>
 * </ul>
 */
public class Customer implements Serializable {
    private static final long serialVersionUID = 1L;
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
        prettyPrint.printWelcome();

        System.out.println("What do you want? ");

        Scanner scanner = new Scanner(System.in);
        String line = scanner.nextLine();
        orders = cleanOrders(line);

        System.out.println("What is your name? ");
        String name = scanner.nextLine();

        Customer customer = new Customer(name, UUID.randomUUID().hashCode(), orders);
        try (CustomerClient client = new CustomerClient(customer)) {

            if (!client.connect()) {
                System.err.println("Failed to connect to cafe server. Please try again later.");
                return;
            }
            System.err.println("We have placed your order, please standby, " + customer.getName());

            while (true) {
                prettyPrint.printOptionsMenu();

                String command = scanner.nextLine();
                if (command.equalsIgnoreCase("order status")) {
                    client.getOrderStatus();
                } else if (command.equalsIgnoreCase("collect")) {
                    client.collectOrder();
                } else if (command.equalsIgnoreCase("exit")) {
                    client.terminateSession();
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("Error communicating with cafe: " + e.getMessage());
        }
    }

    /**
     * Parses a text order line from customer into a list of {@link Order} objects.
     * <p>
     * Expected format:
     * <ul>
     * <li>{@code "1 tea"}</li>
     * <li>{@code "2 coffee and 1 tea"}</li>
     * </ul>
     * Splits on "and", then expects each split to start with a quantity
     * followed by drink type.
     *
     * @param in input string from the user
     * @return list orders
     */
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
