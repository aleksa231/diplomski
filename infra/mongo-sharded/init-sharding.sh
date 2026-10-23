#!/bin/bash
set -e

echo "Initializing config server replica set..."
mongosh --host mongo-cfg1:27017 <<EOF
try {
  rs.status();
} catch (e) {
  rs.initiate({
    _id: "cfgrs",
    configsvr: true,
    members: [
      { _id: 0, host: "mongo-cfg1:27017" },
      { _id: 1, host: "mongo-cfg2:27017" }
    ]
  });
}
EOF

echo "Initializing shard1 replica set..."
mongosh --host mongo-shard1a:27017 <<EOF
try {
  rs.status();
} catch (e) {
  rs.initiate({
    _id: "shard1rs",
    members: [
      { _id: 0, host: "mongo-shard1a:27017" },
      { _id: 1, host: "mongo-shard1b:27017" }
    ]
  });
}
EOF

echo "Initializing shard2 replica set..."
mongosh --host mongo-shard2a:27017 <<EOF
try {
  rs.status();
} catch (e) {
  rs.initiate({
    _id: "shard2rs",
    members: [
      { _id: 0, host: "mongo-shard2a:27017" },
      { _id: 1, host: "mongo-shard2b:27017" }
    ]
  });
}
EOF

echo "Waiting for mongos to be ready to accept shards..."
sleep 5
mongosh --host mongos:27017 <<EOF
try {
  sh.addShard("shard1rs/mongo-shard1a:27017,mongo-shard1b:27017");
  sh.addShard("shard2rs/mongo-shard2a:27017,mongo-shard2b:27017");
  sh.enableSharding("benchmark");
  sh.shardCollection("benchmark.products", { "_id": "hashed" });
  print("Sharding enabled on benchmark.products with hashed _id!");
} catch (e) {
  print("Error or already sharded: " + e);
}
EOF

echo "MongoDB sharded topology initialization complete."
