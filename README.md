Simple money transfer service

Running an application

Start an application running at default port (8765)
sbt run

Start an application running at specified port
sbt "run {port}"

for example

sbt "run 8080"

will run an application listening at 8080 port

We assume that account balance and transfer amounts are nominated in the smallest currency units (for USD it is cent, for EUR it is eurocent etc).
Therefore we use integer types to avoid rounding errors.

We use sequential queue processing algorithm for the sake of simplicity, but we can also develop some sharding mechanism to parallelize transfer processing and thus improving the overall throughput.

Working with accounts.

- Create an account:
curl -i -XPOST -H 'Content-Type: application/json' -d '{"id": "123-456-7890", "description": "Sample account", "initialBalance": 1000000, "maxLimit": 999999999}'  http://localhost:8765/account

- retrieve account details
curl -i -XGET http://localhost:8765/account/123-456-7890

- retrieve account balance
curl -i -XGET http://localhost:8765/account/123-456-7890/balance


TODO:
- futures in storage