const fs = require('node:fs');
const path = process.argv[2];
const data = JSON.parse(fs.readFileSync(path, 'utf8'));
console.log('Total:', data.total);
for (const i of data.issues) {
  const comp = i.component.replace('ai-demo:', '');
  console.log(i.severity.padEnd(8) + ' ' + i.rule.padEnd(22) + ' ' + comp + ':' + (i.line || '?') + '  ' + i.message.slice(0, 100));
}
