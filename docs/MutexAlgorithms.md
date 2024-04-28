# Mutex Algorithms Documentation

This document provides a detailed explanation of the mutex algorithms implemented in this project.

## Overview

The mutex module in this project implements various mutual exclusion algorithms. These algorithms are used to prevent concurrent processes from accessing shared resources simultaneously, thus avoiding potential conflicts or inconsistencies.
All the algorithms implemented from the mutual exclusion chapter of the textbook ["Distributed Algorithms, Second Edition An Intuitive Approach, W Fokik"](https://mitpress.mit.edu/9780262037662/distributed-algorithms/) which provides an intuitive approach to learning them by summarising and elaborating on individual paper.



## Algorithms Implemented

The following algorithms are implemented in this project:
1. Ricart Agarwal
2. Ricart Agarwal Carvalho Extension
3. Raymonds Algorithm
4. Agrawal El Abbadi
5. Peterson's Algorithm (Tournament for N Processes)
6. Peterson's Algorithm (Two Process)
7. Bakery Algorithm
8. Test and Set Lock Algorithm
9. Test and Test and Set Lock Algorithm

### [Simulation Plan](../mutexsimplan.json)

1. Ricart Agarwal
2. Ricart Agarwal Carvalho Extension
3. Raymonds Algorithm
4. Agrawal El Abbadi
5. Peterson's Algorithm (Tournament for N Processes) 26 Processes
6. Peterson's Algorithm (Tournament for N Processes) 8 Processes
7. Peterson's Algorithm (Two Process)
8. Bakery Algorithm
9. Test and Set Lock Algorithm
10. Test and Test and Set Lock Algorithm

## Algorithm Details

### [Ricart Agarwal](../src/main/scala/com/distcomp/mutex/RicartaAgarwal.scala)

Request all processes in the system for permission to enter the critical section. The process can enter the critical section only when it receives permission from all other processes. The process sends a request message to all other processes and waits for a reply from all of them. The process can enter the critical section only when it receives a reply from all other processes.

#### Implementation Details


1. **Timestamp-based Ordering:** Each actor sends a `RequestCS` with its timestamp to all other actors and must receive a `ReplyCS` from every actor before entering the critical section, ensuring requests are ordered by timestamp and node ID.

2. **State Management:** Uses mutable state (`pendingReplies`) to track which actors have not yet replied to the critical section request, updating this state upon receiving each `ReplyCS`.

3. **Message Handling:** Actors handle different types of messages (`RequestCS`, `ReplyCS`, `EnterCriticalSection`, `ExitCriticalSection`, and `ReleaseCS`) to manage entry, operation within, and exit from the critical section, adhering to the mutual exclusion principle.

4. **Concurrency Control:** Prioritizes critical section access based on timestamp and node ID comparisons. This is implemented in the condition checking within the `RequestCS` message handling, deciding whether to immediately reply or defer the reply until after exiting the critical section.

5. **State Transitions and Timestamp Updates:** Implements state transitions upon entering and exiting the critical section and updates the logical clock upon receiving messages with higher timestamps to maintain consistency.


### [Ricart Agarwal Carvalho Extension](../src/main/scala/com/distcomp/mutex/RicartaAgarwalCarvalhoRoucairol.scala)

An extension of the Ricart Agarwal algorithm that allows a process to enter the critical section without waiting for permission from all other processes. The process can enter the critical section when it receives permission from processes which have requested critical section after it last exited critical section.

#### Implementation Details

1. **Roucairol and Carvalho Optimization:** Utilizes a `lastGranted` set to remember which nodes last granted access to the critical section, allowing the node to request access only from these nodes in subsequent attempts, significantly reducing the number of messages needed.

2. **Selective Request Broadcasting:** When requesting critical section access, the node either broadcasts to all nodes (if not previously in the critical section) or only to nodes in `lastGranted` (if re-entering), optimizing network use.

3. **Dynamic Set Management:** Actively manages the `lastGranted` set by adding nodes that grant access and clearing it upon exiting the critical section, ensuring that only relevant nodes are contacted in future requests.

4. **Critical Section Re-entry:** Employs conditional logic to check if re-entry is possible without waiting for new permissions if no changes in `lastGranted` have occurred, speeding up the process of re-entering the critical section.


### Raymonds Algorithm

Raymond's algorithm forms a logical tree structure among processes to control access to the critical section. Each process in the tree has a token, and only the holder of the token can enter the critical section. A process needing to enter the critical section must request the token from its parent in the tree, and this request may be propagated up the tree until it reaches the token holder. The token is then passed down the tree to the requesting process.

#### Implementation Details

1. **Token Management:** Uses a token-based approach for critical section entry. Nodes request and pass a token along a logical tree, where possession of the token grants access to the critical section.

2. **Queue System:** Implements a queue to manage requests for the token. Nodes add themselves to the queue when they need the token, and the token is passed to the first node in the queue when available.

3. **Critical Section Entry and Exit:** Nodes enter the critical section when they have the token and exit by passing the token to the next node in the queue, if any.

4. **Dynamic Parent-Child Reassignment:** On exiting the critical section, a node can reassign the 'parent' role to the next node in the queue, reflecting changes in the tree's structure necessary for efficient token passing.

### Agrawal El Abbadi

This algorithm uses a timestamping mechanism to decide the order of critical section access among competing processes. Each process maintains a local clock. When a process desires to enter the critical section, it broadcasts its timestamp to all other processes and waits for their acknowledgment. The process can enter the critical section when it has received all acknowledgments and has the smallest timestamp among all pending requests.

#### Implementation Details 

1. **Heartbeat Mechanism:** Incorporates a heartbeat system to monitor node availability, using scheduled heartbeats sent to a failure detector. This ensures that the system can adapt to node failures by dynamically updating permissions and quorum configurations.

2. **Dynamic Quorum and Permission Management:** Manages a quorum queue to control access to the critical section, only granting permission to the head of the queue and dynamically adjusting the queue based on node failures and permissions.

3. **Critical Section Protocols:** Implements protocols for entering and exiting the critical section, including granting and revoking permissions based on quorum status, and signaling completion to a simulator.

4. **Failure Handling:** Reacts to node failures by adjusting the permission given state and recalibrating the quorum queue, ensuring the system remains operational and consistent even in the presence of failures.

### Peterson's Algorithm (Tournament for N Processes)

This version of Peterson's algorithm extends the basic two-process algorithm to N processes organized in a binary tree. Each node of the tree represents a competition between two processes, and the winner at each node moves up to compete at the next level. This continues until one process emerges as the winner at the root of the tree, gaining access to the critical section.

#### Implementation Details

1. **Tournament Tree Construction:** Builds a binary tournament tree for managing mutual exclusion among multiple processes. Each process is assigned an internal node ID and a bit position, creating a structured pathway to contest for entering the critical section.

2. **Flag and Turn Setting:** Processes communicate with a shared memory actor to set flags and turns that dictate the ability to proceed or wait in the contest for the critical section. These flags are set recursively up the tournament tree until the root is reached or the contest is resolved.

3. **Critical Section Entry:** A process enters the critical section only after successfully navigating the tournament tree, ensuring that no other process is contesting its entry at a higher priority level. Entry is managed through message passing that checks and sets conditions at each level of the tree.

4. **Message Queue Management:** Utilizes a message queue to store actions that need to be executed after exiting the critical section, ensuring that flags are reset correctly and the state is consistent for future requests.

5. **Recursive Contest Resolution:** Implements recursive checks and updates up the tournament tree. If a process cannot proceed, it waits and retries, effectively spinning on a condition. Once able to proceed, it moves up the tree until it can either enter the critical section or needs to retry at a higher level.

### Peterson's Algorithm (Two Process)

This simpler variant of Peterson's algorithm is designed for two competing processes. Each process signals its intent to enter the critical section and sets a preference variable indicating the other process's turn to enter. A process can enter the critical section when it sees either the other process is not interested or it is its turn according to the preference variable.

#### Implementation Details

1. **Initialization and Logging:** Starts by logging the initialization of the Peterson two-process algorithm, ensuring the node is set up correctly for mutual exclusion operations.

2. **Shared Memory Interaction:** Processes interact with a shared memory actor to set and check flags and turns, crucial for determining the right to enter the critical section based on the Peterson algorithm rules.

3. **Critical Section Entry Logic:** Implements a check-and-wait mechanism where the process sets its flag, sets the turn to the other process, and repeatedly checks both the flag and turn conditions to decide when it can safely enter the critical section.

4. **Critical Section Management:** Once conditions are met, the process enters the critical section, performs operations, and then exits. It resets its flag in shared memory to allow the other process to enter the critical section.

5. **Exit and Cleanup:** Signals completion of its critical section execution to a simulator actor and ensures the flag is reset in shared memory, maintaining system consistency for subsequent operations.

### Bakery Algorithm

The Bakery algorithm is based on the analogy of taking a number at a bakery. Each process takes a ticket number when it wants to enter the critical section. Processes enter the critical section in the order of their ticket numbers, ensuring fairness. A process with a lower ticket number will always enter the critical section before those with higher numbers.

#### Implementation Details

1. **Algorithm Initialization:** Logs the initiation of the Bakery algorithm, which simulates the classic bakery ticket system for managing access to a critical section among multiple processes.

2. **Flag and Number Setting:** Processes interact with a shared memory actor to set 'choosing' flags when attempting to enter the critical section, then calculate and set their 'number' based on the highest current number observed plus one.

3. **Critical Section Entry Check:** Each process repeatedly checks two conditions before entering the critical section: that no other process is in the 'choosing' state, and that no process has a smaller 'number' (or if the same, has a lower ID).

4. **Entry and Exit from Critical Section:** Once conditions are met, the process enters the critical section, performs its operations, and upon exiting, resets its number to zero and notifies the simulator of the completion.

5. **Handling Delays and Shared Memory Checks:** Implements random delays to simulate network latency and ensures operations on shared memory are synchronized and consistent across all participating processes.

### Test and Set Lock Algorithm

This algorithm uses a single shared boolean variable. A process attempting to enter the critical section uses an atomic "test and set" operation on the variable. If the variable is false (unlocked), it is set to true (locked), and the process enters the critical section. If the variable is true, the process must wait and repeatedly test until it becomes false.

#### Implementation Details

1. **Initialization and Setup:** Logs the start of the Test and Set mutex mechanism, preparing the node for operation within the mutual exclusion environment.

2. **Lock Acquisition Attempt:** Each process sends a `SetLockRequest` to the shared memory actor to attempt acquiring the lock. If unsuccessful (i.e., the lock is already held), it periodically retries until the lock becomes available.

3. **Entering and Exiting Critical Section:** Once the lock is successfully acquired, the process enters the critical section, performs its task, and upon completion, signals an `ExitCriticalSection` to proceed with lock release.

4. **Lock Release and Cleanup:** After exiting the critical section, the process sends an `UnlockRequest` to the shared memory actor to release the lock and notifies the simulator of the completion of its critical section execution.

### Test and Test and Set Lock Algorithm

An optimization of the Test and Set Lock Algorithm, this method reduces the bus traffic in multiprocessor systems. A process first tests the lock status without altering it. If the lock is free, the process then uses the "test and set" operation to attempt to acquire the lock. This reduces the amount of expensive atomic operations required, potentially lowering system overhead.

#### Implementation Details

1. **Initialization and Setup:** Logs the startup process for the Test and Test and Set mutex mechanism, preparing the node for operation within the mutual exclusion environment.

2. **Initial Lock Check:** Each process first sends a `ReadLockRequest` to shared memory to check the lock status. This step avoids unnecessary locking attempts if the lock is already held by another process.

3. **Lock Attempt and Optimization:** If the initial lock check returns false (lock is free), the process then sends a `SetLockRequest`. This double-checking minimizes the lock's contention and improves performance by reducing the number of lock acquisition attempts.

4. **Critical Section Entry:** Upon successful lock acquisition (confirmed by a `SetLockResponse` with false), the process enters the critical section, performs necessary operations, and then signals `ExitCriticalSection`.

5. **Lock Release and Algorithm Completion:** After completing the critical section operations, the process releases the lock by sending an `UnlockRequest` to shared memory and notifies the simulator of the operation's completion.

6. **Robust Error Handling:** Ensures continuous operation despite potential null references to shared memory, crucial for maintaining stability in distributed systems.