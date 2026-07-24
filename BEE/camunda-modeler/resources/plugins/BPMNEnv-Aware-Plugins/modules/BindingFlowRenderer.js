import { TASK_TYPE_KEYS } from './TaskTypes';

/**
 * Custom renderer for binding/unbinding message flows
 * Adds dots on BOTH ends (symmetric) for visual distinction
 */
function BindingFlowRenderer(
    eventBus,
    elementRegistry,
    canvas,
    messageFlowXmlService
) {
  const self = this;

  // Set up markers on canvas ready
  eventBus.on('canvas.init', function() {
    self.ensureCustomMarkers();
  });

  // After import, update all message flows
  eventBus.on('import.render.complete', function() {
    self.ensureCustomMarkers();
    setTimeout(() => {
      self.updateAllMessageFlows();
    }, 100);
  });

  // Handle connection added
  eventBus.on('connection.added', function(event) {
    const element = event.element;
    if (element && element.type === 'bpmn:MessageFlow') {
      setTimeout(() => {
        self.updateMessageFlow(element);
      }, 50);
    }
  });

  // Handle connection changes
  eventBus.on('connection.changed', function(event) {
    const element = event.element;
    if (element && element.type === 'bpmn:MessageFlow') {
      setTimeout(() => {
        self.updateMessageFlow(element);
      }, 50);
    }
  });

  // Handle elements changed
  eventBus.on('elements.changed', function(event) {
    if (event.elements) {
      event.elements.forEach(element => {
        if (element.type === 'bpmn:MessageFlow') {
          self.updateMessageFlow(element);
        }
      });
    }
  });

  this._canvas = canvas;
  this._elementRegistry = elementRegistry;
  this._messageFlowXmlService = messageFlowXmlService;
}

BindingFlowRenderer.$inject = [
  'eventBus',
  'elementRegistry',
  'canvas',
  'messageFlowXmlService'
];

/**
 * Ensure custom SVG markers are defined in the defs of the given SVG (or canvas root).
 * Uses svgRoot when provided so markers live in the same SVG as the connection (url(#id) resolves).
 * Only adds markers if they are not already present to avoid duplicate IDs.
 */
BindingFlowRenderer.prototype.ensureCustomMarkers = function(svgRoot) {
  const container = svgRoot || (this._canvas && this._canvas._svg);
  if (!container) return;

  const root = (container.nodeName && container.nodeName.toLowerCase() === 'svg')
    ? container
    : (container.closest && container.closest('svg')) || container;

  let defs = root.querySelector('defs');
  if (!defs) {
    defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs');
    root.insertBefore(defs, root.firstChild);
  }

  if (!defs.querySelector('#binding-dot')) {
    defs.appendChild(this.createDotMarker('binding-dot'));
  }
  if (!defs.querySelector('#binding-arrow')) {
    defs.appendChild(this.createArrowMarker('binding-arrow'));
  }
};

/**
 * Create a dot marker element - centered so it works for both start and end
 */
BindingFlowRenderer.prototype.createDotMarker = function(id) {
  const marker = document.createElementNS('http://www.w3.org/2000/svg', 'marker');
  marker.setAttribute('id', id);
  marker.setAttribute('viewBox', '0 0 35 35');
  marker.setAttribute('fill', '#fff');

  // Adjust refX based on whether it's start or end marker
  marker.setAttribute('refX', '10');
  marker.setAttribute('refY', '10');
  marker.setAttribute('markerWidth', '35');
  marker.setAttribute('markerHeight', '35');
  marker.setAttribute('orient', 'auto');

  const rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
  rect.setAttribute('x', '5');
  rect.setAttribute('y', '5');
  rect.setAttribute('width', '10');
  rect.setAttribute('height', '10');
  rect.setAttribute('rx', '2'); // Rounded corners
  marker.appendChild(rect);

  const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
  line.setAttribute('x1', '10');
  line.setAttribute('y1', '0');
  line.setAttribute('x2', '10');
  line.setAttribute('y2', '15');
  line.setAttribute('stroke', '#000');
  line.setAttribute('stroke-width', '1');
  marker.appendChild(line);

  const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
  circle.setAttribute('cx', '10');
  circle.setAttribute('cy', '17.5');
  circle.setAttribute('r', '3.5');
  circle.setAttribute('fill', 'white');
  circle.setAttribute('stroke', '#000');
  circle.setAttribute('stroke-width', '1');
  marker.appendChild(circle);
  return marker;
};

