import {
  registerBpmnJSPlugin,
  registerBpmnJSModdleExtension
} from 'camunda-modeler-plugin-helpers';

import spaceModdle from './space-moddle.json';
import bpenvModeler from 'bpenv-modeler';

// import TypedPaletteProvider from './modules/TypedPaletteProvider';
import TypedOverlay from './modules/TypedOverlay';
import MovementContextPadProvider from './modules/MovementContextPadProvider';
import SpacePropertiesProvider from './modules/SpacePropertiesProvider';
import ServicesModule from './services/ServicesModule';
import SimpleBindingHandler from './modules/SimpleBindingHandler';
import { SimpleBindingService } from './services/SimpleBindingService';
import BindingFlowRenderer from './modules/BindingFlowRenderer';

import CustomReplaceProvider from './modules/CustomReplaceProvider';

const LibrariesModule = {
  __init__: [],
  bpenvModeler: [ 'value', bpenvModeler ]
};


// Create simple binding module bundle
const SimpleBindingModule = {
  __init__: [ 'simpleBindingHandler', 'simpleBindingService' ],
  simpleBindingHandler: [ 'type', SimpleBindingHandler ],
  simpleBindingService: [ 'type', SimpleBindingService ]
};

// Register moddle extension
registerBpmnJSModdleExtension(spaceModdle);

registerBpmnJSPlugin(LibrariesModule);
registerBpmnJSPlugin(ServicesModule);
registerBpmnJSPlugin(TypedOverlay);
registerBpmnJSPlugin(MovementContextPadProvider);
registerBpmnJSPlugin(SpacePropertiesProvider);
registerBpmnJSPlugin(SimpleBindingModule);
registerBpmnJSPlugin(BindingFlowRenderer);
registerBpmnJSPlugin(CustomReplaceProvider);