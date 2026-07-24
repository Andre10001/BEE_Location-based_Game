import { getAllTaskTypes, getTaskConfig } from './TaskTypes';

function TypedPaletteProvider(palette, create, elementFactory, translate, taskTypeService) {
  this._create = create;
  this._elementFactory = elementFactory;
  this._translate = translate;
  this._taskTypeService = taskTypeService;
  
  palette.registerProvider(this);
}

TypedPaletteProvider.$inject = ["palette", "create", "elementFactory", "translate", "taskTypeService"];

TypedPaletteProvider.prototype.getPaletteEntries = function() {
  const { _create: create, _elementFactory: elementFactory, _translate: t } = this;

  const createTaskForType = (event, typeKey) => {
    const config = getTaskConfig(typeKey);
    if (!config) return;

    const shape = elementFactory.createShape({ type: "bpmn:Task" });
    shape.businessObject.name = t(config.displayName);

    // Use TaskTypeService to set up the task properly
    // Note: This will be called after the shape is created in the modeler
    setTimeout(() => {
      this._taskTypeService.setTaskType(shape, typeKey);
    }, 0);

    create.start(event, shape);
  };

  const entries = {};
  getAllTaskTypes().forEach(config => {
    entries[`${config.key}.task`] = {
      group: "activity",
      className: `bpmn-icon-task ${config.icon.class}`,
      title: t(`Create ${config.typeValue.toUpperCase()} Task`),
      action: {
        dragstart: (e) => createTaskForType(e, config.key),
        click: (e) => createTaskForType(e, config.key)
      }
    };
  });

  return entries;
};

export default {
  __init__: ["typedPaletteProvider"],
  typedPaletteProvider: ["type", TypedPaletteProvider]
};