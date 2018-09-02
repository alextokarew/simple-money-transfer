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
