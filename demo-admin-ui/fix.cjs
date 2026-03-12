const fs = require('fs');
const path = require('path');

function isAscii(buffer) {
    for (let i=0; i<buffer.length; i++) {
        if (buffer[i] > 127) return false;
    }
    return true;
}

function processFile(f) {
    const buf = fs.readFileSync(f);
    if (isAscii(buf)) return; // safe, nothing to fix
    
    // Check if it's already UTF-8
    let isUtf8 = true;
    try {
        const textUtf8 = new TextDecoder('utf-8', { fatal: true }).decode(buf);
    } catch (e) {
        isUtf8 = false;
    }
    
    if (!isUtf8) {
        // It must be GBK.
        const textGBK = new TextDecoder('gbk').decode(buf);
        fs.writeFileSync(f, textGBK, 'utf8');
        console.log('Fixed:', f);
    }
}

function walk(dir) {
  let results = [];
  const list = fs.readdirSync(dir);
  list.forEach(file => {
    const full = path.join(dir, file);
    const stat = fs.statSync(full);
    if (stat && stat.isDirectory()) { results = results.concat(walk(full)); }
    else { if (full.endsWith('.vue') || full.endsWith('.css')) results.push(full); }
  });
  return results;
}

const files = walk('./src');
files.forEach(processFile);
console.log('Conversion done.');
