// DEVELOPMENT ONLY: recreates demo.order_messages and removes all previous messages.
const database = db.getSiblingDB('demo');

database.order_messages.drop();
database.createCollection('order_messages');

database.order_messages.createIndex(
  { orderId: 1, createTime: -1 },
  { name: 'idx_order_time' }
);
database.order_messages.createIndex(
  { orderId: 1, fromUserId: 1, clientMsgId: 1 },
  { name: 'uniq_order_clientMsg', unique: true }
);
database.order_messages.createIndex(
  { toUserId: 1, read: 1, createTime: -1 },
  { name: 'idx_to_read' }
);

database.order_messages.insertMany([
  {
    orderId: NumberLong('1003'),
    fromUserId: NumberLong('2'),
    toUserId: NumberLong('3'),
    content: 'The product arrived. I have submitted the development after-sale request.',
    read: false,
    clientMsgId: 'DEV-MSG-1003-BUYER-001',
    createTime: new Date('2026-07-22T05:00:00.000Z')
  },
  {
    orderId: NumberLong('1003'),
    fromUserId: NumberLong('3'),
    toUserId: NumberLong('2'),
    content: 'I will review the evidence in this development fixture.',
    read: false,
    clientMsgId: 'DEV-MSG-1003-SELLER-001',
    createTime: new Date('2026-07-22T05:05:00.000Z')
  },
  {
    orderId: NumberLong('1002'),
    fromUserId: NumberLong('0'),
    toUserId: NumberLong('3'),
    content: 'System reminder: please ship the paid development order before its deadline.',
    read: false,
    clientMsgId: 'DEV-MSG-1002-SYSTEM-H24',
    createTime: new Date('2026-07-23T02:00:00.000Z')
  }
]);

const messageCount = database.order_messages.countDocuments();
const indexNames = database.order_messages.getIndexes().map((index) => index.name).sort();
if (messageCount !== 3 || !indexNames.includes('idx_order_time') || !indexNames.includes('uniq_order_clientMsg') || !indexNames.includes('idx_to_read')) {
  throw new Error(`MongoDB seed verification failed: messages=${messageCount}, indexes=${indexNames.join(',')}`);
}

print(`MongoDB development seed complete: messages=${messageCount}, indexes=${indexNames.join(',')}`);
