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
- `stake` - Locked coins;
- `bond` - The operation of casting coins as stake;
- `unbond` -  The operation of withdrawing bonded coins.
- `transfer` -  The operation of transferring stake to another staker.

### Pool Registry:
- `pool` - A registered staker which accepts stake from and shares the block rewards with other participants.
- `delegator` - Any coin-holder who have delegated for a pool.
- `delegate` - The operation of delegating stake to a pool.
- `redelegate` - The operation of delegating stake to a pool, using block rewards.
- `undelegate`- The operation of un-delegating stake from a pool; 
- `withdraw`- The operation of claiming block rewards from a pool.
- `transfer` - The operation of transferring stake from one pool to another.

