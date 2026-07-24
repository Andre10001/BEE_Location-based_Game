import { EXTENSION_TYPES, TASK_TYPE_KEYS } from '../modules/TaskTypes';

/**
 * Service for extracting and managing message flow binding/unbinding data
 * Similar pattern to your existing XML services
 */
export function MessageFlowXmlService(extensionService, elementRegistry) {
  this._extensionService = extensionService;
  this._elementRegistry = elementRegistry;
}

MessageFlowXmlService.$inject = ['extensionService', 'elementRegistry'];

/**
 * Extract connection type from message flow
 * @returns {string|null} 'binding', 'unbinding', or null
 */
MessageFlowXmlService.prototype.getConnectionType = function(messageFlow) {
  if (!messageFlow?.businessObject?.extensionElements?.values) {
    return null;
  }
  
  const typeElement = messageFlow.businessObject.extensionElements.values.find(
    v => v.$type === EXTENSION_TYPES.TYPE
  );
  
  return typeElement?.body || null;
};

/**
 * Extract source reference (participant ID) from message flow
 */
MessageFlowXmlService.prototype.getParticipant1 = function(messageFlow) {
  if (!messageFlow?.businessObject?.extensionElements?.values) {
    return null;
  }
  
  const sourceRefElement = messageFlow.businessObject.extensionElements.values.find(
    v => v.$type === EXTENSION_TYPES.PARTICIPANT1
  );
  
  return sourceRefElement?.body || null;
};

/**
 * Extract target reference (participant ID) from message flow
 */
MessageFlowXmlService.prototype.getParticipant2 = function(messageFlow) {
  if (!messageFlow?.businessObject?.extensionElements?.values) {
    return null;
  }
  
  const targetRefElement = messageFlow.businessObject.extensionElements.values.find(
    v => v.$type === EXTENSION_TYPES.PARTICIPANT2
  );
  
  return targetRefElement?.body || null;
};

/**
 * Get connection mode from message flow ('static' | 'dynamic'). Defaults to 'dynamic'.
 */
MessageFlowXmlService.prototype.getConnectionMode = function(messageFlow) {
  if (!messageFlow?.businessObject?.extensionElements?.values) {
    return 'dynamic';
  }
  const el = messageFlow.businessObject.extensionElements.values.find(
    v => v.$type === EXTENSION_TYPES.CONNECTION_MODE
  );
  const mode = el?.body || 'dynamic';
  return mode === 'static' ? 'static' : 'dynamic';
};

/**
 * Get leader participant ID for static mode (which end shows arrow).
 */
MessageFlowXmlService.prototype.getLeaderId = function(messageFlow) {
  if (!messageFlow?.businessObject?.extensionElements?.values) {
    return null;
  }
  const el = messageFlow.businessObject.extensionElements.values.find(
    v => v.$type === EXTENSION_TYPES.LEADER_ID
  );
  return el?.body || null;
};

/**
 * Get complete connection info from message flow
 */
MessageFlowXmlService.prototype.getConnectionInfo = function(messageFlow) {
  const type = this.getConnectionType(messageFlow);

  if (!type) {
    return null;
  }

  return {
    type: type,
    participant1: this.getParticipant1(messageFlow),
    participant2: this.getParticipant2(messageFlow),
    connectionMode: this.getConnectionMode(messageFlow),
    leaderId: this.getLeaderId(messageFlow),
    sourceTaskId: messageFlow.source?.id,
    targetTaskId: messageFlow.target?.id,
    connectionId: messageFlow.id
  };
};

/**
 * Find all binding message flows
 */
MessageFlowXmlService.prototype.getAllBindingFlows = function() {
  return this._elementRegistry.filter(element => {
    if (element.type !== 'bpmn:MessageFlow') return false;
    return this.getConnectionType(element) === TASK_TYPE_KEYS.BINDING;
  });
};

/**
 * Find all unbinding message flows
 */
MessageFlowXmlService.prototype.getAllUnbindingFlows = function() {
  return this._elementRegistry.filter(element => {
    if (element.type !== 'bpmn:MessageFlow') return false;
    return this.getConnectionType(element) === TASK_TYPE_KEYS.UNBINDING;
  });
};

/**
 * Check if two participants are bound
 */
MessageFlowXmlService.prototype.areParticipantsBound = function(participant1Id, participant2Id) {
  const bindingFlows = this.getAllBindingFlows();
  
  return bindingFlows.some(flow => {
    const sourceRef = this.getParticipant1(flow);
    const targetRef = this.getParticipant2(flow);
    
    return (sourceRef === participant1Id && targetRef === participant2Id) ||
           (sourceRef === participant2Id && targetRef === participant1Id);
  });
};

/**
 * Get all message flows for a specific participant
 */
MessageFlowXmlService.prototype.getFlowsForParticipant = function(participantId) {
  return this._elementRegistry.filter(element => {
    if (element.type !== 'bpmn:MessageFlow') return false;
    
    const sourceRef = this.getParticipant1(element);
    const targetRef = this.getParticipant2(element);
    
    return sourceRef === participantId || targetRef === participantId;
  });
};