import { EXTENSION_TYPES } from '../modules/TaskTypes';

/**
 * ExtensionService - Manages BPMN extension elements and moddle operations
 * 
 * Responsibilities:
 * - Create/update/remove extension elements
 * - Manage extensionElements collections
 * - Handle moddle property updates
 * - Provide utilities for reading extension data
 */
export function ExtensionService(modeling, bpmnFactory) {
  this.modeling = modeling;
  this.bpmnFactory = bpmnFactory;
}

ExtensionService.$inject = ['modeling', 'bpmnFactory'];

/**
 * Get all extension element values from a business object
 * @param {Object} bo - Business object
 * @returns {Array} Array of extension elements
 */
ExtensionService.prototype.getExtensionValues = function(bo) {
  return (bo.extensionElements && bo.extensionElements.values) || [];
};

/**
 * Find a specific extension element by type
 * @param {Object} bo - Business object
 * @param {string} type - Extension type (e.g., 'space:Type')
 * @returns {Object|null} Extension element or null
 */
ExtensionService.prototype.findExtension = function(bo, type) {
  return this.getExtensionValues(bo).find(v => v.$type === type);
};

/**
 * Get text content from an extension element (supports legacy 'value' property)
 * @param {Object} element - Extension element
 * @returns {string} Text content
 */
ExtensionService.prototype.getText = function(element) {
  return (element && (element.body ?? element.value)) || "";
};

/**
 * Ensure extensionElements container exists on business object
 * @param {Object} element - BPMN element
 * @param {Object} bo - Business object
 */
ExtensionService.prototype.ensureExtensionElements = function(element, bo) {
  if (!bo.extensionElements) {
    const ext = this.bpmnFactory.create("bpmn:ExtensionElements", { values: [] });
    this.modeling.updateModdleProperties(element, bo, { extensionElements: ext });
  }
};

/**
 * Set or update an extension element
 * @param {Object} element - BPMN element
 * @param {string} type - Extension type
 * @param {string} value - Extension value
 */
ExtensionService.prototype.setExtension = function(element, type, value) {
  const bo = element.businessObject;
  this.ensureExtensionElements(element, bo);

  let ext = this.findExtension(bo, type);
  if (!ext) {
    // Create new extension element
    ext = this.bpmnFactory.create(type, { body: value });
    const values = [...(bo.extensionElements.values || []), ext];
    this.modeling.updateModdleProperties(element, bo.extensionElements, { values });
  } else {
    // Update existing extension element
    this.modeling.updateModdleProperties(element, ext, { body: value });
  }
};

/**
 * Remove extension elements matching a predicate
 * @param {Object} element - BPMN element
 * @param {Function} predicate - Function to test elements for removal
 */
ExtensionService.prototype.removeExtensions = function(element, predicate) {
  const bo = element.businessObject;
  const existing = this.getExtensionValues(bo);
  const keep = existing.filter(v => !predicate(v));
  
  if (keep.length !== existing.length) {
    this.modeling.updateModdleProperties(element, bo.extensionElements, { values: keep });
  }
};

/**
 * Get current task type from element
 * @param {Object} element - BPMN element
 * @returns {string} Current type (lowercase) or empty string
 */
ExtensionService.prototype.getCurrentType = function(element) {
  const typeEl = this.findExtension(element.businessObject, EXTENSION_TYPES.TYPE);
  return (typeEl && String(typeEl.body).toLowerCase()) || "";
};

/**
 * Get destination value from element
 * @param {Object} element - BPMN element
 * @returns {string} Destination value or empty string
 */
ExtensionService.prototype.getDestination = function(element) {
  const destEl = this.findExtension(element.businessObject, EXTENSION_TYPES.DESTINATION);
  return this.getText(destEl);
};

/**
 * Get binding value from element
 * @param {Object} element - BPMN element
 * @returns {string} Binding value or empty string
 */
ExtensionService.prototype.getBinding = function(element) {
  const bindingEl = this.findExtension(element.businessObject, EXTENSION_TYPES.BINDING);
  return this.getText(bindingEl);
};

/**
 * Get guard value from element
 * @param {Object} element - BPMN element
 * @returns {string} Guard value or empty string
 */
ExtensionService.prototype.getGuard = function(element) {
  const guardEl = this.findExtension(element.businessObject, EXTENSION_TYPES.GUARD);
  return this.getText(guardEl);
};

/**
 * Get action value from element
 * @param {Object} element - BPMN element
 * @returns {string} Action value or empty string
 */
ExtensionService.prototype.getAction = function(element) {
  const actionEl = this.findExtension(element.businessObject, EXTENSION_TYPES.ACTION);
  return this.getText(actionEl);
};

/**
 * Get timer value from element
 * @param {Object} element - BPMN element
 * @returns {string} Timer value or empty string
 */
ExtensionService.prototype.getTimer = function(element) {
  const timerEl = this.findExtension(element.businessObject, EXTENSION_TYPES.TIMER);
  return this.getText(timerEl);
};

/**
 * Check if element has a specific extension type
 * @param {Object} element - BPMN element
 * @param {string} type - Extension type
 * @returns {boolean} True if extension exists
 */
ExtensionService.prototype.hasExtension = function(element, type) {
  return !!this.findExtension(element.businessObject, type);
};

/**
 * Clear all custom extensions from element
 * @param {Object} element - BPMN element
 */
ExtensionService.prototype.clearCustomExtensions = function(element) {
  const customTypes = Object.values(EXTENSION_TYPES);
  this.removeExtensions(element, v => customTypes.includes(v.$type));
};