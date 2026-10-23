#!/bin/bash
set -e

echo "Waiting for MongoDB nodes to be ready..."
until mongosh --host mongo1:27017 --eval "db.adminCommand('ping')" --quiet; do
    echo "Waiting for mongo1..."
    sleep 2
done

until mongosh --host mongo2:27017 --eval "db.adminCommand('ping')" --quiet; do
    echo "Waiting for mongo2..."
    sleep 2
done

until mongosh --host mongo3:27017 --eval "db.adminCommand('ping')" --quiet; do
    echo "Waiting for mongo3..."
    sleep 2
done

echo "Initiating replica set rs0..."
mongosh --host mongo1:27017 <<EOF
try {
    const status = rs.status();
    print("Replica set already initiated: " + status.set);
} catch (e) {
    print("Initiating rs0...");
    rs.initiate({
        _id: "rs0",
        members: [
            { _id: 0, host: "mongo1:27017", priority: 2 },
            { _id: 1, host: "mongo2:27017", priority: 1 },
            { _id: 2, host: "mongo3:27017", priority: 1 }
        ]
    });
}
EOF

echo "Waiting for primary election..."
until mongosh --host mongo1:27017 --eval "rs.isMaster().ismaster" --quiet | grep "true"; do
    echo "Waiting for primary on mongo1..."
    sleep 2
done

echo "Replica set rs0 initialized and primary is available."
