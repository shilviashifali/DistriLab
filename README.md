\# DistriLab – CS324 Assignment 1



Distributed worker network using Java RMI, with leader election and distributed job processing.



\## Requirements

\- JDK 21

\- Apache NetBeans (Ant project)



\## Build

Open the project in NetBeans → right-click \*\*DistriLab\*\* → \*\*Clean and Build\*\*.

This creates `dist\\DistriLab.jar`.



\## Run (quick demo)

From the project folder in Command Prompt:

```

run-demo.bat

```

Enter the number of workers (3–10). The script starts the Bootstrap Node, the workers and 2 clients.



\## Run manually (start in this order, each in its own Command Prompt)

1\. Bootstrap Node:

&#x20;  `java -cp dist\\DistriLab.jar bootstrap.BootstrapNode \[port] \[host]`

2\. Each worker (3–10):

&#x20;  `java -cp dist\\DistriLab.jar worker.WorkerNode \[bootstrapHost] \[bootstrapPort] \[thisHost]`

3\. Each client:

&#x20;  `java -cp dist\\DistriLab.jar client.ClientApp`



Defaults: `localhost`, port `1099`.



\## Running across several computers

Replace `localhost` with the Bootstrap computer's IP address, and pass each computer's own IP as `thisHost`.



\## Worker console commands

\- `e` – manually trigger a leader election
\- `j` – simulate a job assignment for JAC testing
\- `n` – show neighbours
\- `w` – show all active workers
\- `q` – leave the network


## Leader Election and JAC

//Workers use a leader election mechanism to select a coordinator.

- The worker with the highest JAC (Job Assignment Count) is selected as coordinator.
- If workers have the same JAC, the worker with the higher Worker ID is selected.
- An election can be manually triggered using the `e` command.
- The `j` command simulates a job assignment for testing.
- After 5 job assignments, the worker's JAC increases by 1 and a new election is automatically triggered.
- The elected coordinator is announced to the active workers so that all workers maintain the same coordinator.

## Example

If Workers 1, 2 and 3 all have JAC 0, Worker 3 is elected because it has the highest Worker ID.

If Worker 1 then receives 5 job assignments, its JAC increases to 1. A new election is triggered and Worker 1 becomes the coordinator because it now has the highest JAC.
