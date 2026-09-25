package client;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * CsvLoader reads a list of numbers from a CSV file, so the user can
 * load data instead of typing it manually into the GUI.
 *
 * Supports two common CSV layouts:
 *   1) All numbers on one line, comma-separated: 4,19,7,42,3,15
 *   2) One number per line:
 *        4
 *        19
 *        7
 * Both are handled automatically - it just splits on commas AND newlines.
 */
public class CsvLoader {

    /**
     * Reads all valid integers from the given CSV file path.
     * Skips empty lines and anything that isn't a whole number
     * (so a header row like "value" won't crash the whole load -
     * it just gets skipped, and a warning is printed).
     */
    public static List<Integer> loadNumbersFromCsv(String filePath) throws IOException {
        List<Integer> numbers = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // handle comma-separated values on a single line
                String[] parts = line.split(",");
                for (String part : parts) {
                    part = part.trim();
                    if (part.isEmpty()) continue;

                    try {
                        numbers.add(Integer.parseInt(part));
                    } catch (NumberFormatException e) {
                        // not a valid whole number (e.g. a header like "value")
                        // skip it rather than crashing the whole load
                        System.out.println("Skipping non-numeric value in CSV: \"" + part + "\"");
                    }
                }
            }
        }

        return numbers;
    }
}