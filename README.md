# Simple money transfer service

This is an implementation of a simple money transfer service between two random accounts. It uses an in-memory storage
backed by java.util.concurrent.ConcurrentHashMap. The processing algorithm is built around Master-Worker pattern. When a
transfer request arrives to the service it is validated and then is sent to the Master. The Master decides which Worker
will actually process the request and then pushes the transfer to this Worker, or puts it to the waiting buffer if no
Worker can handle this transfer now.

We assume that the number of workers is significantly less than the number of accounts in the system. So when the transfer is
pushed to the worker we bind source and destination accounts to this worker. All subsequent transfers for these accounts
are put into the waiting buffer, therefore we ensure that no concurrent transfers are processed for any account. When the
transfer is completed we release the account-to-worker binding and try to process transfers that are in the waiting buffer.   

In is also assumed that account balance and transfer amounts are nominated in the smallest currency units (for USD it is cent, 
for EUR it is eurocent etc). Therefore we use integer types to avoid rounding errors.

## Running an application

- Start an application running at default port (8765)
```
sbt run
```

- Start an application running at specified port
```
sbt "run {port}"
```

for example
```
sbt "run 8080"
```
will run an application listening at 8080 port

## Testing an application

- Run unit tests
```
sbt test
```

- Run integration tests
```
sbt it:test
```

An integration test tries to process 1M transfers concurrently, hence it might take up to several minutes to run it


## Working with accounts.

To create a money transfer operation we need to create some accounts first:

- Create an account:
```
curl -i -XPOST -H 'Content-Type: application/json' \
-d '{"id": "123-456-7890", "description": "Sample account", "initialBalance": 1000000, "maxLimit": 999999999}'  \
http://localhost:8765/account
```

- retrieve account details
```
curl -i -XGET http://localhost:8765/account/123-456-7890
```

- retrieve account balance
```
curl -i -XGET http://localhost:8765/account/123-456-7890/balance
```

## Working with transfers

- Create a transfer
```
curl -i -XPOST \
-H 'Content-Type: application/json' \
-d '{"from": "123-456-7890", "to": "987-654-3210", "amount": 50000, "comment": "bribe", "token": "123-4567890"}' \
http://localhost:8765/transfer
```

- retrieve transfer status by id
```
curl -i -XGET http://localhost:8765/transfer/10
```

## A sample scenario for demonstrating all main features
```bash
#create two accounts
curl -i -XPOST -H 'Content-Type: application/json' -d '{"id": "123-456-7890", "description": "Sample account", "initialBalance": 100000, "maxLimit": 999999999}'  http://localhost:8765/account
curl -i -XPOST -H 'Content-Type: application/json' -d '{"id": "987-654-3210", "description": "Another account", "initialBalance": 100000, "maxLimit": 999999999}'  http://localhost:8765/account

#checking that accounts were created and their initial balances
curl -i -XGET http://localhost:8765/account/123-456-7890
curl -i -XGET http://localhost:8765/account/123-456-7890/balance

curl -i -XGET http://localhost:8765/account/987-654-3210
curl -i -XGET http://localhost:8765/account/987-654-3210/balance

#creating valid transfer
curl -i -XPOST -H 'Content-Type: application/json' -d '{"from": "123-456-7890", "to": "987-654-3210", "amount": 50000, "comment": "bribe", "token": "123-4567891"}' http://localhost:8765/transfer

#checking the transfer status and that the balances were changed
curl -i -XGET http://localhost:8765/transfer/1
curl -i -XGET http://localhost:8765/account/123-456-7890/balance
curl -i -XGET http://localhost:8765/account/987-654-3210/balance


#creating a transfer with amount that exceeds source account balance
curl -i -XPOST -H 'Content-Type: application/json' -d '{"from": "123-456-7890", "to": "987-654-3210", "amount": 500000, "comment": "bribe", "token": "123-4567892"}' http://localhost:8765/transfer

#checking the transfer status and that the balances were not changed
curl -i -XGET http://localhost:8765/transfer/2
curl -i -XGET http://localhost:8765/account/123-456-7890/balance
curl -i -XGET http://localhost:8765/account/987-654-3210/balance

```