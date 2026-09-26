package worker;

import common.*;
import jobs.TaskExecutor;
import java.io.Serializable;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;


public class WorkerNode extends UnicastRemoteObject implements WorkerService {
    private static final long serialVersionUID = 1L;

    private final String leaderman = "cs324"; // required by spec (used in election)

    private final BootstrapService bootstrap;
    private final Map<Integer, WorkerService> neighbours = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeat =
            Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean left = new AtomicBoolean(false);
    private volatile int id = -1;
    private volatile int jac = 0;
    private volatile int coordinatorId = -1;
    private volatile int jobsAssigned = 0;

    private final Set<String> processedElections = ConcurrentHashMap.newKeySet();
    private final Set<String> processedCoordinators = ConcurrentHashMap.newKeySet();

    // ---------- Person 3: job execution support ----------
    private final TaskExecutor taskExecutor = new TaskExecutor(4);
    private final JobCoordinator jobCoordinator = new JobCoordinator(this);


    public WorkerNode(BootstrapService bootstrap) throws RemoteException {
        super(0);
        this.bootstrap = bootstrap;
    }

    // ---------- Remote methods ----------

    @Override
    public int getId() {
        return id;
    }
    @Override 
    public int getJac() {
        return jac;
    }
    @Override 
    public int getCoordinatorId() {
        return coordinatorId;
    }
    @Override
    public void receiveElection(String electionId, int candidateId, int candidateJac)
        throws RemoteException {
    if (!processedElections.add(electionId)) {
        this.coordinatorId = candidateId;
        Log.info(name(), "Coordinator elected: Worker-" + candidateId);
        processedCoordinators.add(electionId);
        for (WorkerService neighbour : neighbours.values()) {
            neighbour.receiveCoordinator(electionId, candidateId);
        }
        return;
    }

    int bestId = candidateId;
    int bestJac = candidateJac;

    if (jac > candidateJac || (jac == candidateJac && id > candidateId)) {
        bestId = id;
        bestJac = jac;
    }
    for (WorkerService neighbour : neighbours.values()) {
        neighbour.receiveElection(electionId, bestId, bestJac);
    }
    }

   

  @Override
   public void receiveCoordinator(String electionId, int coordinatorId)
        throws RemoteException {

    if (processedCoordinators.add(electionId)) {
        this.coordinatorId = coordinatorId;

        Log.info(name(),
                "Coordinator elected: Worker-" + coordinatorId);

        for (WorkerService neighbour : neighbours.values()) {
            neighbour.receiveCoordinator(electionId, coordinatorId);
    }
    }
    }
   @Override
    public void startElection() throws RemoteException {
    Log.info(name(), "Election started");

    String electionId = UUID.randomUUID().toString();

    Map<Integer, WorkerService> activeWorkers = bootstrap.getActiveWorkers();

    int bestId = id;
    int bestJac = jac;

    for (Map.Entry<Integer, WorkerService> entry : activeWorkers.entrySet()) {
        WorkerService worker = entry.getValue();

        try {
            int workerId = worker.getId();
            int workerJac = worker.getJac();

            if (workerJac > bestJac ||
                    (workerJac == bestJac && workerId > bestId)) {

                bestJac = workerJac;
                bestId = workerId;
            }
        } catch (RemoteException ex) {
            Log.error(name(),
                    "Could not contact Worker-" + entry.getKey());
        }
    }

    this.coordinatorId = bestId;

    Log.info(name(), "Coordinator elected: Worker-" + bestId);

    processedCoordinators.add(electionId);

    for (WorkerService worker : activeWorkers.values()) {
        try {
            worker.receiveCoordinator(electionId, bestId);
        } catch (RemoteException ex) {
            Log.error(name(), "Could not notify worker of coordinator.");
    }
    }
    }
    public void recordJobAssignment() {
    jobsAssigned++;
    Log.info(name(), "Job assigned. Count: " + jobsAssigned);

    if (jobsAssigned >= 5) {
        jac++;
        jobsAssigned = 0;

        Log.info(name(), "JAC increased to: " + jac);

        try {
            startElection();
        } catch (RemoteException e) {
            Log.error(name(), "Could not start election: " + e.getMessage());
        }
    }
}
    @Override
    public boolean ping() {
        return true;
    }

    @Override
    public void addNeighbour(int neighbourId, WorkerService neighbour) {
        neighbours.put(neighbourId, neighbour);
        Log.info(name(), "Worker-" + neighbourId + " connected. Neighbours: " + neighbours.keySet());
    }

    @Override
    public void removeNeighbour(int neighbourId) {
        if (neighbours.remove(neighbourId) != null) {
            Log.info(name(), "Worker-" + neighbourId + " disconnected. Neighbours: " + neighbours.keySet());
        }
    }

    @Override
    public Set<Integer> getNeighbourIds() {
        return new HashSet<>(neighbours.keySet());
    }

    // ---------- Person 3: job execution methods ----------

    @Override
    public <R extends Serializable> R executeTask(Job<R> job) throws RemoteException {
        return taskExecutor.run(job);
    }

