const d = db.getSiblingDB("benchmark");
const o = d.orders.findOne();
print("type=" + (o.createdAt instanceof Date ? "Date" : typeof o.createdAt) + " value=" + o.createdAt);
print("stringFilter=" + d.orders.countDocuments({ createdAt: { $gte: "2024-01-05T00:00:00Z" } }));
print("dateFilter=" + d.orders.countDocuments({ createdAt: { $gte: new Date("2024-01-05T00:00:00Z") } }));
print("total=" + d.orders.countDocuments({}));
print("min=" + d.orders.find().sort({ createdAt: 1 }).limit(1).toArray()[0].createdAt);
print("max=" + d.orders.find().sort({ createdAt: -1 }).limit(1).toArray()[0].createdAt);
