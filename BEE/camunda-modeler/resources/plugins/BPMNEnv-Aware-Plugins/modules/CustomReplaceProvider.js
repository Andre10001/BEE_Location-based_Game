import { getAllTaskTypes, getTaskConfig, TASK_TYPE_KEYS } from '../modules/TaskTypes';

/**
 * Custom Replace Provider - Adds Movement, Binding, Unbinding to the built-in replace menu
 */
class CustomReplaceProvider {
  constructor(popupMenu, translate, taskTypeService, extensionService, eventBus, modeling) {
    this._popupMenu = popupMenu;
    this._translate = translate;
    this._taskTypeService = taskTypeService;
    this._extensionService = extensionService;
    this._eventBus = eventBus;
    this._modeling = modeling;

    // Register with the replace menu
    popupMenu.registerProvider('bpmn-replace', this);
  }

  /**
   * Get entries for the replace menu
   * @param {Object} element - BPMN element
   * @returns {Array} Menu entries including our custom types
   */
  getEntries(element) {
    // Only add entries for tasks
    if (!this._isTask(element)) {
      return [];
    }

    const entries = [];
    const translate = this._translate;
    const currentType = this._extensionService.getCurrentType(element);
    
    // If the task has a custom type, add option to revert to regular Task
    if (currentType) {
      entries.push(this._createRegularTaskEntry(element, translate));
    }
    
    // Add our custom task types to the replace menu
    getAllTaskTypes().forEach(config => {
      const entry = this._createMenuEntry(element, config, translate, currentType);
      if (entry) {
        entries.push(entry);
      }
    });

    return entries;
  }

  /**
   * Get header entries (for the icon bar at the top)
   * We don't need header entries for task types
   */
  getHeaderEntries(element) {
    return [];
  }

  /**
   * Create entry for regular BPMN Task (no custom type)
   */
  _createRegularTaskEntry(element, translate) {
    return {
      id: 'replace-with-regular-task',
      label: translate('Task'),
      className: 'bpmn-icon-task',
      action: () => {
        this._clearCustomType(element);
      },
      group: {
        id: 'space-tasks',
        name: translate('Space Tasks')
      },
      rank: 1 // Position at the top of our group
    };
  }

  /**
   * Create a menu entry for a custom task type
   */
  _createMenuEntry(element, config, translate, currentType) {
    // Don't show entry for current type
    if (currentType === config.key) {
      return null;
    }

    return {
      id: `replace-with-${config.key}`,
      label: translate(config.displayName),
      className: `bpmn-icon-task space-task-${config.key}`,
      imageUrl: config.icon.iconFile, // This will show the SVG icon
      action: () => {
        this._replaceElement(element, config.key);
      },
      group: {
        id: 'space-tasks',
        name: translate('Space Tasks')
      },
      rank: 100 + getAllTaskTypes().indexOf(config) // Order within group
    };
  }

  /**
   * Clear custom type from element
   */
  _clearCustomType(element) {
    try {
      // Clear all custom extensions
      this._taskTypeService.clearTaskType(element);
      
      // Close the popup
      this._popupMenu.close();
      
      // Fire change event
      this._eventBus.fire('elements.changed', { 
        elements: [element] 
      });
      
    } catch (error) {
      console.error('Failed to clear task type:', error);
    }
  }

  /**
   * Replace element with new task type
   */
  _replaceElement(element, newType) {
    try {
      // Use the task type service to handle the type change
      this._taskTypeService.setTaskType(element, newType);
      
      // Close the popup
      this._popupMenu.close();
      
      // Fire change event
      this._eventBus.fire('elements.changed', { 
        elements: [element] 
      });
      
    } catch (error) {
      console.error('Failed to change task type:', error);
    }
  }

  /**
   * Check if element is a task
   */
  _isTask(element) {
    return element && element.type === 'bpmn:Task';
  }
}

CustomReplaceProvider.$inject = [
  'popupMenu',
  'translate', 
  'taskTypeService',
  'extensionService',
  'eventBus',
  'modeling'
];

export default {
  __init__: ['customReplaceProvider'],
  customReplaceProvider: ['type', CustomReplaceProvider]
};