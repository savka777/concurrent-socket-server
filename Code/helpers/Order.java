package Code.helpers;

public class Order {
    private int quantity;
    private String type;

    public Order(int quantity, String type){
        this.quantity = quantity;
        this.type = type;
    }
    
    public int getQuantity() {
        return quantity;
    }
    
    public String getType() {
        return type;
    }
    
    @Override
    public String toString() {
        return quantity + " " + type;
    }
}
