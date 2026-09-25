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
    int getJac() throws RemoteException;
    int getCoordinatorId() throws RemoteException;
    void receiveElection(String electionId, int candidateId, int candidateJac) throws RemoteException;
    void receiveCoordinator(String electionId, int coordinatorId) throws RemoteException;
    void startElection() throws RemoteException;
    
    // Person 3: job execution methods go here
    int runMaxChunk(java.util.List<Integer> numbers) throws RemoteException;
    long runPrimeSumChunk(int start, int end) throws RemoteException;
    int runPrimeCountChunk(java.util.List<Integer> numbers) throws RemoteException;
    JobResult submitJob(JobRequest request) throws RemoteException;
}