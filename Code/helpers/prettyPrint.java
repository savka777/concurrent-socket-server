package Code.helpers;

public class prettyPrint {

  private static final String RESET = "\u001B[0m";
  private static final String BOLD = "\u001B[1m";
  private static final String CYAN = "\u001B[36m";
  private static final String GREEN = "\u001B[32m";
  private static final String YELLOW = "\u001B[33m";

  public static String welcomeBanner() {
    return """

             (  )   (   )  )
              ) (   )  (  (
              ( )  (    ) )
              _____________
             <_____________> ___
             |             |/ _ \\
             |               | | |
             |               |_| |
          ___|             |\\___/
         /    \\___________/    \\
         \\_____________________/

         YO! Welcome to Coffee House
        """;
  }

  public static void printWelcome() {
    System.out.println(welcomeBanner());
  }

  public static void clearScreen() {
    System.out.print("\033[2J\033[H");
    System.out.flush();
  }

  public static void printOptionsMenu() {
    System.out.println(
        BOLD + "Options: " + RESET +
            CYAN + "[ order status ]" + RESET + "  " +
            GREEN + "[ collect ]" + RESET + "  " +
            YELLOW + "[ exit ]" + RESET);
  }

  
}
