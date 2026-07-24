const { execSync } = require('child_process');
const path = require('path');

const pluginDir = path.join(__dirname, '..', 'resources', 'plugins', 'BPMNEnv-Aware-Plugins');

console.log(`Installing dependencies in ${pluginDir}...`);
process.chdir(pluginDir);
execSync('npm install', { stdio: 'inherit' });

