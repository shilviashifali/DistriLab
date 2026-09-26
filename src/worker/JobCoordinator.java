package worker;

import common.Job;
import common.JobResult;
import common.Log;
import common.WorkerService;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Coordinator-side job handling: splits a job across all active workers,
 * sends the parts in parallel, and combines the partial results. Works
 * for every job type through the polymorphic Job interface.
 */
public class JobCoordinator {

    private final WorkerNode node;
    private final ExecutorService dispatchPool = Executors.newFixedThreadPool(10);

    public JobCoordinator(WorkerNode node) {
        this.node = node;
    }

    public JobResult submit(Job<?> job) throws RemoteException {
        return process(job);
    }

    private <R extends Serializable> JobResult process(Job<R> job) throws RemoteException {
        if (node.getId() != node.getCoordinatorId()) {
            throw new RemoteException("Worker-" + node.getId() + " is not the coordinator "
                    + "(current coordinator: Worker-" + node.getCoordinatorId() + ")");
        }

        node.recordJobAssignment(); // counts toward this term's 5-job limit
        Log.info(node.displayName(), "Received job: " + job.describe());

        try {
            Map<Integer, WorkerService> activeWorkers = node.getBootstrap().getActiveWorkers();
            List<WorkerService> workerList = new ArrayList<>(activeWorkers.values());
            int n = workerList.size();
            if (n == 0) {
                throw new RemoteException("No active workers available to process job");
            }

            List<Job<R>> parts = job.split(n);
            List<Future<R>> futures = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) {
                WorkerService w = workerList.get(i);
                Job<R> part = parts.get(i);
                futures.add(dispatchPool.submit(() -> w.executeTask(part)));
            }

            List<R> partialResults = new ArrayList<>();
            for (Future<R> f : futures) {
                partialResults.add(f.get());
            }
            R result = job.combine(partialResults);
            Log.info(node.displayName(), "Completed " + job.describe() + " = " + result);
            return new JobResult(job.getName(), result, parts.size(), node.getId());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteException("Job interrupted: " + job.describe(), e);
        } catch (ExecutionException e) {
            throw new RemoteException("Job failed: " + e.getCause().getMessage(), e.getCause());
        }
    }

    public void shutdown() {
        dispatchPool.shutdownNow();
    }
}