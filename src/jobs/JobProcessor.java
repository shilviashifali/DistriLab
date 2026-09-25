package jobs;

import java.util.ArrayList;
import java.util.List;

/**
 * JobProcessor holds the three job algorithms the assignment requires,
 * plus helper methods to split work evenly across a number of workers.
 *
 * This class has NO RMI and NO threading yet - it's plain Java so it's
 * easy to test on its own before wiring it into the distributed system.
 */
public class JobProcessor {

    // ---------- JOB 1: MAX ----------
    // Return the largest value from an unsorted list of numbers.
    public static int max(List<Integer> numbers) {
        if (numbers == null || numbers.isEmpty()) {
            throw new IllegalArgumentException("Cannot find max of empty list");
        }
        int largest = numbers.get(0);
        for (int n : numbers) {
            if (n > largest) {
                largest = n;
            }
        }
        return largest;
    }

    // ---------- JOB 2: PRIMESUM ----------
    // Calculate the sum of all prime numbers within [start, end] inclusive.
    public static long primeSum(int start, int end) {
        long sum = 0;
        for (int i = Math.max(2, start); i <= end; i++) {
            if (isPrime(i)) {
                sum += i;
            }
        }
        return sum;
    }

    // ---------- JOB 3: PRIMECOUNT ----------
    // Count how many numbers in an unsorted list are prime.
    public static int primeCount(List<Integer> numbers) {
        int count = 0;
        for (int n : numbers) {
            if (isPrime(n)) {
                count++;
            }
        }
        return count;
    }

    // ---------- Helper: prime check ----------
    private static boolean isPrime(int n) {
        if (n < 2) return false;
        if (n == 2) return true;
        if (n % 2 == 0) return false;
        for (int i = 3; (long) i * i <= n; i += 2) {
            if (n % i == 0) return false;
        }
        return true;
    }

    // ---------- Workload splitting: for PRIMESUM ----------
    // Splits a range [start, end] into numWorkers roughly-equal sub-ranges.
    // Returns a list of int[]{subStart, subEnd} pairs, one per worker.
    public static List<int[]> divideRange(int start, int end, int numWorkers) {
        List<int[]> chunks = new ArrayList<>();
        int totalSize = end - start + 1;
        int baseSize = totalSize / numWorkers;
        int remainder = totalSize % numWorkers;

        int currentStart = start;
        for (int i = 0; i < numWorkers; i++) {
            // spread the remainder across the first few workers
            int thisChunkSize = baseSize + (i < remainder ? 1 : 0);
            if (thisChunkSize == 0) continue; // more workers than numbers
            int currentEnd = currentStart + thisChunkSize - 1;
            chunks.add(new int[]{currentStart, currentEnd});
            currentStart = currentEnd + 1;
        }
        return chunks;
    }

    // ---------- Workload splitting: for MAX and PRIMECOUNT ----------
    // Splits an unsorted list into numWorkers roughly-equal sub-lists.
    public static List<List<Integer>> divideList(List<Integer> numbers, int numWorkers) {
        List<List<Integer>> chunks = new ArrayList<>();
        int totalSize = numbers.size();
        int baseSize = totalSize / numWorkers;
        int remainder = totalSize % numWorkers;

        int index = 0;
        for (int i = 0; i < numWorkers; i++) {
            int thisChunkSize = baseSize + (i < remainder ? 1 : 0);
            if (thisChunkSize == 0) continue;
            List<Integer> chunk = new ArrayList<>(numbers.subList(index, index + thisChunkSize));
            chunks.add(chunk);
            index += thisChunkSize;
        }
        return chunks;
    }

    // ---------- Combining partial results back into one final answer ----------
    // These are what the COORDINATOR will eventually call after collecting
    // each worker's partial result, but it's useful to have them here too.
    public static int combineMax(List<Integer> partialMaxes) {
        return max(partialMaxes);
    }

    public static long combinePrimeSum(List<Long> partialSums) {
        long total = 0;
        for (long s : partialSums) total += s;
        return total;
    }

    public static int combinePrimeCount(List<Integer> partialCounts) {
        int total = 0;
        for (int c : partialCounts) total += c;
        return total;
    }
}