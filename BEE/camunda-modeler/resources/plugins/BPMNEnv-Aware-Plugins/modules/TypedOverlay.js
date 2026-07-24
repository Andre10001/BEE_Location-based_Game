import { getAllTaskTypes, getTaskConfig } from './TaskTypes';

function TypedOverlay(eventBus, overlays, elementRegistry, extensionService) {
  this._overlays = overlays;
  this._registry = elementRegistry;
  this._extensionService = extensionService;

  const updateBadge = (element) => {
    if (element?.type !== "bpmn:Task") return;

    // Remove existing badges
    getAllTaskTypes().forEach(config => {
      const existing = this._overlays.get({ element, type: config.icon.class }) || [];
      existing.forEach(o => this._overlays.remove(o.id));
    });

    const currentType = this._extensionService.getCurrentType(element);
    const config = getTaskConfig(currentType);
    
    if (config) {
      this._overlays.add(element, config.icon.class, {
        position: { top: 0, left: 0 },
        html: `<div class="${config.icon.class}" title="${config.typeValue}"></div>`,
        scale: true
      });
    }
  };

  eventBus.on("import.render.complete", () => {
    this._registry.getAll().forEach(updateBadge);
  });
  
  eventBus.on("shape.added", ({ element }) => updateBadge(element));
  eventBus.on("shape.changed", ({ element }) => updateBadge(element));
  eventBus.on("elements.changed", ({ elements }) => elements.forEach(updateBadge));
}

TypedOverlay.$inject = ["eventBus", "overlays", "elementRegistry", "extensionService"];

export default {
  __init__: ["typedOverlay"],
  typedOverlay: ["type", TypedOverlay]
};
