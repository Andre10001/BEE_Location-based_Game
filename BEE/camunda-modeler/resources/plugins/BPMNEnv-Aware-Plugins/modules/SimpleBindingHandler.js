import { TASK_TYPE_KEYS, EXTENSION_TYPES } from './TaskTypes';

export default function SimpleBindingHandler(
    eventBus,
    bpmnFactory,
    extensionService,
    modeling,
    elementRegistry
) {
  const self = this;

  // Track connections being processed to prevent loops
  this._processingConnections = new Set();
  this._updateTimeout = null;

  console.debug('SimpleBindingHandler initialized');

  // Listen for message flow creation
  eventBus.on('connection.added', 1500, function(event) {
    const connection = event.element;

    if (connection && connection.type === 'bpmn:MessageFlow') {
      console.debug('Message flow added:', connection.id);
      self.scheduleUpdate(connection);
    }
  });

  // Listen for connection reconnect
  eventBus.on('commandStack.connection.reconnect.executed', function(event) {
    const context = event.context;
    const connection = context.connection;

    if (connection && connection.type === 'bpmn:MessageFlow') {
      console.debug('Message flow reconnected:', connection.id);
      self.scheduleUpdate(connection, true); // force update on reconnect
    }
  });

  // Handle imports
  eventBus.on('import.done', function() {
    console.debug('Import done, checking for message flows...');

    const messageFlows = elementRegistry.filter(e => e.type === 'bpmn:MessageFlow');
    console.debug('Message flows found:', messageFlows.length);

    messageFlows.forEach(flow => {
      self.scheduleUpdate(flow);
    });
  });

  // Listen for task type changes
  eventBus.on('element.changed', function(event) {
    const element = event.element;

    // Only react to task changes
    if (element && element.type === 'bpmn:Task') {

      // Find connected message flows
      const connectedFlows = [ ...(element.incoming || []), ...(element.outgoing || []) ]
        .filter(conn => conn.type === 'bpmn:MessageFlow');

      connectedFlows.forEach(flow => {
        console.debug('Task type changed, updating connected flow:', flow.id);
        self.scheduleUpdate(flow);
      });
    }
  });

  this._bpmnFactory = bpmnFactory;
  this._extensionService = extensionService;
  this._modeling = modeling;
  this._elementRegistry = elementRegistry;
}

SimpleBindingHandler.$inject = [ 'eventBus', 'bpmnFactory', 'extensionService', 'modeling', 'elementRegistry' ];

/**
 * Schedule an update with debouncing to prevent loops
 */
SimpleBindingHandler.prototype.scheduleUpdate = function(connection, forceUpdate = false) {
  if (!connection || !connection.id) return;

  // Skip if already processing this connection
  if (this._processingConnections.has(connection.id) && !forceUpdate) {
    return;
  }

  // Clear existing timeout
  if (this._updateTimeout) {
    clearTimeout(this._updateTimeout);
  }

  // Schedule update with small delay
  this._updateTimeout = setTimeout(() => {
    this.processConnection(connection, forceUpdate);
  }, 50);
};

/**
 * Process a single connection
 */
SimpleBindingHandler.prototype.processConnection = function(connection, forceUpdate = false) {
  if (!connection || !connection.id) return;

  // Mark as processing
  this._processingConnections.add(connection.id);

  try {
    if (forceUpdate) {
      this.updateConnectionData(connection);
    } else {
      this.checkAndAddConnectionData(connection);
    }
  } finally {

    // Remove from processing set after a delay
    setTimeout(() => {
      this._processingConnections.delete(connection.id);
    }, 100);
  }
};

/**
 * Check if data needs update
 */
SimpleBindingHandler.prototype.needsUpdate = function(connection) {
  if (!connection?.businessObject) return false;

  // Get stored values
  const storedType = this.getStoredType(connection);
  const storedSourceRef = this.getStoredParticipant1(connection);
  const storedTargetRef = this.getStoredParticipant2(connection);

  // Get current values
  const currentType = this.determineConnectionType(connection);
  const currentSourceRef = this.getParticipantId(connection.source);
  const currentTargetRef = this.getParticipantId(connection.target);

  // Check if update needed
  return storedType !== currentType ||
         storedSourceRef !== currentSourceRef ||
         storedTargetRef !== currentTargetRef;
};

/**
 * Determine connection type based on task types
 */
SimpleBindingHandler.prototype.determineConnectionType = function(connection) {
  if (!connection?.source?.type || !connection?.target?.type) return null;

  if (connection.source.type !== 'bpmn:Task' || connection.target.type !== 'bpmn:Task') {
    return null;
  }

  const sourceType = this._extensionService.getCurrentType(connection.source);
  const targetType = this._extensionService.getCurrentType(connection.target);

  if (sourceType === TASK_TYPE_KEYS.BINDING && targetType === TASK_TYPE_KEYS.BINDING) {
    return TASK_TYPE_KEYS.BINDING;
  } else if (sourceType === TASK_TYPE_KEYS.UNBINDING && targetType === TASK_TYPE_KEYS.UNBINDING) {
    return TASK_TYPE_KEYS.UNBINDING;
  }

  return null;
};

