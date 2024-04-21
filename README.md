Steps to Run Current stage of Repository:

Install scala, sbt, and java-17
(for telemetry need lightbend credentials in lightbend.sbt)


1. Git Clone
2. Cd to Git Repo
3. create lightbend.sbt with credentials for telemetry
4. write sbt in command line
5. clean compile run <simplan.json>

# Algorithms

## Simulator

- Weight throwing for termination detection (Slight Variation)

## Mutex

### Message Passing

- Ricart Agarwal
- Ricart Agarwal Carvalho Extension
- Raymonds Algorithm
  - Echo Algorithm for Spanning Tree Building 
- Agrawal El Abbadi
  - Strongly Accurate Failure Detector   

### Shared Memory (Converted to Message Passing)

- Peterson's Algorithm (Two Process)
  - Uses a custom actor for maintianing shared memory (Petersons Shared Mem Actor)
- Peterson's Algorithm (Tournament for N Processes)
  - Uses a custom actor for maintianing shared memory (Peterson Tournament Shared Mem Actor)
- Bakery Algorithm
  - Uses a custom actor for maintianing shared memory (Bakery Shared Mem Actor)
  - TODO: if node is already requesting, then it should not be able to request again
  
### [Simulation Plan](./mutexsimplan.json)

1. Ricart Agarwal
2. Ricart Agarwal Carvalho Extension
3. Raymonds Algorithm
4. Agrawal El Abbadi
5. Peterson's Algorithm (Tournament for N Processes) 26 Processes
6. Peterson's Algorithm (Tournament for N Processes) 8 Processes
7. Peterson's Algorithm (Two Process)
8. Bakery Algorithm

## Telemetry 

### [Mutex](./mutexsimplan.json)

![Mutex Telemetry](./assets/mutexsimplanrun.png)