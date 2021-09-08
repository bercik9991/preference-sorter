package com.bercik9991.preferencesort;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collector;

import static java.util.stream.Collectors.*;

public class PreferenceSorter {

  private static final String APP_VERSION = "0.0.1";
  private static final Integer MAX_LIST_SIZE = 2048;

  // TODO In short term, implement input handling (including error handling) in scanner wrapper
  // TODO In long term, replace scanner with alternative, non-blocking input handlers
  private final Scanner scanner = new Scanner(System.in);
  private Predicate<String> predicate = item -> true;
  private int pageSize = 20;
  private int currentPageNumber = 1;

  // TODO Find a way to remember items' original indices despite filtering
  // TODO Is TreeMap<Integer, String> good for this use case?
  // private List<String> items = new ArrayList<>();
  private List<String> items = new ArrayList<>(480);

  public static void main(String[] args) {
    PreferenceSorter preferenceSorter = new PreferenceSorter();
    preferenceSorter.menu();
  }

  private void menu() {
    prompt();

    String input;
    boolean proceed = true;
    while (proceed && scanner.hasNextLine()) {
      input = scanner.nextLine();
      switch (input) {
        // Basic commands
        case "1": case "print":     printList(predicate, currentPageNumber); break;
        case "2": case "add":       add(); break;
        case "3": case "edit":      edit(); break;
        case "4": case "move":      move(); break;
        case "5": case "delete":    delete(); break;
        case "6": case "find":      find(); break;
        case "7": case "sort":      sort(); break;
        // case "8": case "":          (); break;
        // case "9": case "":          (); break;
        case "0": case "commands":  printCommands(); break;
        case "": case "exit":       exit(); break;

        // Additional commands:
        case "swap":                swap(); break;
        case "clear":               clear(); break;
        case "filter":              filter(); break;
        case "load":                loadFromFile(); break;
        case "save":                saveToFile(); break;
        case "help":                printHelp(); break;
        default:                    handleOtherCases(input); break;
      }
      proceed = shouldContinue(input);
      if (proceed) {
        prompt();
      }
    }
    scanner.close();
  }

  private void printList(Predicate<String> predicate, int pageNumber) {
    int listSize = items.size();

    if (listSize < 1) {
      System.out.println("List is empty.");
      return;
    }

    final int pageCount = getPageCount();
    if (pageNumber < 1) {
      setCurrentPageNumber(1);
    } else if (pageNumber > pageCount) {
      setCurrentPageNumber(pageCount);
    }

    System.out.println("List size: " + listSize);
    AtomicInteger counter = new AtomicInteger(0);
    items.stream()
      .filter(predicate)
      .skip((long) (pageNumber - 1) * pageSize)
      .limit(pageSize)
      .forEach(item -> printListItem(counter.incrementAndGet(), item));
  }

  private void add() {
    int endIndex = items.size(), newItemIndex = endIndex;
    if (endIndex >= MAX_LIST_SIZE) {
      System.out.println("Cannot add more items.\nMax allowed list size reached.");
      return;
    }

    System.out.println("Add new item:");
    String newItem = "";
    if (scanner.hasNextLine()) {
      newItem = scanner.nextLine();
    }

    System.out.printf("New item index [%d]: ", endIndex + 1);
    if (scanner.hasNextLine()) {
      String input = scanner.nextLine();
      if (StringUtils.isBlank(input)) {
        System.out.println("New item added to the end of the list.");
      } else {
        newItemIndex = Integer.parseInt(input);
        // TODO New item index - Error handling
      }
    }

    items.add(newItemIndex, newItem);
  }

  private void addImmediately(String input) {
    items.add(input);
    System.out.println("New item added to the end of the list.");
  }

  private void edit() {
    System.out.println("Edit item");
    System.out.print("Enter item index: ");

    int itemIndex = -1;
    if (scanner.hasNextLine()) {
      itemIndex = Integer.parseInt(scanner.nextLine());
      final int listSize = items.size();
      if (itemIndex < 1 || itemIndex > listSize) {
        System.out.println("Index [" + itemIndex + "] is out of bounds [1, " + listSize + "].");
        return;
      }
      --itemIndex;
    }

    String itemValue = "";
    if (scanner.hasNextLine()) {
      itemValue = scanner.nextLine();
    }

    items.set(itemIndex, itemValue);
  }

  private void move() {
    System.out.println("Move item");
    int i = 0, j = 0, listSize = items.size();

    System.out.print("Enter item's current index: ");
    if (scanner.hasNextLine()) {
      i = Integer.parseInt(scanner.nextLine());

      if (i < 1 || i > listSize) {
        System.out.println("Index [" + i + "] is out of bounds [1, " + listSize + "].");
        return;
      }
      --i;
    }

    System.out.print("Enter item's destination index: ");
    if (scanner.hasNextLine()) {
      j = Integer.parseInt(scanner.nextLine());

      if (j < 1 || j > listSize) {
        System.out.println("Index [" + j + "] is out of bounds [1, " + listSize + "].");
        return;
      }
      --j;
    }

    int direction = Integer.signum(i - j);
    if (direction != 0) {
      Collections.rotate(items.subList(Math.min(i, j), Math.max(i, j) + 1), direction);
    } else {
      System.out.println("Source index is the same as destination index.");
    }
  }

  private void delete() {
    System.out.println("Delete item");
    System.out.print("Enter item index: ");

    int i = -1, listSize = items.size();
    if (scanner.hasNextLine()) {
      i = Integer.parseInt(scanner.nextLine());

      if (i < 1 || i > listSize) {
        System.out.println("Index [" + i + "] is out of bounds [1, " + listSize + "].");
        return;
      }
      --i;
    }

    items.remove(i);
  }

