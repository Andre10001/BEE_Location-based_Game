/**
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership.
 *
 * Camunda licenses this file to you under the MIT; you may not use this file
 * except in compliance with the MIT License.
 */

const log = require('./app/lib/log')('app:dev');

const { default: installExtension, REACT_DEVELOPER_TOOLS } = require('electron-extension-installer');

const getAppVersion = require('./app/util/get-version');

const AXE_DEVTOOLS = 'lhdoppojpmngadmnindnejefpokejbdd';

// enable development perks
process.env.NODE_ENV = 'development';

// monkey-patch package version to indicate DEV mode in application
const pkg = require('./app/package');

pkg.version = getAppVersion();

// monkey-patch cli args to not open this file in application
process.argv = process.argv.filter(arg => !arg.includes('dev.js'));

const app = require('./app/lib');


// make sure the app quits and does not hang
app.on('before-quit', function() {
  app.exit(0);
});

app.on('app:window-created', async () => {

  // Enable right-click "Inspect Element" context menu
  if (app.mainWindow) {
    app.mainWindow.webContents.on('context-menu', (event, params) => {
      const { Menu, MenuItem } = require('electron');
      const menu = new Menu();

      menu.append(new MenuItem({
        label: 'Inspect Element',
        click: () => {
          app.mainWindow.webContents.inspectElement(params.x, params.y);
        }
      }));

      menu.append(new MenuItem({
        label: 'Open DevTools (Separate Window)',
        click: () => {
          app.mainWindow.webContents.openDevTools({ mode: 'detach' });
        }
      }));

      menu.popup();
    });
    log.info('Right-click context menu enabled for DevTools');
  }

  for (const extension of [
    REACT_DEVELOPER_TOOLS,
    AXE_DEVTOOLS
  ]) {
    try {
      const name = await installExtension(extension, {
        loadExtensionOptions: {
          allowFileAccess: true
        }
      });
      log.info('added extension <%s>', name);
    } catch (err) {
      log.error('failed to add extension', err);
    }
  }
});

try {

  // reload on changes but ignore client source (we want to watch build dir)
  require('electron-reloader')(module, {
    ignore: [ 'client/src', 'resources/diagram' ]
  });
} catch (err) {

  // ignore it
}
