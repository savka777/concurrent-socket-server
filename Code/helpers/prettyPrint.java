package Code.helpers;

import java.util.Scanner;

public class prettyPrint {

    public static void welcome(){
              System.out.println("     ________________________");
      System.out.println("    /                        \\");
      System.out.println("   | Hello Welcome to         |");
      System.out.println("   | Starbucks, what can I    |");
      System.out.println("   | get you?                 |");
      System.out.println("    \\________________________/");
      System.out.println("              |");
      System.out.println("              |");
      System.out.println("         .-\"\"\"\"-.");
      System.out.println("        /        \\");
      System.out.println("       |  ^    ^  |");
      System.out.println("       |     <    |");
      System.out.println("       |   \\___/  |");
      System.out.println("        \\        /");
      System.out.println("         '------'");
      System.out.println();
      
    }

    public static void askName(){
        System.out.println("     ________________________");
        System.out.println("    /                        \\");
        System.out.println("   | Great! What's your name  |");
        System.out.println("   | so I can get this order  |");
        System.out.println("   | started for you?         |");
        System.out.println("    \\________________________/");
        System.out.println("              |");
        System.out.println("              |");
        System.out.println("         .-\"\"\"\"-.");
        System.out.println("        /        \\");
        System.out.println("       |  ^    ^  |");
        System.out.println("       |     <    |");
        System.out.println("       |   \\___/  |");
        System.out.println("        \\        /");
        System.out.println("         '------'");
        System.out.println();

    }

    public static void repeatOrder(String name){
        System.out.println("     ________________________");
        System.out.println("    /                        \\");
        System.out.println("   | Thanks " + name + "! Can I    |");
        System.out.println("   | get you anything else?   |");
        System.out.println("    \\________________________/");
        System.out.println("              |");
        System.out.println("              |");
        System.out.println("         .-\"\"\"\"-.");
        System.out.println("        /        \\");
        System.out.println("       |  ^    ^  |");
        System.out.println("       |     <    |");
        System.out.println("       |   \\___/  |");
        System.out.println("        \\        /");
        System.out.println("         '------'");
        System.out.println();

    }

      public static void clearScreen() {
      System.out.print("\033[2J\033[H");
      System.out.flush();
  }
}
