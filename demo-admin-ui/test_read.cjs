const fs = require('fs');
const buf = fs.readFileSync('src/pages/Dashboard.vue');
console.log('Buffer hex top 100 bytes:', buf.subarray(0, 100).toString('hex'));
console.log('Buffer string utf8:', buf.toString('utf8').substring(0, 200));
console.log('Buffer string ansi (latin1):', buf.toString('latin1').substring(0, 200));