    @Override
    public JobResult submitJob(Job<?> job) throws RemoteException {
        return jobCoordinator.submit(job);
    }

    // ---------- Local accessors for Person 2 & 3 ----------

    public Map<Integer, WorkerService> getNeighbours() {
        return Collections.unmodifiableMap(neighbours);
    }

    public BootstrapService getBootstrap() {
        return bootstrap;
    }

    String displayName() {
        return name();
    }

    // ---------- Lifecycle ----------

    public void join() throws RemoteException {
        id = bootstrap.register(this);
        Log.info(name(), "Registered with bootstrap");
        if (!connectToRandomWorker()) {
            Log.info(name(), "First worker in the network");
        }
        heartbeat.scheduleAtFixedRate(this::checkNeighbours,
                Config.HEARTBEAT_SECONDS, Config.HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    private boolean connectToRandomWorker() {
        try {
            WorkerInfo peer = bootstrap.getRandomActiveWorker(id);
            if (peer == null) {
                return false;
            }
            peer.getStub().addNeighbour(id, this);
            neighbours.put(peer.getId(), peer.getStub());
            Log.info(name(), "Connected to Worker-" + peer.getId());
            return true;
        } catch (RemoteException e) {
            Log.error(name(), "Could not connect to peer: " + e.getMessage());
            return false;
        }
    }

    private void checkNeighbours() {
        try {
            boolean lostNeighbour = false;
            for (Map.Entry<Integer, WorkerService> e : neighbours.entrySet()) {
                try {
                    e.getValue().ping();
                } catch (RemoteException ex) {
                    neighbours.remove(e.getKey());
                    lostNeighbour = true;
                    Log.info(name(), "Neighbour Worker-" + e.getKey() + " unreachable, removed");
                }
            }
            if (lostNeighbour || neighbours.isEmpty()) {
                connectToRandomWorker(); // repair the network
            }
        } catch (Exception e) {
            Log.error(name(), "Heartbeat failed: " + e.getMessage());
        }
    }

    public void leave() {
        if (!left.compareAndSet(false, true)) {
            return;
        }
        heartbeat.shutdownNow();
        taskExecutor.shutdown();
        jobCoordinator.shutdown();
        for (WorkerService n : neighbours.values()) {
            try {
                n.removeNeighbour(id);
            } catch (RemoteException ignored) {
                // neighbour already gone
            }
        }
        try {
            bootstrap.unregister(id);
        } catch (RemoteException e) {
            Log.error(name(), "Could not unregister: " + e.getMessage());
        }
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (NoSuchObjectException ignored) {
            // already unexported
        }
        Log.info(name(), "Left the network");
    }

    private void consoleLoop() {
        Scanner in = new Scanner(System.in);
        Log.info(name(), "Commands: e = election, j= jobs assigned, n = neighbours, w = all workers, q = quit");
        while (in.hasNextLine()) {
            switch (in.nextLine().trim().toLowerCase()) {
                case "j":
                   recordJobAssignment();
                 break;
                case "e":
                     try {
                        startElection();
                    } catch (RemoteException ex) {
                         Log.error(name(), "Election failed: " + ex.getMessage());
                    }
               
                    break;              
              
                case "n":
                    Log.info(name(), "Neighbours: " + neighbours.keySet());
                    break;
                case "w":
                    try {
                        Log.info(name(), "Active workers: " + bootstrap.getActiveWorkers().keySet());
                    } catch (RemoteException e) {
                        Log.error(name(), "Bootstrap unreachable: " + e.getMessage());
                    }
                    break;
                case "q":
                    leave();
                    System.exit(0);
                    break;
                default:
                    break;
            }
        }
    }

    private String name() {
        return "Worker-" + id;
    }

    public static void main(String[] args) {
        String bsHost = args.length > 0 ? args[0] : Config.DEFAULT_BOOTSTRAP_HOST;
        String myHost = args.length > 2 ? args[2] : "localhost";
        int bsPort;
        try {
            bsPort = args.length > 1 ? Integer.parseInt(args[1]) : Config.DEFAULT_BOOTSTRAP_PORT;
        } catch (NumberFormatException e) {
            Log.error("Worker", "Invalid port: " + args[1]);
            return;
        }

        System.setProperty("java.rmi.server.hostname", myHost);
        System.setProperty("sun.rmi.transport.tcp.responseTimeout", Config.RMI_TIMEOUT_MS);

        try {
            Registry registry = LocateRegistry.getRegistry(bsHost, bsPort);
            BootstrapService bootstrap = (BootstrapService) registry.lookup(Config.BOOTSTRAP_NAME);
            WorkerNode worker = new WorkerNode(bootstrap);
            worker.join();
            Runtime.getRuntime().addShutdownHook(new Thread(worker::leave));
            worker.consoleLoop();
        } catch (RemoteException | NotBoundException e) {
            Log.error("Worker", "Cannot reach bootstrap at " + bsHost + ":" + bsPort
                    + " - " + e.getMessage());
        }
    }
}