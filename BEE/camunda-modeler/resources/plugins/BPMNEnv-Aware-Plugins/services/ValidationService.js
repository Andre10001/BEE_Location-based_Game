import { getTaskConfig, requiresValidation, TASK_TYPE_KEYS } from '../modules/TaskTypes';

export function ValidationService(elementRegistry, extensionService) {
  this.elementRegistry = elementRegistry;
  this.extensionService = extensionService;
}

ValidationService.$inject = ['elementRegistry', 'extensionService'];

/**
 * Main validation entry point - now returns warnings instead of blocking
 * @param {Object} element - BPMN element being changed
 * @param {string} newTypeKey - Target task type
 * @param {Function} translate - Translation function
 * @returns {Object} Validation result {valid: boolean, warning?: string, severity?: string}
 */
ValidationService.prototype.validateTypeChange = function(element, newTypeKey, translate) {
  const config = getTaskConfig(newTypeKey);
  if (!config) {
    return { valid: false, error: translate("Invalid task type") };
  }

  const currentType = this.extensionService.getCurrentType(element);

  // Skip validation if no change
  if (currentType === newTypeKey) {
    return { valid: true };
  }

  // Skip validation for non-critical changes
  if (!requiresValidation(currentType, newTypeKey)) {
    return { valid: true };
  }

  // Check for warnings instead of blocking
  const warnings = this.getValidationWarnings(element, newTypeKey, currentType, translate);
  
  if (warnings.length > 0) {
    return {
      valid: true, // Allow the change
      warning: warnings[0].message, // Show first warning
      severity: warnings[0].severity,
      allWarnings: warnings // Include all warnings for detailed display
    };
  }

  return { valid: true };
};

/**
 * Get all validation warnings for a type change
 * @param {Object} element - BPMN element
 * @param {string} newTypeKey - Target task type
 * @param {string} currentType - Current task type
 * @param {Function} translate - Translation function
 * @returns {Array} Array of warning objects
 */
ValidationService.prototype.getValidationWarnings = function(element, newTypeKey, currentType, translate) {
  const warnings = [];
  const config = getTaskConfig(newTypeKey);

  for (const rule of config.validationRules) {
    const warning = this.applyValidationWarning(element, rule, translate, currentType, newTypeKey);
    if (warning) warnings.push(warning);
  }

  if (currentType === TASK_TYPE_KEYS.BINDING && newTypeKey !== TASK_TYPE_KEYS.BINDING) {
    const bindingWarning = this.getBindingChangeWarning(element, translate);
    if (bindingWarning) warnings.push(bindingWarning);
  }

  return warnings;
};

/**
 * Apply a specific validation rule as a warning
 * @param {Object} element - BPMN element
 * @param {string} rule - Validation rule name
 * @param {Function} translate - Translation function
 * @param {string} currentType - Current task type
 * @param {string} newTypeKey - Target task type
 * @returns {Object|null} Warning object or null
 */
ValidationService.prototype.applyValidationWarning = function(element, rule, translate, currentType, newTypeKey) {
  switch (rule) {
    case "requiresUpstreamBinding":
      return this.getUpstreamBindingWarning(element, translate);
    
    case "noDownstreamUnbinding":
      return this.getDownstreamUnbindingWarning(element, translate, currentType, newTypeKey);
    
    default:
      return null;
  }
};

/**
 * Get warning about missing upstream binding
 * @param {Object} element - BPMN element
 * @param {Function} translate - Translation function
 * @returns {Object|null} Warning object or null
 */
ValidationService.prototype.getUpstreamBindingWarning = function(element, translate) {
  const upstreamBindings = this.findUpstreamBindingTasks(element);
  
  if (upstreamBindings.length === 0) {
    return {
      type: "missing_upstream_binding",
      severity: "warning",
      message: translate("No preceding Binding task found in the same pool. Consider adding a Binding task before this Unbinding."),
      suggestion: translate("Add a Binding task earlier in the flow to establish what should be unbound.")
    };
  }
  
  return null;
};

/**
 * Get warning about downstream unbinding tasks
 * @param {Object} element - BPMN element
 * @param {Function} translate - Translation function
 * @param {string} currentType - Current task type
 * @param {string} newTypeKey - Target task type
 * @returns {Object|null} Warning object or null
 */
ValidationService.prototype.getDownstreamUnbindingWarning = function(element, translate, currentType, newTypeKey) {
  // Only warn when changing FROM binding to non-binding
  if (currentType === TASK_TYPE_KEYS.BINDING && newTypeKey !== TASK_TYPE_KEYS.BINDING) {
    const dependentUnbindings = this.findDependentUnbindingTasks(element);
    
    if (dependentUnbindings.length > 0) {
      const taskNames = dependentUnbindings
        .map(task => task.businessObject.name || task.id)
        .slice(0, 3)
        .join(", ");
      
      const suffix = dependentUnbindings.length > 3 ? "..." : "";
      
      return {
        type: "orphaned_unbinding",
        severity: "warning", 
        message: translate(`This change will leave Unbinding tasks without a corresponding Binding: ${taskNames}${suffix}`),
        suggestion: translate("Consider changing those Unbinding tasks to a different type or keeping this as a Binding task."),
        affectedTasks: dependentUnbindings
      };
    }
  }
  
  return null;
};

