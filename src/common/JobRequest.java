package common;

import java.io.Serializable;
import java.util.List;

/**
 * Represents a job submitted by a Client, sent over RMI to the Coordinator.
 * Serializable so it can travel across the network as an RMI argument.
 */
public class JobRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum JobType { MAX, PRIMESUM, PRIMECOUNT }

    private final JobType type;
    private final List<Integer> numbers; // used for MAX and PRIMECOUNT
    private final int rangeStart;        // used for PRIMESUM
    private final int rangeEnd;          // used for PRIMESUM

    // Constructor for MAX / PRIMECOUNT (list-based jobs)
    public JobRequest(JobType type, List<Integer> numbers) {
        this.type = type;
        this.numbers = numbers;
        this.rangeStart = 0;
        this.rangeEnd = 0;
    }

    // Constructor for PRIMESUM (range-based job)
    public JobRequest(JobType type, int rangeStart, int rangeEnd) {
        this.type = type;
        this.numbers = null;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
    }

    public JobType getType() { return type; }
    public List<Integer> getNumbers() { return numbers; }
    public int getRangeStart() { return rangeStart; }
    public int getRangeEnd() { return rangeEnd; }
}