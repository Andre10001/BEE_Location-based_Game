import { EXTENSION_TYPES } from '../modules/TaskTypes';

export function AssignmentService(extensionService, modeling, bpmnFactory) {
  this.extensionService = extensionService;
  this.modeling = modeling;
  this.bpmnFactory = bpmnFactory;
}

AssignmentService.$inject = [ 'extensionService', 'modeling', 'bpmnFactory' ];

/**
 * Get all assignments for a task element
 */
AssignmentService.prototype.getAssignments = function(element) {
  if (!element?.businessObject) return [];

  const assignments = [];
  const extensions = this.extensionService.getExtensionValues(element.businessObject);

  // Collect all assignment conditions and values
  const conditions = extensions.filter(ext => ext.$type === EXTENSION_TYPES.TASK_ASSIGNMENT);
  const values = extensions.filter(ext => ext.$type === EXTENSION_TYPES.TASK_ASSIGNMENT_REACHED);

  // Pair them up based on order
  const maxLength = Math.max(conditions.length, values.length);
  for (let i = 0; i < maxLength; i++) {
    assignments.push({
      id: `assignment_${i}`,
      condition: conditions[i] ? this.extensionService.getText(conditions[i]) : '',
      value: values[i] ? this.extensionService.getText(values[i]) : ''
    });
  }

  return assignments;
};

/**
 * Add a new assignment to a task
 */
AssignmentService.prototype.addAssignment = function(element, condition, value) {
  try {
    const bo = element.businessObject;

    // Ensure extension elements exist
    this.extensionService.ensureExtensionElements(element, bo);

    // Create new extension elements for this assignment
    const conditionExt = this.bpmnFactory.create(EXTENSION_TYPES.TASK_ASSIGNMENT, {
      body: condition || ''
    });

    const valueExt = this.bpmnFactory.create(EXTENSION_TYPES.TASK_ASSIGNMENT_REACHED, {
      body: value || ''
    });

    // Add them to the extension elements
    const currentValues = [ ...(bo.extensionElements.values || []) ];
    currentValues.push(conditionExt);
    currentValues.push(valueExt);

    // Update the model
    this.modeling.updateModdleProperties(element, bo.extensionElements, {
      values: currentValues
    });

    console.log('Assignment added successfully');
  } catch (error) {
    console.error('Error adding assignment:', error);
  }
};

/**
 * Update an existing assignment
 */
AssignmentService.prototype.updateAssignment = function(element, index, condition, value) {
  try {
    const bo = element.businessObject;
    const extensions = this.extensionService.getExtensionValues(bo);

    // Find all conditions and values
    const conditions = extensions.filter(ext => ext.$type === EXTENSION_TYPES.TASK_ASSIGNMENT);
    const values = extensions.filter(ext => ext.$type === EXTENSION_TYPES.TASK_ASSIGNMENT_REACHED);

    // Update the specific ones at the index
    if (conditions[index]) {
      this.modeling.updateModdleProperties(element, conditions[index], {
        body: condition || ''
      });
    }

    if (values[index]) {
      this.modeling.updateModdleProperties(element, values[index], {
        body: value || ''
      });
    }

  } catch (error) {
    console.error('Error updating assignment:', error);
  }
};

/**
 * Remove an assignment from a task
 */
AssignmentService.prototype.removeAssignment = function(element, index) {
  try {
    const bo = element.businessObject;
    const extensions = this.extensionService.getExtensionValues(bo);

    // Find all conditions and values
    const conditions = extensions.filter(ext => ext.$type === EXTENSION_TYPES.TASK_ASSIGNMENT);
    const values = extensions.filter(ext => ext.$type === EXTENSION_TYPES.TASK_ASSIGNMENT_REACHED);

    // Remove the ones at the specified index
    const toRemove = [];
    if (conditions[index]) toRemove.push(conditions[index]);
    if (values[index]) toRemove.push(values[index]);

    // Filter out the removed ones
    const newValues = extensions.filter(ext => !toRemove.includes(ext));

    // Update the model
    this.modeling.updateModdleProperties(element, bo.extensionElements, {
      values: newValues
    });

  } catch (error) {
    console.error('Error removing assignment:', error);
  }
};

/**
 * Clear all assignments from a task
 */
AssignmentService.prototype.clearAssignments = function(element) {
  this.extensionService.removeExtensions(element,
    v => v.$type === EXTENSION_TYPES.TASK_ASSIGNMENT ||
         v.$type === EXTENSION_TYPES.TASK_ASSIGNMENT_REACHED
  );
};

/**
 * Get assignment count for a task
 */
AssignmentService.prototype.getAssignmentCount = function(element) {
  try {
    const assignments = this.getAssignments(element);
    return assignments.length;
  } catch (error) {
    console.error('Error getting assignment count:', error);
    return 0;
  }
};