/**
 * Get warning when changing from binding type
 * @param {Object} element - BPMN element
 * @param {Function} translate - Translation function
 * @returns {Object|null} Warning object or null
 */
ValidationService.prototype.getBindingChangeWarning = function(element, translate) {
  const dependentUnbindings = this.findDependentUnbindingTasks(element);
  
  if (dependentUnbindings.length > 0) {
    const taskInfo = dependentUnbindings
      .map(task => `"${task.businessObject.name || task.id}"`)
      .join(", ");
      
    return {
      type: "binding_change_impact",
      severity: "info",
      message: translate(`Changing this Binding will affect these Unbinding tasks: ${taskInfo}`),
      suggestion: translate("Make sure this change aligns with your process design."),
      affectedTasks: dependentUnbindings
    };
  }
  
  return null;
};

/**
 * Quick validation check for UI feedback (now returns warning info)
 * @param {Object} element - BPMN element
 * @param {string} newTypeKey - Target task type
 * @returns {Object} {valid: boolean, hasWarnings: boolean, warningCount: number}
 */
ValidationService.prototype.quickValidationCheck = function(element, newTypeKey) {
  const currentType = this.extensionService.getCurrentType(element);
  
  // Same type is always valid with no warnings
  if (currentType === newTypeKey) return { valid: true, hasWarnings: false, warningCount: 0 };
  
  // Non-critical changes are valid with no warnings
  if (!requiresValidation(currentType, newTypeKey)) return { valid: true, hasWarnings: false, warningCount: 0 };
  
  // Check for warnings
  const warnings = this.getValidationWarnings(element, newTypeKey, currentType, (msg) => msg);
  
  return {
    valid: true, // Always allow the change
    hasWarnings: warnings.length > 0,
    warningCount: warnings.length,
    warnings: warnings
  };
};

// Keep all the existing helper methods (findUpstreamBindingTasks, etc.) unchanged
ValidationService.prototype.findUpstreamBindingTasks = function(element) {
  const pool = this.getContainingParticipant(element);
  if (!pool) return [];

  const bindingTasks = [];
  const visited = new Set();
  const stack = [element];

  while (stack.length) {
    const node = stack.pop();
    if (!node || visited.has(node.id)) continue;
    visited.add(node.id);

    const incoming = (node.incoming || []).filter(c => c.type === "bpmn:SequenceFlow");
    
    for (const flow of incoming) {
      const source = flow.source;
      if (!source || !this.isDescendantOf(source, pool)) continue;

      if (source.type === "bpmn:Task") {
        const sourceType = this.extensionService.getCurrentType(source);
        if (sourceType === TASK_TYPE_KEYS.BINDING) {
          bindingTasks.push(source);
        }
      }
      
      stack.push(source);
    }
  }

  return bindingTasks;
};

// Searching for all the dependent unbinding task, if a binding task exists
ValidationService.prototype.findDependentUnbindingTasks = function(bindingElement) {
  const pool = this.getContainingParticipant(bindingElement);
  if (!pool) return [];

  const dependentTasks = [];
  const visited = new Set();
  const stack = [bindingElement];

  while (stack.length) {
    const node = stack.pop();
    if (!node || visited.has(node.id)) continue;
    visited.add(node.id);
    const outgoing = (node.outgoing || []).filter(c => c.type === "bpmn:SequenceFlow");
    for (const flow of outgoing) {
      const target = flow.target;
      if (!target || !this.isDescendantOf(target, pool)) continue;

      if (target.type === "bpmn:Task") {
        const targetType = this.extensionService.getCurrentType(target);
        if (targetType === TASK_TYPE_KEYS.UNBINDING) {
          dependentTasks.push(target);
        }
      }
      stack.push(target);
    }
  }

  return dependentTasks;
};
ValidationService.prototype.getContainingParticipant = function(element) {
  const process = element?.businessObject?.$parent;
  if (!process?.id) return null;

  return this.elementRegistry.getAll().find(e => {
    if (e.type !== "bpmn:Participant") return false;
    const processRef = e.businessObject?.processRef;
    return processRef?.id === process.id;
  });
};

ValidationService.prototype.isDescendantOf = function(child, ancestor) {
  let current = child;
  while (current) {
    if (current === ancestor) return true;
    current = current.parent;
  }
  return false;
};