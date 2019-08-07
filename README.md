# Aion Unity Contracts

This is a reference implementation of smart contracts for Aion Unity.


## To Build


To build a specific smart contract, go to the corresponding subdirectory and run
```
mvn initialize
mvn clean install
```

## Terminology

### Staker Registry:

- `staker` - A registered node which is responsible for producing PoS blocks;
- `voter` - Any coin-holder who have voted for a staker;
- `stake` - Locked coins;
- `vote` - The operation of voting for a staker;
- `unvote` -  The operation of un-voting for a staker.

### Pool Registry:
- `pool` - A registered staker which accepts stake from and shares the block rewards with other participants;
- `delegator` - Any coin-holder who have delegated for a pool;
- `delegate` - The operation of delegating stake to a pool;
- `redelegate` - The operation of delegating stake to a pool, using block rewards;
- `undelegate`- The operation of un-delegating stake to a pool; 
- `withdraw`- The operation of claiming block rewards from a pool.