SimpleBindingHandler.prototype.checkAndAddConnectionData = function(connection) {
  if (!connection) return;

  // Check if update is actually needed
  if (!this.needsUpdate(connection)) {
    return;
  }

  const connectionType = this.determineConnectionType(connection);

  if (connectionType) {
    console.debug(`Adding/updating ${connectionType} data to message flow`);
    this.addConnectionData(connection, connectionType);
  } else if (this.hasConnectionData(connection.businessObject)) {
    console.debug('Clearing connection data - tasks not matching');
    this.clearAndUpdate(connection);
  }
};

SimpleBindingHandler.prototype.updateConnectionData = function(connection) {
  if (!connection) return;

  // Clear and re-add
  this.clearConnectionData(connection.businessObject);
  this.checkAndAddConnectionData(connection);
};

SimpleBindingHandler.prototype.addConnectionData = function(connection, connectionType) {
  const bo = connection.businessObject;
  if (!bo) return;

  const sourceParticipantId = this.getParticipantId(connection.source);
  const targetParticipantId = this.getParticipantId(connection.target);

  if (!sourceParticipantId || !targetParticipantId) {
    console.error('Could not find participant IDs');
    return;
  }

  try {

    // Create extension elements
    const typeElement = this._bpmnFactory.create(EXTENSION_TYPES.TYPE, {
      body: connectionType
    });

    const sourceRefElement = this._bpmnFactory.create(EXTENSION_TYPES.PARTICIPANT1, {
      body: sourceParticipantId
    });

    const targetRefElement = this._bpmnFactory.create(EXTENSION_TYPES.PARTICIPANT2, {
      body: targetParticipantId
    });

    if (!bo.extensionElements) {
      bo.extensionElements = this._bpmnFactory.create('bpmn:ExtensionElements', {
        values: []
      });
    }

    // Clear existing
    this.clearConnectionData(bo);

    // Add new
    bo.extensionElements.values.push(typeElement);
    bo.extensionElements.values.push(sourceRefElement);
    bo.extensionElements.values.push(targetRefElement);

    // Update model
    this._modeling.updateModdleProperties(connection, bo, {
      extensionElements: bo.extensionElements
    });

    console.debug(`Updated ${connectionType} connection data:`, {
      connectionId: connection.id,
      sourceRef: sourceParticipantId,
      targetRef: targetParticipantId
    });

  } catch (error) {
    console.error('Error adding connection extension:', error);
  }
};

SimpleBindingHandler.prototype.clearAndUpdate = function(connection) {
  if (!connection?.businessObject) return;

  this.clearConnectionData(connection.businessObject);

  this._modeling.updateModdleProperties(connection, connection.businessObject, {
    extensionElements: connection.businessObject.extensionElements
  });
};

// Helper methods
SimpleBindingHandler.prototype.getStoredType = function(connection) {
  const typeElement = connection.businessObject?.extensionElements?.values?.find(
    v => v.$type === EXTENSION_TYPES.TYPE
  );
  return typeElement?.body || null;
};

SimpleBindingHandler.prototype.getStoredParticipant1 = function(connection) {
  const element = connection.businessObject?.extensionElements?.values?.find(
    v => v.$type === EXTENSION_TYPES.PARTICIPANT1
  );
  return element?.body || null;
};

SimpleBindingHandler.prototype.getStoredParticipant2 = function(connection) {
  const element = connection.businessObject?.extensionElements?.values?.find(
    v => v.$type === EXTENSION_TYPES.PARTICIPANT2
  );
  return element?.body || null;
};

SimpleBindingHandler.prototype.hasConnectionData = function(businessObject) {
  return businessObject?.extensionElements?.values?.some(v =>
    v.$type === EXTENSION_TYPES.TYPE &&
    (v.body === TASK_TYPE_KEYS.BINDING || v.body === TASK_TYPE_KEYS.UNBINDING)
  ) || false;
};

SimpleBindingHandler.prototype.clearConnectionData = function(businessObject) {
  if (!businessObject?.extensionElements?.values) return;

  businessObject.extensionElements.values = businessObject.extensionElements.values.filter(v =>
    v.$type !== EXTENSION_TYPES.TYPE &&
    v.$type !== EXTENSION_TYPES.PARTICIPANT1 &&
    v.$type !== EXTENSION_TYPES.PARTICIPANT2
  );
};

SimpleBindingHandler.prototype.getParticipantId = function(element) {
  let current = element;
  let depth = 0;

  while (current && depth < 10) {
    if (current.type === 'bpmn:Participant') {
      return current.id;
    }
    current = current.parent;
    depth++;
  }

  return null;
};