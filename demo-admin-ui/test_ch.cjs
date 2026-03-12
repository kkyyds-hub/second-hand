const fs = require('fs');
const buf = fs.readFileSync('src/pages/Dashboard.vue');
const ix = buf.indexOf(Buffer.from('今'));
if (ix !== -1) {
    console.log('Found bytes for 今 at', ix);
    console.log('Hex:', buf.subarray(ix, ix + 20).toString('hex'));
}
