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

\- `n` – show neighbours

\- `w` – show all active workers

\- `q` – leave the network

