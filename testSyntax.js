const fs = require('fs');
const vm = require('vm');
const html = fs.readFileSync('c:\\Users\\TechnoKraft\\Desktop\\i-rasa\\i-rasa\\admin-orders.html', 'utf8');
const scriptMatch = html.match(/<script>\s*([\s\S]*?)<\/script>/g);
if (scriptMatch) {
  const code = scriptMatch[scriptMatch.length - 1].replace(/<\/?script>/g, '');
  try {
    new vm.Script(code);
    console.log("Syntax is OK!");
  } catch(e) {
    console.log("Syntax Error: ", e.message);
  }
} else {
  console.log("No script found");
}
