package common;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Set;

public interface WorkerService extends Remote {
    int getId() throws RemoteException;
    boolean ping() throws RemoteException;
    void addNeighbour(int neighbourId, WorkerService neighbour) throws RemoteException;
    void removeNeighbour(int neighbourId) throws RemoteException;
    Set<Integer> getNeighbourIds() throws RemoteException;

    // Person 2: election / coordinator methods go here
    // Person 3: job execution methods go here
}