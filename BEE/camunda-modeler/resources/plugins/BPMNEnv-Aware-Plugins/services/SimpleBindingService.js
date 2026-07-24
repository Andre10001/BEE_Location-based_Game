import { EXTENSION_TYPES, TASK_TYPE_KEYS } from '../modules/TaskTypes';


export function SimpleBindingService(elementRegistry, extensionService) {
  this._elementRegistry = elementRegistry;
  this._extensionService = extensionService;
}

SimpleBindingService.$inject = ['elementRegistry', 'extensionService'];

/**
 * Get connection information from a message flow
 */
SimpleBindingService.prototype.getConnectionInfo = function(connection) {
  if (!connection || !connection.businessObject) return null;
  
  const bo = connection.businessObject;
  
  // Check for extension elements
  if (!bo.extensionElements?.values) return null;
  
  const extensions = bo.extensionElements.values;
  
  // Find the type element
  const typeElement = extensions.find(v => v.$type === EXTENSION_TYPES.TYPE);
  if (!typeElement) return null;
  
  // Find source and target refs
  const sourceRefElement = extensions.find(v => v.$type === EXTENSION_TYPES.PARTICIPANT1);
  const targetRefElement = extensions.find(v => v.$type === EXTENSION_TYPES.PARTICIPANT2);
  
  if (!sourceRefElement || !targetRefElement) return null;
  
  return {
    type: typeElement.body, // 'binding', 'unbinding', or 'transition'
    sourceParticipantId: sourceRefElement.body,
    targetParticipantId: targetRefElement.body,
    sourceTaskId: connection.source?.id,
    targetTaskId: connection.target?.id
  };
};

/**
 * Get all message flows that have binding data
 */
SimpleBindingService.prototype.getAllBindingConnections = function() {
  return this._elementRegistry.filter(element => {
    if (element.type !== 'bpmn:MessageFlow') return false;
    const info = this.getConnectionInfo(element);
    return info && info.type === 'binding';
  });
};

/**
 * Get all message flows that have unbinding data
 */
SimpleBindingService.prototype.getAllUnbindingConnections = function() {
  return this._elementRegistry.filter(element => {
    if (element.type !== 'bpmn:MessageFlow') return false;
    const info = this.getConnectionInfo(element);
    return info && info.type === 'unbinding';
  });
};

/**
 * Get all message flows with any connection type
 */
SimpleBindingService.prototype.getAllTypedConnections = function() {
  return this._elementRegistry.filter(element => {
    return element.type === 'bpmn:MessageFlow' && 
           this.getConnectionInfo(element) !== null;
  });
};

/**
 * Check if a message flow is a binding connection
 */
SimpleBindingService.prototype.isBindingConnection = function(connection) {
  const info = this.getConnectionInfo(connection);
  return info && info.type === 'binding';
};

/**
 * Check if a message flow is an unbinding connection
 */
SimpleBindingService.prototype.isUnbindingConnection = function(connection) {
  const info = this.getConnectionInfo(connection);
  return info && info.type === 'unbinding';
};

/**
 * Get all typed connections for a specific task
 */
SimpleBindingService.prototype.getConnectionsForTask = function(taskId) {
  return this.getAllTypedConnections().filter(connection => {
    const info = this.getConnectionInfo(connection);
    return info && (info.sourceTaskId === taskId || info.targetTaskId === taskId);
  });
};

/**
 * Get all typed connections for a specific participant
 */
SimpleBindingService.prototype.getConnectionsForParticipant = function(participantId) {
  return this.getAllTypedConnections().filter(connection => {
    const info = this.getConnectionInfo(connection);
    return info && (info.sourceParticipantId === participantId || info.targetParticipantId === participantId);
  });
};

/**
 * Get binding pairs (which participants are bound together)
 */
SimpleBindingService.prototype.getBindingPairs = function() {
  const bindings = this.getAllBindingConnections();
  return bindings.map(binding => {
    const info = this.getConnectionInfo(binding);
    return {
      source: info.sourceParticipantId,
      target: info.targetParticipantId,
      connectionId: binding.id
    };
  });
};

/**
 * Get unbinding pairs (which participants are unbound)
 */
SimpleBindingService.prototype.getUnbindingPairs = function() {
  const unbindings = this.getAllUnbindingConnections();
  return unbindings.map(unbinding => {
    const info = this.getConnectionInfo(unbinding);
    return {
      source: info.sourceParticipantId,
      target: info.targetParticipantId,
      connectionId: unbinding.id
    };
  });
};

/**
 * Check if two participants are bound
 */
SimpleBindingService.prototype.areParticipantsBound = function(participant1Id, participant2Id) {
  const bindings = this.getAllBindingConnections();
  return bindings.some(binding => {
    const info = this.getConnectionInfo(binding);
    return (info.sourceParticipantId === participant1Id && info.targetParticipantId === participant2Id) ||
           (info.sourceParticipantId === participant2Id && info.targetParticipantId === participant1Id);
  });
};