  private void find() {
    String searchPhrase = "";
    if (scanner.hasNextLine()) {
      searchPhrase = scanner.nextLine();
    }

    String finalSearchPhrase = searchPhrase;
    Predicate<String> predicate = item -> item.contains(finalSearchPhrase);
    printList(predicate, currentPageNumber);
  }

  public void sort() {
    System.out.println("Sort items");
    if (items.size() > 1) {
      items.sort(this::compareByPreference);
      System.out.println("List is sorted.");
    } else {
      System.out.println("List has " + items.size() + " items.");
    }
  }

  private int compareByPreference(String b, String a) {
    System.out.println("1. " + a + "\n2. " + b);

    System.out.print("Which item is 'greater'? [1]: ");

    String input = "";
    if (scanner.hasNextLine()) {
      input = scanner.nextLine();
    }

    switch (input) {
      case "1": return 1;
      case "2": return -1;
      default:
        System.out.println("Wrong input. Using default value of 1.");
        return 1;
    }
  }

  private void swap() {
    System.out.println("Swap items");
    int i = 0, j = 0, listSize = items.size();

    System.out.print("Enter index of the 1st item: ");
    if (scanner.hasNextLine()) {
      i = Integer.parseInt(scanner.nextLine());

      if (i < 1 || i > listSize) {
        System.out.println("Index [" + i + "] is out of bounds [1, " + listSize + "].");
        return;
      }
      --i;
    }

    System.out.print("Enter index of the 2nd item: ");
    if (scanner.hasNextLine()) {
      j = Integer.parseInt(scanner.nextLine());

      if (j < 1 || j > listSize) {
        System.out.println("Index [" + j + "] is out of bounds [1, " + listSize + "].");
        return;
      }
      --j;
    }

    if (i != j) {
      Collections.swap(items, i, j);
    } else {
      System.out.println("Source index is the same as destination index.");
    }
  }

  private void clear() {
    System.out.println("Clear list");

    items.clear();
    System.out.println("List is empty.");
  }

  private void filter() {
    System.out.println("Filter list");

    System.out.print("Enter regular expression: ");
    String regexp = "";
    if (scanner.hasNextLine()) {
      regexp = scanner.nextLine();
    }

    if (StringUtils.isNotBlank(regexp)) {
      predicate = regexp::matches;
    } else {
      predicate = item -> true;
    }
  }

  private void handleOtherCases(String input) {
    if (input.startsWith("+")) {
      addImmediately(input.substring(1));
    } else {
      System.out.println("Unknown command.");
    }
  }

  private int getPageCount() {
    // TODO Fix returning wrong value when list is filtered
    return items.size() / pageSize + 1;
  }

  private void loadFromFile() {
    System.out.println("Load from file");

    System.out.print("Enter file path: ");
    String input = "";
    if (scanner.hasNextLine()) {
      input = scanner.nextLine();
    }

    try {
      // TODO handle case when max allowed size is exceeded during import
      final Path path = Paths.get(input);
      items = Files.readAllLines(path).stream()
        .map(line -> line.replaceAll("[,\"]",""))
        .collect(toList());
    } catch (IOException e) {
      System.out.println("Could not read from specified file.");
    }
  }

  private void saveToFile() {
    System.out.println("Save to file");

    System.out.print("Enter file path: ");
    String input = "";
    if (scanner.hasNextLine()) {
      input = scanner.nextLine();
    }

    final Collector<CharSequence, ?, String> joiningToCSV = joining("\",\n\"", "\"", "\",\n");
    String output = items.stream().collect(joiningToCSV);

    try {
      Files.write(Paths.get(input), output.getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      System.out.println("Could not write to file.");
    }
  }

  public int getCurrentPageNumber() {
    return currentPageNumber;
  }

  public void setCurrentPageNumber(int currentPageNumber) {
    this.currentPageNumber = currentPageNumber;
  }

  private PrintStream printListItem(int index, String item) {
    return System.out.printf("%4d. %s\n", index, item);
  }

  private void prompt() {
    System.out.println();
    System.out.print("prefsort> ");
  }

  private void printHelp() {
    printAppVersion();
    System.out.println("This app helps to sort lists by allowing");
    System.out.println("the user to compare only 2 items at a time.");
    System.out.println();
    System.out.println("To see available commands, type in 'commands'.");
  }

  private void printCommands() {
    System.out.println("Commands:");
    System.out.println(" 1 | print    Print List");
    System.out.println(" 2 | add      Add new item");
    System.out.println(" 3 | edit     Edit existing item");
    System.out.println(" 4 | move     Move item");
    System.out.println(" 5 | delete   Delete item");
    System.out.println(" 6 | find     Find items");
    System.out.println(" 7 | sort     Sort items");
    System.out.println(" 9 | commands Print available commands");
    System.out.println(" 0 | quit     Quit");
    System.out.println();
    System.out.println("Additional commands:");
    System.out.println("   | swap     Swap two items in list");
    System.out.println("   | filter   Filter items with regexp");
    System.out.println("   | clear    Clear list");
    System.out.println("   | load     Open list from file");
    System.out.println("   | save     Save list to file");
    System.out.println("   | help     Print help");
  }

  private void printAppVersion() {
    System.out.println("Preference Sorter v" + APP_VERSION);
  }

  private void exit() {
    System.out.println("Goodbye!");
  }

  private static boolean shouldContinue(String input) {
    final List<String> exitCommands = Arrays.asList(
      "0", "exit", "quit"
    );

    return !exitCommands.contains(input.toLowerCase());
  }
}
