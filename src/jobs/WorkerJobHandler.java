package jobs;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * WorkerJobHandler is what a Worker uses to actually run jobs.
 *
 * Instead of running each job directly (which would block the worker
 * until it finishes), every job is submitted to a thread pool. This
 * means a worker can be handed several jobs in a row and work on all
 * of them at the same time, instead of queueing them up one by one.
 *
 * How this fits with RMI later: when Person 1/2's Worker RMI method
 * (e.g. runJob(...)) is called, it should call one of the submit___
 * methods below instead of calling JobProcessor directly. That keeps
 * the RMI call itself fast (it just hands off the work) while the
 * actual computation happens on a separate thread.
 */
public class WorkerJobHandler {

    // A fixed-size thread pool. 4 threads means this worker can crunch
    // on up to 4 jobs at once. Adjust the number if needed later.
    private final ExecutorService threadPool = Executors.newFixedThreadPool(4);

    // ---- Submit a MAX job on a sub-list, runs on its own thread ----
    public Future<Integer> submitMaxJob(List<Integer> chunk) {
        Callable<Integer> task = () -> {
            System.out.println(Thread.currentThread().getName()
                    + " starting MAX on chunk of size " + chunk.size());
            int result = JobProcessor.max(chunk);
            System.out.println(Thread.currentThread().getName()
                    + " finished MAX -> " + result);
            return result;
        };
        return threadPool.submit(task);
    }

    // ---- Submit a PRIMESUM job on a sub-range, runs on its own thread ----
    public Future<Long> submitPrimeSumJob(int start, int end) {
        Callable<Long> task = () -> {
            System.out.println(Thread.currentThread().getName()
                    + " starting PRIMESUM on [" + start + "," + end + "]");
            long result = JobProcessor.primeSum(start, end);
            System.out.println(Thread.currentThread().getName()
                    + " finished PRIMESUM -> " + result);
            return result;
        };
        return threadPool.submit(task);
    }

    // ---- Submit a PRIMECOUNT job on a sub-list, runs on its own thread ----
    public Future<Integer> submitPrimeCountJob(List<Integer> chunk) {
        Callable<Integer> task = () -> {
            System.out.println(Thread.currentThread().getName()
                    + " starting PRIMECOUNT on chunk of size " + chunk.size());
            int result = JobProcessor.primeCount(chunk);
            System.out.println(Thread.currentThread().getName()
                    + " finished PRIMECOUNT -> " + result);
            return result;
        };
        return threadPool.submit(task);
    }

    // Call this when the worker shuts down, to stop the thread pool cleanly.
    public void shutdown() {
        threadPool.shutdown();
    }
}