/**
 * Create an arrow marker for static mode (leader end).
 * Single filled triangle only (like createDotMarker: one simple shape), pointing right.
 */
BindingFlowRenderer.prototype.createArrowMarker = function(id) {
  const marker = document.createElementNS('http://www.w3.org/2000/svg', 'marker');
  marker.setAttribute('id', id);
  marker.setAttribute('viewBox', '0 0 20 20');
  marker.setAttribute('refX', '20');
  marker.setAttribute('refY', '10');
  marker.setAttribute('markerWidth', '20');
  marker.setAttribute('markerHeight', '20');
  marker.setAttribute('markerUnits', 'userSpaceOnUse');
  marker.setAttribute('orient', 'auto');

  const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  path.setAttribute('d', 'M 0 5 L 20 10 L 0 15 Z');
  path.setAttribute('fill', '#000');
  path.setAttribute('stroke', 'none');
  marker.appendChild(path);
  return marker;
};


BindingFlowRenderer.prototype.updateMessageFlow = function(element) {
  if (!element || element.type !== 'bpmn:MessageFlow') return;

  const gfx = this._elementRegistry.getGraphics(element);
  if (!gfx) return;

  const connectionInfo = this._messageFlowXmlService.getConnectionInfo(element);
  const dot = 'url(#binding-dot)';
  const arrow = 'url(#binding-arrow)';
  let desired = { start: dot, end: dot };

  if (connectionInfo) {
    const mode = connectionInfo.connectionMode || 'dynamic';
    const leaderId = connectionInfo.leaderId;
    const participant1 = connectionInfo.participant1;
    const participant2 = connectionInfo.participant2;
    if (mode === 'static' && leaderId && (participant1 || participant2)) {
      // Arrow on the leader's end to identify the connection task; dot on the other end.
      // participant1 = source (path start), participant2 = target (path end).
      if (leaderId === participant1) desired = { start: arrow, end: dot };
      else if (leaderId === participant2) desired = { start: dot, end: arrow };
    }
  }

  const paths = gfx.querySelectorAll('.djs-visual path');
  if (!paths || paths.length === 0) return;

  const isBindingOrUnbinding = connectionInfo &&
    (connectionInfo.type === TASK_TYPE_KEYS.BINDING || connectionInfo.type === TASK_TYPE_KEYS.UNBINDING);
  if (isBindingOrUnbinding) {
    const svg = gfx.closest('svg');
    if (svg) this.ensureCustomMarkers(svg);
  }

  // Clear markers on all paths so no duplicate/opposite path shows a second marker.
  paths.forEach(p => {
    p.removeAttribute('marker-start');
    p.removeAttribute('marker-end');
    p.removeAttribute('marker-mid');
  });

  // Apply our markers only to the first (main) path. Other paths (e.g. outline) can have
  // opposite direction and would draw circle and arrow at the same end if we set them too.
  const path = paths[0];
  if (connectionInfo && (connectionInfo.type === TASK_TYPE_KEYS.BINDING || connectionInfo.type === TASK_TYPE_KEYS.UNBINDING)) {
    path.setAttribute('marker-start', desired.start);
    path.setAttribute('marker-end', desired.end);
  }
};

/**
 * Update all message flows
 */
BindingFlowRenderer.prototype.updateAllMessageFlows = function() {
  const messageFlows = this._elementRegistry.filter(element =>
    element.type === 'bpmn:MessageFlow'
  );

  messageFlows.forEach(flow => {
    this.updateMessageFlow(flow);
  });
};

export default {
  __init__: [ 'bindingFlowRenderer' ],
  bindingFlowRenderer: [ 'type', BindingFlowRenderer ]
};