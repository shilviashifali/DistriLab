package common;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Set;

/** Remote interface of a worker node. */
public interface WorkerService extends Remote {

    // ---------- Network ----------
    int getId() throws RemoteException;
    boolean ping() throws RemoteException;
    void addNeighbour(int neighbourId, WorkerService neighbour) throws RemoteException;
    void removeNeighbour(int neighbourId) throws RemoteException;
    Set<Integer> getNeighbourIds() throws RemoteException;

    // ---------- Leader election ----------
    int getJac() throws RemoteException;
    int getCoordinatorId() throws RemoteException;
    ElectionCandidate receiveElection(String electionId, int senderId) throws RemoteException;
    void receiveCoordinator(String electionId, int coordinatorId) throws RemoteException;
    void startElection() throws RemoteException;

    // ---------- Distributed jobs ----------
    <R extends Serializable> R executeTask(Job<R> task) throws RemoteException;
    JobResult submitJob(Job<?> job) throws RemoteException;
}