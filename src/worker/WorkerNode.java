package worker;

import common.*;
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

    // ---------- Local accessors for Person 2 & 3 ----------

    public Map<Integer, WorkerService> getNeighbours() {
        return Collections.unmodifiableMap(neighbours);
    }

    public BootstrapService getBootstrap() {
        return bootstrap;
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
        Log.info(name(), "Commands: n = neighbours, w = all workers, q = quit");
        while (in.hasNextLine()) {
            switch (in.nextLine().trim().toLowerCase()) {
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