let GAME_API = location.origin;

if (location.port === '8082') {
  GAME_API = location.protocol + '//' + location.hostname + ':8080';
}

const ADMIN_URL = 'map-editor.html';
