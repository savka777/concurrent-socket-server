package Code.helpers;

import java.io.Serializable;

// Objects of this class can be converted into bytes and later restored.
public class Order implements Serializable {
    private static final long serialVersionUID = 1L;
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
