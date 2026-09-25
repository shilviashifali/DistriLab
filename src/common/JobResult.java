package common;

import java.io.Serializable;

/**
 * The combined result of a job, sent back from the Coordinator to the Client.
 * Only one of the value fields will be set, depending on jobType.
 */
public class JobResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final JobRequest.JobType jobType;
    private final int intValue;   // used for MAX and PRIMECOUNT
    private final long longValue; // used for PRIMESUM

    public JobResult(JobRequest.JobType jobType, int intValue) {
        this.jobType = jobType;
        this.intValue = intValue;
        this.longValue = 0;
    }

    public JobResult(JobRequest.JobType jobType, long longValue) {
        this.jobType = jobType;
        this.intValue = 0;
        this.longValue = longValue;
    }

    public JobRequest.JobType getJobType() { return jobType; }
    public int getIntValue() { return intValue; }
    public long getLongValue() { return longValue; }

    @Override
    public String toString() {
        if (jobType == JobRequest.JobType.PRIMESUM) {
            return "PRIMESUM result: " + longValue;
        }
        return jobType + " result: " + intValue;
    }
}