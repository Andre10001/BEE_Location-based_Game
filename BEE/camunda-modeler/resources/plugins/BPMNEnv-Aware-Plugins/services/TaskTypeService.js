import {
  getTaskConfig,
  EXTENSION_TYPES,
  TASK_TYPE_KEYS,
} from "../modules/TaskTypes";

/**
 * TaskTypeService - Handles task type changes and related operations
 *
 * Responsibilities:
 * - Set and change task types
 * - Clean up incompatible extensions
 * - Initialize required extensions
 * - Handle type transitions
 */
export function TaskTypeService(extensionService, eventBus) {
  this.extensionService = extensionService;
  this.eventBus = eventBus;
}

TaskTypeService.$inject = ["extensionService", "eventBus"];

/**
 * Set the task type and handle all related changes
 * @param {Object} element - BPMN element
 * @param {string} typeKey - Task type key
 * @throws {Error} If unknown task type
 */
TaskTypeService.prototype.setTaskType = function (element, typeKey) {
  const config = getTaskConfig(typeKey);
  if (!config) {
    throw new Error(`Unknown task type: ${typeKey}`);
  }

  const previousType = this.extensionService.getCurrentType(element);
  this.extensionService.setExtension(element, EXTENSION_TYPES.TYPE, typeKey);
  this.handleTypeTransition(element, previousType, typeKey, config);
  // Fire change event
  this.eventBus.fire("elements.changed", { elements: [element] });
};

/**
 * Handle the complete transition from one type to another
 * @param {Object} element - BPMN element
 * @param {string} previousType - Previous task type
 * @param {string} newType - New task type
 * @param {Object} config - New type configuration
 */
TaskTypeService.prototype.handleTypeTransition = function (
  element,
  previousType,
  newType,
  config
) {
  // Clean up incompatible extensions
  this.cleanupIncompatibleExtensions(element, config);

  // Initialize new extensions
  this.initializeExtensions(element, config);

  // Handle specific type transitions
  this.handleSpecificTransitions(element, previousType, newType);
};

/**
 * Remove extensions that are not compatible with the new type
 * @param {Object} element - BPMN element
 * @param {Object} config - Task type configuration
 */
TaskTypeService.prototype.cleanupIncompatibleExtensions = function (
  element,
  config
) {
  const allowedTypes = [EXTENSION_TYPES.TYPE, ...config.extensionElements];
  this.extensionService.removeExtensions(
    element,
    (v) => !allowedTypes.includes(v.$type)
  );
};

/**
 * Initialize required extensions with default values
 * @param {Object} element - BPMN element
 * @param {Object} config - Task type configuration
 */
TaskTypeService.prototype.initializeExtensions = function (element, config) {
  // Initialize destination extension for movement tasks
  if (config.extensionElements.includes(EXTENSION_TYPES.DESTINATION)) {
    const currentDestination = this.extensionService.getDestination(element);
    if (!currentDestination && config.defaultDestination) {
      this.extensionService.setExtension(
        element,
        EXTENSION_TYPES.DESTINATION,
        config.defaultDestination
      );
    }
  }

  // Initialize binding extension for binding tasks
  if (config.extensionElements.includes(EXTENSION_TYPES.BINDING)) {
    const currentBinding = this.extensionService.getBinding(element);
    if (!currentBinding) {
      this.extensionService.setExtension(element, EXTENSION_TYPES.BINDING, "");
    }
  }
};

/**
 * Handle specific type transitions that need special logic
 * @param {Object} element - BPMN element
 * @param {string} previousType - Previous task type
 * @param {string} newType - New task type
 */
TaskTypeService.prototype.handleSpecificTransitions = function (
  element,
  previousType,
  newType
) {
  //Track all the conditions

  // Transition from binding to something else
  if (
    previousType === TASK_TYPE_KEYS.BINDING &&
    newType !== TASK_TYPE_KEYS.BINDING
  ) {
    this.handleBindingToOtherTransition(element, newType);
  }

  // Transition to binding from something else
  if (
    previousType !== TASK_TYPE_KEYS.BINDING &&
    newType === TASK_TYPE_KEYS.BINDING
  ) {
    this.handleOtherToBindingTransition(element, previousType);
  }

  // Transition to movement type
  if (newType === TASK_TYPE_KEYS.MOVEMENT) {
    this.handleMovementTransition(element, previousType);
  }

  // Transition from movement type
  if (
    previousType === TASK_TYPE_KEYS.MOVEMENT &&
    newType !== TASK_TYPE_KEYS.MOVEMENT
  ) {
    this.handleFromMovementTransition(element, newType);
  }
};

/**
 * Handle transition from binding to another type
 * @param {Object} element - BPMN element
 * @param {string} newType - New task type
 */
TaskTypeService.prototype.handleBindingToOtherTransition = function (
  element,
  newType
) {
  // Log the change for debugging
  console.log(`Changing binding task ${element.id} to ${newType}`);
};

/**
 * Handle transition from another type to binding
 * @param {Object} element - BPMN element
 * @param {string} previousType - Previous task type
 */
TaskTypeService.prototype.handleOtherToBindingTransition = function (
  element,
  previousType
) {
  console.log(`Changing ${previousType} task ${element.id} to binding`);
};

/**
 * Handle transition to movement type
 * @param {Object} element - BPMN element
 * @param {string} previousType - Previous task type
 */
TaskTypeService.prototype.handleMovementTransition = function (
  element,
  previousType
) {
  // Could handle movement-specific initialization
};

/**
 * Handle transition from movement type
 * @param {Object} element - BPMN element
 * @param {string} newType - New task type
 */
TaskTypeService.prototype.handleFromMovementTransition = function (
  element,
  newType
) {
  // Could handle cleanup of movement-specific artifacts
};

/**
 * Get current task type of element
 * @param {Object} element - BPMN element
 * @returns {string} Current task type
 */
TaskTypeService.prototype.getCurrentType = function (element) {
  return this.extensionService.getCurrentType(element);
};

/**
 * Check if element has a specific task type
 * @param {Object} element - BPMN element
 * @param {string} typeKey - Task type to check
 * @returns {boolean} True if element has the specified type
 */
TaskTypeService.prototype.hasType = function (element, typeKey) {
  return this.getCurrentType(element) === typeKey;
};

/**
 * Clear task type from element
 * @param {Object} element - BPMN element
 */
TaskTypeService.prototype.clearTaskType = function (element) {
  this.extensionService.clearCustomExtensions(element);
  this.eventBus.fire("elements.changed", { elements: [element] });
};
