import { getTaskConfig, getAllTaskTypes, TASK_TYPE_KEYS, EXTENSION_TYPES, CONNECTION_MODE } from './TaskTypes';

function SpacePropertiesProvider(
    eventBus,
    translate,
    extensionService,
    taskTypeService,
    environmentService,
    messageFlowXmlService,
    elementRegistry,
    assignmentService,
    modeling,
    bpmnFactory
) {
  this._eventBus = eventBus;
  this._translate = translate;
  this._extensionService = extensionService;
  this._taskTypeService = taskTypeService;
  this._environmentService = environmentService;
  this._messageFlowXmlService = messageFlowXmlService;
  this._elementRegistry = elementRegistry;
  this._assignmentService = assignmentService;
  this._modeling = modeling;
  this._bpmnFactory = bpmnFactory;

  console.info('SpacePropertiesProvider initialized');

  const isSupportedSpaceElement = (element) => {
    return element && (
      element.type === 'bpmn:Task' ||
      element.type === 'bpmn:SendTask' ||
      element.type === 'bpmn:StartEvent'
    );
  };

  eventBus.on('selection.changed', (event) => {
    if (event.newSelection && event.newSelection.length === 1) {
      const element = event.newSelection[0];

      if (isSupportedSpaceElement(element)) {
        setTimeout(() => this.createStandaloneSpaceSection(element), 200);
      } else if (element.type === 'bpmn:MessageFlow') {

        // Show binding info for connections
        setTimeout(() => this.createMessageFlowSpaceSection(element), 200);
      } else if (element.type === 'bpmn:SequenceFlow') {

        // Show guard properties for sequence flows
        setTimeout(() => this.createSequenceFlowSpaceSection(element), 200);
      } else {
        setTimeout(() => this.showEnvironmentSection(), 200);
      }
    } else if (event.newSelection && event.newSelection.length === 0) {

      // No selection - show environment configuration section
      setTimeout(() => this.showEnvironmentSection(), 200);
    }

    // Do nothing for multiple selections
  });

  // Listen for model changes to refresh the UI
  eventBus.on('elements.changed', (event) => {
    if (event.elements && event.elements.length > 0) {
      const element = event.elements[0];
      if (isSupportedSpaceElement(element)) {
        setTimeout(() => this.refreshSpaceSection(element), 100);
      }
    }
  });

  // Listen for environment changes
  eventBus.on('environment.ready', () => {
    this.refreshEnvironmentSection();
  });

  eventBus.on('environment.cleared', () => {
    this.refreshEnvironmentSection();
  });

  // Listen for manual file load results
  eventBus.on('environment.manual.loaded', (event) => {
    this.handleManualLoadResult(event);
  });
}

SpacePropertiesProvider.$inject = [
  'eventBus',
  'translate',
  'extensionService',
  'taskTypeService',
  'environmentService',
  'messageFlowXmlService',
  'elementRegistry',
  'assignmentService',
  'modeling',
  'bpmnFactory'
];

/**
 * Create Space Properties section for message flows
 */
SpacePropertiesProvider.prototype.createMessageFlowSpaceSection = function(messageFlow) {
  const propertiesPanel = document.querySelector('.bio-properties-panel-scroll-container');
  if (!propertiesPanel) {
    console.error('Properties panel scroll container not found');
    return;
  }

  // Remove existing space sections
  const existingSection = propertiesPanel.querySelector('.space-properties-section');
  if (existingSection) {
    existingSection.remove();
  }

  // Get connection info using the XML service
  const connectionInfo = this._messageFlowXmlService.getConnectionInfo(messageFlow);

  // Create the space section for message flow
  const section = this.createMessageFlowSection(messageFlow, connectionInfo);

  // Insert after General section or at the beginning
  const generalSection = propertiesPanel.querySelector('[data-group-id*="general"]');
  if (generalSection && generalSection.nextSibling) {
    propertiesPanel.insertBefore(section, generalSection.nextSibling);
  } else {
    propertiesPanel.insertBefore(section, propertiesPanel.firstChild);
  }
};

/**
 * Create Space Properties section for sequence flows.
 * Exposes an editable space:Guard field, like environmental tasks.
 */
// SpacePropertiesProvider.prototype.createSequenceFlowSpaceSection = function(sequenceFlow) {
//   const propertiesPanel = document.querySelector('.bio-properties-panel-scroll-container');
//   if (!propertiesPanel) {
//     console.error('Properties panel scroll container not found');
//     return;
//   }

//   const existingSection = propertiesPanel.querySelector('.space-properties-section');
//   if (existingSection) {
//     existingSection.remove();
//   }

//   const section = this.createSequenceFlowSection(sequenceFlow);

//   const generalSection = propertiesPanel.querySelector('[data-group-id*="general"]');
//   if (generalSection && generalSection.nextSibling) {
//     propertiesPanel.insertBefore(section, generalSection.nextSibling);
//   } else {
//     propertiesPanel.insertBefore(section, propertiesPanel.firstChild);
//   }
// };

// SpacePropertiesProvider.prototype.createSequenceFlowSection = function(sequenceFlow) {
//   const section = document.createElement('div');
//   section.className = 'bio-properties-panel-group space-properties-section';
//   section.setAttribute('data-group-id', 'group-space-properties');

//   const translate = this._translate;
//   const guardValue = this._extensionService.getGuard(sequenceFlow) || '';
//   const isExpanded = true;
//   const hasData = !!guardValue.trim();

//   section.innerHTML = `
//     <div class="bio-properties-panel-group-header ${isExpanded ? 'open' : ''} ${hasData ? '' : 'empty'}">
//       <div title="Environmental Properties" 
//            data-title="Environmental Properties" 
//            class="bio-properties-panel-group-header-title">
//           Environmental Properties
//       </div>
//       <div class="bio-properties-panel-group-header-buttons">
//         ${hasData ? '<div title="Section contains data" class="bio-properties-panel-dot"></div>' : ''}
//         <button type="button" 
//                 title="Toggle section" 
//                 class="bio-properties-panel-group-header-button bio-properties-panel-arrow">
//           <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" class="${isExpanded ? 'bio-properties-panel-arrow-down' : 'bio-properties-panel-arrow-right'}">
//             <path fill-rule="evenodd" d="m11.657 8-4.95 4.95a1 1 0 0 1-1.414-1.414L8.828 8 5.293 4.464A1 1 0 1 1 6.707 3.05L11.657 8Z"></path>
//           </svg>
//         </button>
//       </div>
//     </div>

//     <div class="bio-properties-panel-group-entries ${isExpanded ? 'open' : ''}" style="${isExpanded ? '' : 'display: none;'}">
//       <div data-entry-id="space-sequenceflow-guard" class="bio-properties-panel-entry">
//         <div class="bio-properties-panel-textfield">
//           <label for="space-sequenceflow-guard-input" class="bio-properties-panel-label">Guard</label>
//           <input id="space-sequenceflow-guard-input"
//                  type="text"
//                  name="spaceSequenceFlowGuard"
//                  spellcheck="false"
//                  autocomplete="off"
//                  class="bio-properties-panel-input space-sequenceflow-guard-input"
//                  placeholder="${translate('Enter guard condition')}"
//                  value="${this.escapeHtml(guardValue)}" />
//         </div>
//       </div>
//     </div>
//   `;

//   this.attachSequenceFlowGuardListener(section, sequenceFlow);
//   return section;
// };

// SpacePropertiesProvider.prototype.attachSequenceFlowGuardListener = function(section, sequenceFlow) {
//   const guardInput = section.querySelector('.space-sequenceflow-guard-input');
//   if (!guardInput) {
//     return;
//   }

//   [ 'input', 'blur', 'change' ].forEach(eventType => {
//     guardInput.addEventListener(eventType, (e) => {
//       try {
//         const value = e.target.value.trim();
//         if (value) {
//           this._extensionService.setExtension(sequenceFlow, 'space:Guard', value);
//         }

//         this._eventBus.fire('elements.changed', { elements: [ sequenceFlow ] });
//       } catch (error) {
//         console.error('Error saving sequence flow guard:', error);
//       }
//     });
//   });
// };

/**
 * Create the Space Properties section for message flow.
 * For binding/unbinding: shows Connection Mode (Static/Dynamic) and conditional Leader dropdown.
 * For other connection types: shows legacy Type and Participant references.
 */
SpacePropertiesProvider.prototype.createMessageFlowSection = function(messageFlow, connectionInfo) {
  const section = document.createElement('div');
  section.className = 'bio-properties-panel-group space-properties-section';
  section.setAttribute('data-group-id', 'group-space-properties');

  const translate = this._translate;
  const hasData = !!connectionInfo;
  const isExpanded = hasData;
  const isBindingOrUnbinding = hasData &&
    (connectionInfo.type === TASK_TYPE_KEYS.BINDING || connectionInfo.type === TASK_TYPE_KEYS.UNBINDING);

  // Participant labels for Leader dropdown (from connected participants)
  const sourceParticipant = connectionInfo ? this._elementRegistry.get(connectionInfo.participant1) : null;
  const targetParticipant = connectionInfo ? this._elementRegistry.get(connectionInfo.participant2) : null;
  const sourceName = sourceParticipant?.businessObject?.name || connectionInfo?.participant1 || '';
  const targetName = targetParticipant?.businessObject?.name || connectionInfo?.participant2 || '';

  const connectionMode = (connectionInfo && this._messageFlowXmlService.getConnectionMode(messageFlow)) || CONNECTION_MODE.DYNAMIC;
  const leaderId = connectionInfo && this._messageFlowXmlService.getLeaderId(messageFlow);
  const showLeader = isBindingOrUnbinding && connectionMode === CONNECTION_MODE.STATIC;
  const p1 = connectionInfo?.participant1 || '';
  const p2 = connectionInfo?.participant2 || '';

  const entriesContent = isBindingOrUnbinding
    ? `
      <!-- Connection Mode Entry -->
      <div data-entry-id="space-connection-mode" class="bio-properties-panel-entry">
        <div class="bio-properties-panel-textfield">
          <label for="connection-mode-select" class="bio-properties-panel-label">${translate('Connection Mode')}</label>
          <select id="connection-mode-select" 
                  name="connectionMode" 
                  class="bio-properties-panel-input connection-mode-select">
            <option value="${CONNECTION_MODE.DYNAMIC}" ${connectionMode === CONNECTION_MODE.DYNAMIC ? 'selected' : ''}>${translate('Dynamic')}</option>
            <option value="${CONNECTION_MODE.STATIC}" ${connectionMode === CONNECTION_MODE.STATIC ? 'selected' : ''}>${translate('Static')}</option>
          </select>
        </div>
      </div>

      <!-- Leader Entry (only when Static) -->
      <div data-entry-id="space-leader" class="bio-properties-panel-entry space-leader-entry" style="${showLeader ? '' : 'display: none;'}">
        <div class="bio-properties-panel-textfield">
          <label for="leader-select" class="bio-properties-panel-label">${translate('Leader')}</label>
          <select id="leader-select" 
                  name="leaderId" 
                  class="bio-properties-panel-input leader-select">
            <option value="">${translate('(None)')}</option>
            ${p1 ? `<option value="${this.escapeHtml(p1)}" ${leaderId === p1 ? 'selected' : ''}>${this.escapeHtml(sourceName || p1)}</option>` : ''}
            ${p2 ? `<option value="${this.escapeHtml(p2)}" ${leaderId === p2 ? 'selected' : ''}>${this.escapeHtml(targetName || p2)}</option>` : ''}
          </select>
        </div>
      </div>
    `
    : `
      <!-- Legacy: Connection Type Entry -->
      <div data-entry-id="space-connection-type" class="bio-properties-panel-entry">
        <div class="bio-properties-panel-textfield">
          <label class="bio-properties-panel-label">${translate('Type')}</label>
          <input type="text" 
                 class="bio-properties-panel-input" 
                 value="${connectionInfo ? connectionInfo.type : ''}" 
                 readonly
                 style="background: #f8f9fa; cursor: default;" />
        </div>
      </div>
      <div data-entry-id="space-source-ref" class="bio-properties-panel-entry">
        <div class="bio-properties-panel-textfield">
          <label class="bio-properties-panel-label">${translate('Participant 1 reference')}</label>
          <input type="text" 
                 class="bio-properties-panel-input" 
                 value="${connectionInfo?.participant1 || ''}" 
                 readonly
                 title="${sourceName}"
                 style="background: #f8f9fa; cursor: default;" />
        </div>
      </div>
      <div data-entry-id="space-target-ref" class="bio-properties-panel-entry">
        <div class="bio-properties-panel-textfield">
          <label class="bio-properties-panel-label">${translate('Participant 2 reference')}</label>
          <input type="text" 
                 class="bio-properties-panel-input" 
                 value="${connectionInfo?.participant2 || ''}" 
                 readonly
                 title="${targetName}"
                 style="background: #f8f9fa; cursor: default;" />
        </div>
      </div>
    `;

  section.innerHTML = `
  ${hasData ? `
    <div class="bio-properties-panel-group-header ${isExpanded ? 'open' : ''}">
      <div title="Environmental Properties" 
           data-title="Environmental Properties" 
           class="bio-properties-panel-group-header-title">
        Environmental Properties 
      </div>
      <div class="bio-properties-panel-group-header-buttons">
        <div title="Section contains data" class="bio-properties-panel-dot"></div>
        <button type="button" 
                title="Toggle section" 
                class="bio-properties-panel-group-header-button bio-properties-panel-arrow">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" 
               class="${isExpanded ? 'bio-properties-panel-arrow-down' : 'bio-properties-panel-arrow-right'}">
            <path fill-rule="evenodd" 
                  d="m11.657 8-4.95 4.95a1 1 0 0 1-1.414-1.414L8.828 8 5.293 4.464A1 1 0 1 1 6.707 3.05L11.657 8Z">
            </path>
          </svg>
        </button>
      </div>
    </div>

    <div class="bio-properties-panel-group-entries ${isExpanded ? 'open' : ''}" style="${isExpanded ? '' : 'display: none;'}">
      ${entriesContent}
    </div>
  ` : ''}`;

  this.attachSectionEventListeners(section, messageFlow);
  if (isBindingOrUnbinding) {
    this.attachMessageFlowConnectionListeners(section, messageFlow);
  }

  return section;
};

/**
 * Attach listeners for Connection Mode and Leader on binding/unbinding message flows.
 * Persists to extensionElements and triggers renderer update via elements.changed.
 */
SpacePropertiesProvider.prototype.attachMessageFlowConnectionListeners = function(section, messageFlow) {
  const connectionModeSelect = section.querySelector('.connection-mode-select');
  const leaderSelect = section.querySelector('.leader-select');
  const leaderEntry = section.querySelector('.space-leader-entry');

  if (connectionModeSelect) {
    connectionModeSelect.addEventListener('change', (e) => {
      const mode = e.target.value;
      this.setMessageFlowConnectionMode(messageFlow, mode);
      if (leaderEntry) {
        leaderEntry.style.display = mode === CONNECTION_MODE.STATIC ? 'block' : 'none';
      }
      if (mode === CONNECTION_MODE.DYNAMIC) {
        this.setMessageFlowLeaderId(messageFlow, '');
      }
      this._eventBus.fire('elements.changed', { elements: [ messageFlow ] });
    });
  }

  if (leaderSelect) {
    leaderSelect.addEventListener('change', (e) => {
      const leaderId = e.target.value || '';
      this.setMessageFlowLeaderId(messageFlow, leaderId);
      this._eventBus.fire('elements.changed', { elements: [ messageFlow ] });
    });
  }
};

/**
 * Ensure extensionElements on message flow and set or update a single extension by type.
 */
SpacePropertiesProvider.prototype.ensureMessageFlowExtension = function(messageFlow, type, value) {
  const bo = messageFlow.businessObject;
  if (!bo.extensionElements) {
    bo.extensionElements = this._bpmnFactory.create('bpmn:ExtensionElements', { values: [] });
  }
  const values = bo.extensionElements.values || [];
  const existing = values.find(v => v.$type === type);
  if (existing) {
    this._modeling.updateModdleProperties(messageFlow, existing, { body: value });
  } else {
    const el = this._bpmnFactory.create(type, { body: value });
    const newValues = [ ...values, el ];
    this._modeling.updateModdleProperties(messageFlow, bo.extensionElements, { values: newValues });
  }
};

/**
 * Remove one extension element type from message flow.
 */
SpacePropertiesProvider.prototype.removeMessageFlowExtension = function(messageFlow, type) {
  const bo = messageFlow.businessObject;
  if (!bo.extensionElements?.values) return;
  const values = bo.extensionElements.values.filter(v => v.$type !== type);
  this._modeling.updateModdleProperties(messageFlow, bo.extensionElements, { values });
};

SpacePropertiesProvider.prototype.setMessageFlowConnectionMode = function(messageFlow, mode) {
  this.ensureMessageFlowExtension(messageFlow, EXTENSION_TYPES.CONNECTION_MODE, mode);
};

SpacePropertiesProvider.prototype.setMessageFlowLeaderId = function(messageFlow, leaderId) {
  if (!leaderId) {
    this.removeMessageFlowExtension(messageFlow, EXTENSION_TYPES.LEADER_ID);
  } else {
    this.ensureMessageFlowExtension(messageFlow, EXTENSION_TYPES.LEADER_ID, leaderId);
  }
};

/**
 * Show environment configuration section when no task is selected
 */
SpacePropertiesProvider.prototype.showEnvironmentSection = function() {
  const propertiesPanel = document.querySelector('.bio-properties-panel-scroll-container');
  if (!propertiesPanel) {
    console.error('Properties panel scroll container not found');
    return;
  }

  // Check if environment section already exists
  const existingEnvSection = propertiesPanel.querySelector('.space-properties-section[data-group-id="group-environment-config"]');
  if (existingEnvSection) {
    return;
  }

  // Remove any other space sections (task-related)
  const existingSpaceSection = propertiesPanel.querySelector('.space-properties-section');
  if (existingSpaceSection) {
    existingSpaceSection.remove();
  }

  // Create the environment configuration section
  const envSection = this.createEnvironmentSection();

  // Insert at the beginning
  propertiesPanel.insertBefore(envSection, propertiesPanel.firstChild);
};

SpacePropertiesProvider.prototype.createEnvironmentSection = function() {
  const section = document.createElement('div');
  section.className = 'bio-properties-panel-group space-properties-section';
  section.setAttribute('data-group-id', 'group-environment-config');

  const translate = this._translate;
  const hasConfig = this._environmentService.hasConfiguration();
  const configSummary = hasConfig ? this._environmentService.getConfigSummary() : null;

  const isExpanded = hasConfig;

  section.innerHTML = `
    <div class="bio-properties-panel-group-header ${isExpanded ? 'open' : ''} ${hasConfig ? '' : 'empty'}">
      <div title="Environment Configuration" data-title="Environment Configuration" class="bio-properties-panel-group-header-title">
          Environment Configuration
      </div>
      <div class="bio-properties-panel-group-header-buttons">
        ${hasConfig ? '<div title="Environment loaded" class="bio-properties-panel-dot"></div>' : ''}
        <button type="button" title="Toggle section" class="bio-properties-panel-group-header-button bio-properties-panel-arrow">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" class="${isExpanded ? 'bio-properties-panel-arrow-down' : 'bio-properties-panel-arrow-right'}">
            <path fill-rule="evenodd" d="m11.657 8-4.95 4.95a1 1 0 0 1-1.414-1.414L8.828 8 5.293 4.464A1 1 0 1 1 6.707 3.05L11.657 8Z"></path>
          </svg>
        </button>
      </div>
    </div>

    <div class="bio-properties-panel-group-entries ${isExpanded ? 'open' : ''}" style="${isExpanded ? '' : 'display: none;'}">
      
      <!-- ALWAYS SHOW - Load button for new physical places -->
      <div data-entry-id="env-file-upload" class="bio-properties-panel-entry">
        <div class="bio-properties-panel-textfield">
          <label class="bio-properties-panel-label">${translate('Environment File')}</label>
          <div class="env-file-input-container">
            <button type="button" class="env-file-load-btn bio-properties-panel-input" 
                    style="text-align: left; cursor: pointer; display: flex; align-items: center; gap: 8px;">
              <span class="file-text">${translate('Load environment.json')}</span>
            </button>
            <input type="file" accept=".json" class="env-file-input" style="display: none;" />
          </div>
          <small class="bio-properties-panel-description">
            ${translate('Load environment.json to add physical places to the modeler')}
          </small>
        </div>
      </div>

      ${hasConfig ? this.renderConfigurationDetails(configSummary) : ''}
       
      ${hasConfig ? `
      <div data-entry-id="env-clear-config" class="bio-properties-panel-entry">
        <div class="bio-properties-panel-textfield">
          <button type="button" class="env-clear-config-btn bio-properties-panel-input" 
                  style="text-align: center; cursor: pointer; color: #d32f2f; border-color: #d32f2f;">
            ${translate('Clear Configuration')}
          </button>
        </div>
      </div>
      ` : ''}
      
    </div>
  `;

  this.attachEnvironmentEventListeners(section);
  return section;
};

/**
 * Render configuration details section
 */
SpacePropertiesProvider.prototype.renderConfigurationDetails = function(configSummary) {
  const translate = this._translate;
  console.log('CONFIG SUMMARY:', configSummary.summary);
  return `
    <!-- Configuration Details Entry -->
    <div data-entry-id="env-config-details" class="bio-properties-panel-entry">
      <div class="bio-properties-panel-textfield">
        <label class="bio-properties-panel-label">${translate('Configuration Summary')}</label>
        <div class="env-config-details">
          <div class="config-metric">
            <span class="metric-label">${translate('Places')}:</span>
            <span class="metric-value">${configSummary.summary.physicalPlaces}</span>
          </div>
          <div class="config-metric">
            <span class="metric-label">${translate('Logical Places')}:</span>
            <span class="metric-value">${configSummary.summary.logicalPlaces}</span>
          </div>
          <div class="config-metric">
            <span class="metric-label">${translate('Views')}:</span>
            <span class="metric-value">${configSummary.summary.views}</span>
          </div>
          ${configSummary.zones.length > 0 ? `
          <div class="config-metric">
            <span class="metric-label">${translate('Zones')}:</span>
            <span class="metric-value">${configSummary.zones.join(', ')}</span>
          </div>
          ` : ''}
          ${configSummary.purposes.length > 0 ? `
          <div class="config-metric">
            <span class="metric-label">${translate('Purposes')}:</span>
            <span class="metric-value">${configSummary.purposes.join(', ')}</span>
          </div>
          ` : ''}
        </div>
      </div>
    </div>
  `;
};

/**
 * Attach event listeners to environment section
 */
SpacePropertiesProvider.prototype.attachEnvironmentEventListeners = function(section) {

  // Toggle section expand/collapse
  const toggleButton = section.querySelector('.bio-properties-panel-group-header-button');
  const header = section.querySelector('.bio-properties-panel-group-header');
  const entries = section.querySelector('.bio-properties-panel-group-entries');

  if (toggleButton && header && entries) {
    toggleButton.addEventListener('click', () => {
      const isOpen = header.classList.contains('open');

      if (isOpen) {

        // Close section
        header.classList.remove('open');
        entries.classList.remove('open');
        entries.style.display = 'none';

        const arrow = toggleButton.querySelector('svg');
        if (arrow) {
          arrow.classList.remove('bio-properties-panel-arrow-down');
          arrow.classList.add('bio-properties-panel-arrow-right');
        }
      } else {

        // Open section
        header.classList.add('open');
        entries.classList.add('open');
        entries.style.display = 'block';

        const arrow = toggleButton.querySelector('svg');
        if (arrow) {
          arrow.classList.remove('bio-properties-panel-arrow-right');
          arrow.classList.add('bio-properties-panel-arrow-down');
        }
      }
    });
  }

  // File input handling
  const fileButton = section.querySelector('.env-file-load-btn');
  const fileInput = section.querySelector('.env-file-input');

  if (fileButton && fileInput) {
    fileButton.addEventListener('click', () => {
      fileInput.click();
    });

    fileInput.addEventListener('change', (e) => {
      const file = e.target.files[0];
      if (file) {
        this.handleFileLoad(file, section);
      }
    });
  }

  // Clear configuration button
  const clearButton = section.querySelector('.env-clear-config-btn');
  if (clearButton) {
    clearButton.addEventListener('click', () => {
      this.handleClearConfiguration(section);
    });
  }
};

/**
 * Handle file loading (manual upload)
 */
SpacePropertiesProvider.prototype.handleFileLoad = function(file, section) {

  // Validate file type
  if (!file.name.toLowerCase().endsWith('.json')) {
    this.showFileError(section, this._translate('Please select a JSON file'));
    return;
  }

  // Show loading state
  this.showFileLoading(section, file.name);

  // Use environment service to handle the file
  this._environmentService.handleManualFileLoad(file);
};

/**
 * Handle clear configuration
 */
SpacePropertiesProvider.prototype.handleClearConfiguration = function(section) {

  // Confirm with user
  if (confirm(this._translate('Are you sure you want to clear the environment configuration?'))) {
    this._environmentService.clearConfiguration();
    this.refreshEnvironmentSection();
  }
};

/**
 * Handle manual load result
 */
SpacePropertiesProvider.prototype.handleManualLoadResult = function(event) {
  const section = document.querySelector('.space-properties-section[data-group-id="group-environment-config"]');
  if (!section) return;

  if (event.success) {
    this.refreshEnvironmentSection();
  } else {
    this.showFileError(section, event.error || this._translate('Unknown error occurred'));
  }
};

/**
 * Show file loading state
 */
SpacePropertiesProvider.prototype.showFileLoading = function(section, fileName) {
  const button = section.querySelector('.env-file-load-btn');
  if (button) {
    button.innerHTML = `
      <span class="file-text">${this._translate('Loading')} ${fileName}...</span>
    `;
    button.disabled = true;
    button.style.opacity = '0.7';
  }
};

/**
 * Show file success state
 */
SpacePropertiesProvider.prototype.showFileSuccess = function(section, fileName) {
  const button = section.querySelector('.env-file-load-btn');
  if (button) {
    button.innerHTML = `
      <span class="file-text">${this._translate('Loaded')} ${fileName}</span>
    `;
    setTimeout(() => {
      button.disabled = false;
      button.style.opacity = '1';
    }, 1500);
  }
};

/**
 * Show file error state
 */
SpacePropertiesProvider.prototype.showFileError = function(section, message) {
  const button = section.querySelector('.env-file-load-btn');
  if (button) {
    button.innerHTML = `
      <span class="file-text">${message}</span>
    `;
    button.style.color = '#d32f2f';

    setTimeout(() => {
      const hasConfig = this._environmentService.hasConfiguration();
      button.innerHTML = `
        <span class="file-text">${hasConfig ? this._translate('Load Different File') : this._translate('Load environment.json')}</span>
      `;
      button.style.color = '';
      button.disabled = false;
      button.style.opacity = '1';
    }, 3000);
  }
};

/**
 * Refresh environment section
 */
SpacePropertiesProvider.prototype.refreshEnvironmentSection = function() {

  // Check if we're currently showing an environment section
  const existingSection = document.querySelector('.space-properties-section[data-group-id="group-environment-config"]');
  if (existingSection) {

    // Force refresh by removing and recreating
    existingSection.remove();
    this.showEnvironmentSection();
  }
};

// Keep all existing task-related methods unchanged from the original SpacePropertiesProvider
SpacePropertiesProvider.prototype.hideSpaceSection = function() {
  const existingSection = document.querySelector('.space-properties-section');
  if (existingSection) {
    existingSection.remove();
  }
};

SpacePropertiesProvider.prototype.createStandaloneSpaceSection = function(element) {
  const propertiesPanel = document.querySelector('.bio-properties-panel-scroll-container');
  if (!propertiesPanel) {
    return;
  }

  // Remove existing space section if present
  const existingSection = propertiesPanel.querySelector('.space-properties-section');
  if (existingSection) {
    existingSection.remove();
  }

  // Create the standalone space section
  const spaceSection = this.createSpaceSection(element);

  // Insert after General section (usually the first section)
  const generalSection = propertiesPanel.querySelector('[data-group-id*="general"]');
  if (generalSection && generalSection.nextSibling) {
    propertiesPanel.insertBefore(spaceSection, generalSection.nextSibling);
  } else {

    // Fallback: add at the beginning
    propertiesPanel.insertBefore(spaceSection, propertiesPanel.firstChild);
  }
};

// FIXED: Removed the double 'S' typo here
SpacePropertiesProvider.prototype.createSpaceSection = function(element) {
  if (element.type === 'bpmn:StartEvent') {
    return this.createStartEventSpaceSectionContent(element);
  }

  if (element.type === 'bpmn:SendTask') {
    return this.createSendTaskSpaceSectionContent(element);
  }

  const section = document.createElement('div');
  section.className = 'bio-properties-panel-group space-properties-section';
  section.setAttribute('data-group-id', 'group-space-properties');

  const currentType = this._extensionService.getCurrentType(element);
  const currentGuard = this._extensionService.getGuard(element) || '';
  const translate = this._translate;

  // NEW: Get assignment count for badge
  const assignmentCount = this._assignmentService.getAssignmentCount(element);

  const isExpanded = !!currentType || !!currentGuard.trim();
  const hasData = !!currentType || !!currentGuard.trim();

  section.innerHTML = `
    <div class="bio-properties-panel-group-header ${isExpanded ? 'open' : ''} ${hasData ? '' : 'empty'}">
      <div title="Environmental Properties" 
           data-title="Environmental Properties" 
           class="bio-properties-panel-group-header-title">
          Environmental Properties
          ${assignmentCount > 0 ? `<span class="assignment-count-badge">${assignmentCount}</span>` : ''}
      </div>
      <div class="bio-properties-panel-group-header-buttons">
        ${hasData ? '<div title="Section contains data" class="bio-properties-panel-dot"></div>' : ''}
        <button type="button" 
                title="Toggle section" 
                class="bio-properties-panel-group-header-button bio-properties-panel-arrow">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" class="${isExpanded ? 'bio-properties-panel-arrow-down' : 'bio-properties-panel-arrow-right'}">
            <path fill-rule="evenodd" d="m11.657 8-4.95 4.95a1 1 0 0 1-1.414-1.414L8.828 8 5.293 4.464A1 1 0 1 1 6.707 3.05L11.657 8Z"></path>
          </svg>
        </button>
      </div>
    </div>

    <div class="bio-properties-panel-group-entries ${isExpanded ? 'open' : ''}" style="${isExpanded ? '' : 'display: none;'}">
      
      <!-- Task Type Entry -->
      <div data-entry-id="space-task-type" class="bio-properties-panel-entry">
        <div class="bio-properties-panel-textfield">
          <label for="space-type-select" class="bio-properties-panel-label">Type</label>
          <select id="space-type-select" 
                  name="spaceType" 
                  class="bio-properties-panel-input space-type-select">
            <option value="">(None)</option>
            ${getAllTaskTypes().map(config =>
    `<option value="${config.key}" ${config.key === currentType ? 'selected' : ''}>${translate(config.typeValue)}</option>`
  ).join('')}
          </select>
        </div>
      </div>

      <!-- Destination Entry (for Movement) -->
      <div data-entry-id="space-destination" 
           class="bio-properties-panel-entry space-destination-entry" 
           style="${currentType !== 'movement' ? 'display: none;' : ''}">
        <div class="bio-properties-panel-textfield">
          <label for="space-destination-input" class="bio-properties-panel-label">Destination</label>
          <input id="space-destination-input" 
                 type="text" 
                 name="spaceDestination" 
                 spellcheck="false" 
                 autocomplete="off" 
                 class="bio-properties-panel-input space-destination-input"
                 placeholder="${translate('Enter destination')}"
                 value="${this._extensionService.getDestination(element) || ''}" />
        </div>
        ${this.renderDestinationAttributes(element)}
      </div>

      <!-- Binding Entry (for Bind) -->
      <div data-entry-id="space-binding" 
           class="bio-properties-panel-entry space-binding-entry" 
           style="${currentType !== 'binding' ? 'display: none;' : ''}">
      </div>

      <div data-entry-id="space-action" 
           class="bio-properties-panel-entry space-action-entry" 
           style="${currentType !== 'environmental' ? 'display: none;' : ''}">
        <div class="bio-properties-panel-textfield">
          <label for="space-action-input" class="bio-properties-panel-label">Action</label>
          <input id="space-action-input" 
                 type="text" 
                 name="spaceAction" 
                 spellcheck="false" 
                 autocomplete="off" 
                 class="bio-properties-panel-input space-action-input"
                 placeholder="${translate('Enter action')}"
                 value="${this._extensionService.getAction(element) || ''}" />
        </div>
      </div>
      
      <div data-entry-id="space-guard" 
           class="bio-properties-panel-entry space-guard-entry" 
         style="display: block;">
        <div class="bio-properties-panel-textfield">
          <label for="space-guard-input" class="bio-properties-panel-label">Guard</label>
          <input id="space-guard-input" 
                 type="text" 
                 name="spaceGuard" 
                 spellcheck="false" 
                 autocomplete="off" 
                 class="bio-properties-panel-input space-guard-input"
                 placeholder="${translate('Enter guard condition')}"
                 value="${this._extensionService.getGuard(element) || ''}" />
        </div>
      </div>

      <div data-entry-id="space-timer" 
           class="bio-properties-panel-entry space-timer-entry" 
         style="${(currentType !== 'environmental' && currentType !== 'movement' && currentType !== 'binding' && currentType !== 'unbinding') ? 'display: none;' : ''}">
        <div class="bio-properties-panel-textfield">
          <label for="space-timer-input" class="bio-properties-panel-label">Timer (optional)</label>
          <input id="space-timer-input"
                 type="number"
                 min="0"
                 step="1"
                 name="spaceTimer"
                 spellcheck="false"
                 autocomplete="off"
                 class="bio-properties-panel-input space-timer-input"
                 placeholder="${translate('Enter timer value')}"
                 value="${this._extensionService.getTimer(element) || ''}" />
        </div>
      </div>
      </div>
      `;
      
  // <!-- NEW: Task Assignments Section (shown for all task types) -->
  // ${currentType==="environmental" ? this.renderTaskAssignments(element) : ''}
  this.attachSectionEventListeners(section, element);

  return section;
};

SpacePropertiesProvider.prototype.createSendTaskSpaceSectionContent = function(element) {
  const section = document.createElement('div');
  section.className = 'bio-properties-panel-group space-properties-section';
  section.setAttribute('data-group-id', 'group-space-properties');

  const currentGuard = this._extensionService.getGuard(element) || '';
  const translate = this._translate;
  const hasData = !!currentGuard.trim();
  const isExpanded = hasData;

  section.innerHTML = `
    <div class="bio-properties-panel-group-header ${isExpanded ? 'open' : ''} ${hasData ? '' : 'empty'}">
       <div title="Environmental Properties" 
         data-title="Environmental Properties" 
           class="bio-properties-panel-group-header-title">
        Environmental Properties
      </div>
      <div class="bio-properties-panel-group-header-buttons">
        ${hasData ? '<div title="Section contains data" class="bio-properties-panel-dot"></div>' : ''}
        <button type="button" 
                title="Toggle section" 
                class="bio-properties-panel-group-header-button bio-properties-panel-arrow">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" class="${isExpanded ? 'bio-properties-panel-arrow-down' : 'bio-properties-panel-arrow-right'}">
            <path fill-rule="evenodd" d="m11.657 8-4.95 4.95a1 1 0 0 1-1.414-1.414L8.828 8 5.293 4.464A1 1 0 1 1 6.707 3.05L11.657 8Z"></path>
          </svg>
        </button>
      </div>
    </div>

    <div class="bio-properties-panel-group-entries ${isExpanded ? 'open' : ''}" style="${isExpanded ? '' : 'display: none;'}">
      <div data-entry-id="space-guard" class="bio-properties-panel-entry space-guard-entry" style="display: block;">
        <div class="bio-properties-panel-textfield">
          <label for="space-guard-input" class="bio-properties-panel-label">Guard</label>
          <input id="space-guard-input"
                 type="text"
                 name="spaceGuard"
                 spellcheck="false"
                 autocomplete="off"
                 class="bio-properties-panel-input space-guard-input"
                 placeholder="${translate('Enter guard condition')}"
                 value="${currentGuard}" />
        </div>
      </div>
    </div>
  `;

  this.attachSectionEventListeners(section, element);
  return section;
};

SpacePropertiesProvider.prototype.createStartEventSpaceSectionContent = function(element) {
  const section = document.createElement('div');
  section.className = 'bio-properties-panel-group space-properties-section';
  section.setAttribute('data-group-id', 'group-space-properties');

  const currentGuard = this._extensionService.getGuard(element) || '';
  const translate = this._translate;
  const hasData = !!currentGuard.trim();
  const isExpanded = hasData;

  section.innerHTML = `
    <div class="bio-properties-panel-group-header ${isExpanded ? 'open' : ''} ${hasData ? '' : 'empty'}">
       <div title="Environmental Properties"
         data-title="Environmental Properties"
           class="bio-properties-panel-group-header-title">
        Environmental Properties
      </div>
      <div class="bio-properties-panel-group-header-buttons">
        ${hasData ? '<div title="Section contains data" class="bio-properties-panel-dot"></div>' : ''}
        <button type="button"
                title="Toggle section"
                class="bio-properties-panel-group-header-button bio-properties-panel-arrow">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" class="${isExpanded ? 'bio-properties-panel-arrow-down' : 'bio-properties-panel-arrow-right'}">
            <path fill-rule="evenodd" d="m11.657 8-4.95 4.95a1 1 0 0 1-1.414-1.414L8.828 8 5.293 4.464A1 1 0 1 1 6.707 3.05L11.657 8Z"></path>
          </svg>
        </button>
      </div>
    </div>

    <div class="bio-properties-panel-group-entries ${isExpanded ? 'open' : ''}" style="${isExpanded ? '' : 'display: none;'}">
      <div data-entry-id="space-guard" class="bio-properties-panel-entry space-guard-entry" style="display: block;">
        <div class="bio-properties-panel-textfield">
          <label for="space-guard-input" class="bio-properties-panel-label">Condition</label>
          <input id="space-guard-input"
                 type="text"
                 name="spaceGuard"
                 spellcheck="false"
                 autocomplete="off"
                 class="bio-properties-panel-input space-guard-input"
                 value="${currentGuard}" />
        </div>
      </div>
    </div>
  `;

  this.attachSectionEventListeners(section, element);
  return section;
};

// Replace the renderTaskAssignments method in SpacePropertiesProvider
// with this native Camunda-styled version

SpacePropertiesProvider.prototype.renderTaskAssignments = function(element) {
  const translate = this._translate;
  const assignments = this._assignmentService.getAssignments(element);
  const assignmentCount = assignments.length;

  // Generate assignment items using native collapsible structure
  const assignmentItems = assignments.map((assignment, index) => {
    // Create a short title for the collapsed header
    const title = assignment.condition 
      ? `Assignment ${index + 1}: ${assignment.condition.substring(0, 20)}${assignment.condition.length > 20 ? '...' : ''}`
      : `Assignment ${index + 1}`;
    
    return `
    <div class="bio-properties-panel-list-item assignment-list-item">
      <div data-entry-id="assignment-${index}" class="bio-properties-panel-collapsible-entry assignment-collapsible-entry open">
        <div class="bio-properties-panel-collapsible-entry-header assignment-collapsible-header">
          <div title="${this.escapeHtml(title)}" class="bio-properties-panel-collapsible-entry-header-title assignment-collapsible-title">
            ${this.escapeHtml(title)}
          </div>
          <button type="button" 
                  title="${translate('Toggle list item')}" 
                  class="bio-properties-panel-arrow bio-properties-panel-collapsible-entry-arrow btn-toggle-assignment"
                  data-index="${index}">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" class="bio-properties-panel-arrow-down">
              <path fill-rule="evenodd" d="m11.657 8-4.95 4.95a1 1 0 0 1-1.414-1.414L8.828 8 5.293 4.464A1 1 0 1 1 6.707 3.05L11.657 8Z"></path>
            </svg>
          </button>
          <button type="button" 
                  title="${translate('Delete item')}" 
                  class="bio-properties-panel-remove-entry btn-remove-assignment"
                  data-index="${index}">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16">
              <path fill-rule="evenodd" d="M12 6v7c0 1.1-.4 1.55-1.5 1.55h-5C4.4 14.55 4 14.1 4 13V6h8Zm-1.5 1.5h-5v4.3c0 .66.5 1.2 1.111 1.2H9.39c.611 0 1.111-.54 1.111-1.2V7.5ZM13 3h-2l-1-1H6L5 3H3v1.5h10V3Z"></path>
            </svg>
          </button>
        </div>
        <div class="bio-properties-panel-collapsible-entry-entries assignment-collapsible-entries open">
          <!-- Starting Condition Field -->
          <div data-entry-id="assignment-${index}-condition" class="bio-properties-panel-entry assignment-field">
            <div class="bio-properties-panel-textfield">
              <label for="assignment-${index}-condition" class="bio-properties-panel-label">
                ${translate('Starting Condition')}
              </label>
              <input id="assignment-${index}-condition"
                     type="text" 
                     name="assignment-${index}-condition"
                     spellcheck="false"
                     autocomplete="off"
                     class="bio-properties-panel-input assignment-condition" 
                     data-index="${index}"
                     placeholder="${translate('e.g., place1.temperature > 25')}"
                     value="${this.escapeHtml(assignment.condition || '')}" />
            </div>
            <div class="bio-properties-panel-description">
              ${translate('Condition that triggers this assignment')}
            </div>
          </div>
          
          <!-- Value to Assign Field -->
          <div data-entry-id="assignment-${index}-value" class="bio-properties-panel-entry assignment-field">
            <div class="bio-properties-panel-textfield">
              <label for="assignment-${index}-value" class="bio-properties-panel-label">
                ${translate('Value to Assign')}
              </label>
              <input id="assignment-${index}-value"
                     type="text" 
                     name="assignment-${index}-value"
                     spellcheck="false"
                     autocomplete="off"
                     class="bio-properties-panel-input assignment-value" 
                     data-index="${index}"
                     placeholder="${translate('e.g., place1.light = off')}"
                     value="${this.escapeHtml(assignment.value || '')}" />
            </div>
            <div class="bio-properties-panel-description">
              ${translate('New value to set when condition is met')}
            </div>
          </div>
          
          <!-- Validation Message (if needed) -->
          <div class="assignment-validation" style="display: none;">
            <span class="validation-message"></span>
          </div>
        </div>
      </div>
    </div>
    `;
  }).join('');

  // Return the complete assignments section HTML
  return `
    <!-- Task Assignments Entry -->
    <div data-entry-id="space-assignments" class="bio-properties-panel-entry space-assignments-entry">
      <div class="bio-properties-panel-assignments">
        <div class="assignments-header">
          <label class="bio-properties-panel-label">${translate('Task Assignments')}</label>
          <button type="button" 
                  title="${translate('Create new list item')}" 
                  class="bio-properties-panel-group-header-button bio-properties-panel-add-entry btn-add-assignment">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16">
              <path fill-rule="evenodd" d="M9 13V9h4a1 1 0 0 0 0-2H9V3a1 1 0 1 0-2 0v4H3a1 1 0 1 0 0 2h4v4a1 1 0 0 0 2 0Z"></path>
            </svg>
          </button>
          ${assignmentCount > 0 ? `
          <div title="${translate('List contains')} ${assignmentCount} ${assignmentCount === 1 ? translate('item') : translate('items')}" 
               class="bio-properties-panel-list-badge assignments-list-badge">
            ${assignmentCount}
          </div>
          ` : ''}
        </div>
        
        ${assignmentCount === 0 ? `
        <div class="no-assignments"></div>
        ` : `
        <div class="bio-properties-panel-list assignments-list open">
          ${assignmentItems}
        </div>
        `}
      </div>
    </div>
  `;
};

SpacePropertiesProvider.prototype.renderDestinationAttributes = function(element) {
  const destination = this._extensionService.getDestination(element);
  const hasEnvironment = this._environmentService.hasConfiguration();
  const translate = this._translate;

  if (!hasEnvironment || !destination) {
    return '';
  }

  const place = this._environmentService.findPlaceById(destination);
  if (!place) {
    return `
      <div class="destination-attributes destination-not-found">
        <small class="bio-properties-panel-description">
          <span style="color: #f57c00;">${translate('Destination not found in environment')}</span>
        </small>
      </div>
    `;
  }

  // Render place attributes
  const attributes = place.attributes || {};
  const attributeKeys = Object.keys(attributes);

  if (attributeKeys.length === 0) {
    return `
      <div class="destination-attributes destination-no-attributes">
        <small class="bio-properties-panel-description">
          <span style="color: #666;">${translate('No attributes available for this destination')}</span>
        </small>
      </div>
    `;
  }

  // Generate attributes HTML
  const attributesHtml = attributeKeys.map(key => {
    const value = attributes[key];
    let displayValue = value;
    let valueClass = 'attribute-value';

    // Special formatting for certain attribute types
    if (key === 'freeSeats') {
      if (value === 0) {
        displayValue = `${value} (${translate('Full')})`;
        valueClass = 'attribute-value attribute-value-warning';
      } else if (value > 0) {
        displayValue = `${value} ${translate('available')}`;
        valueClass = 'attribute-value attribute-value-success';
      }
    } else if (key === 'zone') {
      valueClass = 'attribute-value attribute-value-zone';
      displayValue = `Zone ${value}`;
    } else if (key === 'purpose') {
      valueClass = 'attribute-value attribute-value-purpose';
      displayValue = value.charAt(0).toUpperCase() + value.slice(1);
    }

    return `
      <div class="attribute-item">
        <span class="attribute-key">${translate(key)}:</span>
        <span class="${valueClass}">${displayValue}</span>
      </div>
    `;
  }).join('');

  return `
    <div class="destination-attributes destination-found">
      <small class="bio-properties-panel-description">
        <span>${translate('Destination attributes')}</span>
      </small>
      <div class="attributes-header">
      </div>
      <div class="attributes-content">
        ${attributesHtml}
      </div>
    </div>
  `;
};

SpacePropertiesProvider.prototype.attachSectionEventListeners = function(section, element) {

  // Toggle section expand/collapse
  const toggleButton = section.querySelector('.bio-properties-panel-group-header-button');
  const header = section.querySelector('.bio-properties-panel-group-header');
  const entries = section.querySelector('.bio-properties-panel-group-entries');

  if (toggleButton && header && entries) {
    toggleButton.addEventListener('click', () => {
      const isOpen = header.classList.contains('open');

      if (isOpen) {

        // Close section
        header.classList.remove('open');
        entries.classList.remove('open');
        entries.style.display = 'none';

        const arrow = toggleButton.querySelector('svg');
        if (arrow) {
          arrow.classList.remove('bio-properties-panel-arrow-down');
          arrow.classList.add('bio-properties-panel-arrow-right');
        }
      } else {

        // Open section
        header.classList.add('open');
        entries.classList.add('open');
        entries.style.display = 'block';

        const arrow = toggleButton.querySelector('svg');
        if (arrow) {
          arrow.classList.remove('bio-properties-panel-arrow-right');
          arrow.classList.add('bio-properties-panel-arrow-down');
        }
      }
    });
  }

  // Form field event listeners
  const typeSelect = section.querySelector('.space-type-select');
  const destinationInput = section.querySelector('.space-destination-input');
  const bindingInput = section.querySelector('.space-binding-input');
  const guardInput = section.querySelector('.space-guard-input');
  const actionInput = section.querySelector('.space-action-input');
  const timerInput = section.querySelector('.space-timer-input');

  // Type selection
  if (typeSelect) {
    typeSelect.addEventListener('change', (e) => {
      try {
        const newType = e.target.value;

        if (newType) {
          this._taskTypeService.setTaskType(element, newType);
        } else {
          this._taskTypeService.clearTaskType(element);
        }

        // Update immediately
        this.updateFieldVisibility(section, newType);
        this.updateSectionIndicators(section, element);

      } catch (error) {
        console.error('Error changing type:', error);
      }
    });
  }

  // Destination input - save on change AND refresh attributes
  if (destinationInput) {
    [ 'input', 'blur', 'change' ].forEach(eventType => {
      destinationInput.addEventListener(eventType, (e) => {
        try {
          const value = e.target.value.trim();
          if (value) {
            this._extensionService.setExtension(element, 'space:Destination', value);
          }

          this.updateSectionIndicators(section, element);
          this.updateDestinationAttributes(section, element);

        } catch (error) {
          console.error('Error saving destination:', error);
        }
      });
    });
  }

  // Binding input - save on change
  if (bindingInput) {
    [ 'input', 'blur', 'change' ].forEach(eventType => {
      bindingInput.addEventListener(eventType, (e) => {
        try {
          const value = e.target.value.trim();
          if (value) {
            this._extensionService.setExtension(element, 'space:Binding', value);
          }

          this.updateSectionIndicators(section, element);

        } catch (error) {
          console.error('Error saving binding:', error);
        }
      });
    });
  }

  // Guard input - save on change
  if (guardInput) {
    [ 'input', 'blur', 'change' ].forEach(eventType => {
      guardInput.addEventListener(eventType, (e) => {
        try {
          const value = e.target.value.trim();
          if (value) {
            this._extensionService.setExtension(element, EXTENSION_TYPES.GUARD, value);
          } else {
            this._extensionService.removeExtensions(
              element,
              ext => ext.$type === EXTENSION_TYPES.GUARD
            );
          }

          this.updateSectionIndicators(section, element);

        } catch (error) {
          console.error('Error saving guard:', error);
        }
      });
    });
  }

  // Action input - save on change
  if (actionInput) {
    [ 'input', 'blur', 'change' ].forEach(eventType => {
      actionInput.addEventListener(eventType, (e) => {
        try {
          const value = e.target.value.trim();
          if (value) {
            this._extensionService.setExtension(element, EXTENSION_TYPES.ACTION, value);
          } else {
            this._extensionService.removeExtensions(
              element,
              ext => ext.$type === EXTENSION_TYPES.ACTION
            );
          }

          this.updateSectionIndicators(section, element);

        } catch (error) {
          console.error('Error saving action:', error);
        }
      });
    });
  }

  // Timer input - optional numeric value for environmental and movement tasks
  if (timerInput) {
    [ 'input', 'blur', 'change' ].forEach(eventType => {
      timerInput.addEventListener(eventType, (e) => {
        try {
          const value = e.target.value.trim();

          if (!value) {
            this._extensionService.removeExtensions(
              element,
              ext => ext.$type === EXTENSION_TYPES.TIMER
            );
            this.updateSectionIndicators(section, element);
            return;
          }

          const numeric = Number(value);
          if (!Number.isFinite(numeric) || numeric < 0) {
            return;
          }

          this._extensionService.setExtension(element, EXTENSION_TYPES.TIMER, String(numeric));
          this.updateSectionIndicators(section, element);

        } catch (error) {
          console.error('Error saving timer:', error);
        }
      });
    });
  }

  this.attachAssignmentListeners(section, element);
};

SpacePropertiesProvider.prototype.attachAssignmentListeners = function(section, element) {
  const translate = this._translate;

  const addButton = section.querySelector('.btn-add-assignment');
  if (addButton) {
    addButton.addEventListener('click', () => {
      this._assignmentService.addAssignment(element, ' ', ' ');
      this.refreshAssignmentsSection(section, element);

      setTimeout(() => {
        const newAssignments = section.querySelectorAll('.assignment-item');
        const lastAssignment = newAssignments[newAssignments.length - 1];
        const conditionInput = lastAssignment?.querySelector('.assignment-condition');
        if (conditionInput) {
          conditionInput.focus();
        }
      }, 100);
    });
  }

  section.querySelectorAll('.btn-remove-assignment').forEach(btn => {
    btn.addEventListener('click', (e) => {
      const index = parseInt(e.currentTarget.getAttribute('data-index'));

      const assignments = this._assignmentService.getAssignments(element);
      const assignment = assignments[index];

      this._assignmentService.removeAssignment(element, index);
      this.refreshAssignmentsSection(section, element);
      this.updateSectionIndicators(section, element);
    });
  });

  section.querySelectorAll('.assignment-condition').forEach(input => {
    const index = parseInt(input.getAttribute('data-index'));

    [ 'blur', 'change' ].forEach(eventType => {
      input.addEventListener(eventType, (e) => {
        const assignments = this._assignmentService.getAssignments(element);
        const assignment = assignments[index];
        if (assignment) {
          this._assignmentService.updateAssignment(
            element,
            index,
            e.target.value.trim(),
            assignment.value
          );
          // this.validateAssignmentField(input, e.target.value.trim(), 'condition');
        }
      });
    });

    // input.addEventListener('input', (e) => {
    //   this.validateAssignmentField(input, e.target.value.trim(), 'condition');
    // });
  });

  section.querySelectorAll('.assignment-value').forEach(input => {
    const index = parseInt(input.getAttribute('data-index'));

    [ 'blur', 'change' ].forEach(eventType => {
      input.addEventListener(eventType, (e) => {
        const assignments = this._assignmentService.getAssignments(element);
        const assignment = assignments[index];
        if (assignment) {
          this._assignmentService.updateAssignment(
            element,
            index,
            assignment.condition,
            e.target.value.trim()
          );
          // this.validateAssignmentField(input, e.target.value.trim(), 'value');
        }
      });
    });

    // input.addEventListener('input', (e) => {
    //   this.validateAssignmentField(input, e.target.value.trim(), 'value');
    // });
  });
};

// SpacePropertiesProvider.prototype.refreshAssignmentsSection = function(section, element) {
//   const assignmentsEntry = section.querySelector('.space-assignments-entry');
//   if (!assignmentsEntry) return;

//   // Get the fresh HTML for assignments
//   const newAssignmentsHTML = this.renderTaskAssignments(element);

//   // Create a temporary container to parse the HTML
//   const temp = document.createElement('div');
//   temp.innerHTML = newAssignmentsHTML;

//   // Find the actual content inside the wrapper
//   const newContent = temp.querySelector('.bio-properties-panel-assignments');

//   // Find the existing container and replace its content
//   const existingContainer = assignmentsEntry.querySelector('.bio-properties-panel-assignments');
//   if (existingContainer && newContent) {
//     existingContainer.innerHTML = newContent.innerHTML;
//   } else {

//     // Fallback: replace entire content
//     assignmentsEntry.innerHTML = newAssignmentsHTML;
//   }

//   // Re-attach event listeners
//   this.attachAssignmentListeners(section, element);

//   // Update the section indicators to refresh the badge count
//   this.updateSectionIndicators(section, element);
// };

// SpacePropertiesProvider.prototype.validateAssignmentField = function(input, value, type) {
//   const assignmentItem = input.closest('.assignment-item');
//   const validationDiv = assignmentItem?.querySelector('.assignment-validation');
//   const validationMessage = validationDiv?.querySelector('.validation-message');

//   if (!validationDiv || !validationMessage) return;

//   if (value && !value.match(/^[^.]+\.[^=<>!]+\s*[=<>!]+\s*.+$/)) {
//     validationDiv.style.display = 'block';
//     validationMessage.textContent = type === 'condition'
//       ? 'Format: place.attribute operator value'
//       : 'Format: place.attribute = value';
//     validationMessage.style.color = '#ef6c00';
//     input.style.borderColor = '#ffb74d';
//   } else {
//     validationDiv.style.display = 'none';
//     input.style.borderColor = '';
//   }
// };

// NEW: Escape HTML helper
SpacePropertiesProvider.prototype.escapeHtml = function(str) {
  const div = document.createElement('div');
  div.textContent = str || '';
  return div.innerHTML;
};

SpacePropertiesProvider.prototype.updateDestinationAttributes = function(section, element) {
  const destinationEntry = section.querySelector('.space-destination-entry');
  if (!destinationEntry) return;

  // Remove existing attributes
  const existingAttributes = destinationEntry.querySelector('.destination-attributes');
  if (existingAttributes) {
    existingAttributes.remove();
  }

  // Add attributes
  const attributesHtml = this.renderDestinationAttributes(element);
  if (attributesHtml) {
    const textField = destinationEntry.querySelector('.bio-properties-panel-textfield');
    textField.insertAdjacentHTML('beforeend', attributesHtml);
  }
};

SpacePropertiesProvider.prototype.updateFieldVisibility = function(section, selectedType) {
  if (selectedType === undefined && section.querySelector('.space-type-select') === null) {
    const guardEntry = section.querySelector('.space-guard-entry');
    if (guardEntry) {
      guardEntry.style.display = 'block';
    }
    return;
  }

  const destinationEntry = section.querySelector('.space-destination-entry');
  const bindingEntry = section.querySelector('.space-binding-entry');
  const unbindingEntry = section.querySelector('.space-unbinding-entry');
  const guardEntry = section.querySelector('.space-guard-entry');
  const actionEntry = section.querySelector('.space-action-entry');
  const timerEntry = section.querySelector('.space-timer-entry');

  if (destinationEntry) {
    destinationEntry.style.display = selectedType === TASK_TYPE_KEYS.MOVEMENT ? 'block' : 'none';
  }

  // By requirement, binding/unbinding should not expose extra fields here.
  if (bindingEntry) {
    bindingEntry.style.display = 'none';
  }
  if (unbindingEntry) {
    unbindingEntry.style.display = 'none';
  }

  if (guardEntry) {
    guardEntry.style.display = 'block';
  }
  if (actionEntry) {
    actionEntry.style.display = selectedType === TASK_TYPE_KEYS.ENVIRONMENTAL ? 'block' : 'none';
  }
  if (timerEntry) {
    timerEntry.style.display =
      selectedType === TASK_TYPE_KEYS.ENVIRONMENTAL ||
      selectedType === TASK_TYPE_KEYS.MOVEMENT ||
      selectedType === TASK_TYPE_KEYS.BINDING ||
      selectedType === TASK_TYPE_KEYS.UNBINDING
        ? 'block'
        : 'none';
  }

  const assignmentsEntry = section.querySelector('.space-assignments-entry');
  if (assignmentsEntry) {
    assignmentsEntry.style.display = selectedType === TASK_TYPE_KEYS.ENVIRONMENTAL ? 'block' : 'none';
  }
};

SpacePropertiesProvider.prototype.getStatusText = function(element, currentType) {
  const translate = this._translate;

  if (element.type === 'bpmn:SendTask') {
    const guard = this._extensionService.getGuard(element);
    if (guard && guard.trim()) {
      return `<strong>${translate('Status')}:</strong> ${translate('Guard configured on send task')}`;
    }
    return `<strong>${translate('Status')}:</strong> ${translate('No configuration')} <br><em>${translate('Configure a guard for this send task')}</em>`;
  }

  if (!currentType) {
    const guard = this._extensionService.getGuard(element);
    if (guard && guard.trim()) {
      return `<strong>${translate('Status')}:</strong> ${translate('Guard configured on base BPMN task')}`;
    }
    return `<strong>${translate('Status')}:</strong> ${translate('No configuration')} <br><em>${translate('Select a type to configure this task')}</em>`;
  }

  const config = getTaskConfig(currentType);
  let status = `<strong>${translate('Status')}:</strong> ${config.typeValue} ${translate('configured')}`;

  if (currentType === TASK_TYPE_KEYS.MOVEMENT) {
    const destination = this._extensionService.getDestination(element);
    status += `<br><strong>${translate('Destination')}:</strong> ${destination || `<em>${translate('(required)')}</em>`}`;
  } else if (currentType === TASK_TYPE_KEYS.BINDING) {
    const binding = this._extensionService.getBinding(element);
    status += `<br><strong>${translate('Participant')}:</strong> ${binding || `<em>${translate('(required)')}</em>`}`;
  } else if (currentType === TASK_TYPE_KEYS.UNBINDING) {
    status += `<br><em>${translate('Ready to release bound participants')}</em>`;
  } else if (currentType === TASK_TYPE_KEYS.ENVIRONMENTAL) {
    status += `<br><em>${translate('Environmental task')}</em>`;
  }

  return status;
};

SpacePropertiesProvider.prototype.updateSectionIndicators = function(section, element) {
  const header = section.querySelector('.bio-properties-panel-group-header');
  const statusDisplay = section.querySelector('.space-status-display');

  const currentType = this._extensionService.getCurrentType(element);
  const currentGuard = this._extensionService.getGuard(element) || '';
  const hasData = element.type === 'bpmn:SendTask'
    ? !!currentGuard.trim()
    : (!!currentType || !!currentGuard.trim());

  const assignmentCount = this._assignmentService.getAssignmentCount(element);
  const titleDiv = header.querySelector('.bio-properties-panel-group-header-title');

  if (titleDiv) {
    if (element.type === 'bpmn:SendTask') {
      titleDiv.childNodes[0].textContent = 'Environmental Properties';
    }

    let badge = titleDiv.querySelector('.assignment-count-badge');
    if (element.type !== 'bpmn:SendTask' && assignmentCount > 0) {
      if (!badge) {
        badge = document.createElement('span');
        badge.className = 'assignment-count-badge';
        titleDiv.appendChild(badge);
      }
      badge.textContent = assignmentCount;
    } else if (badge) {
      badge.remove();
    }
  }

  // Update header class and dot indicator
  if (hasData) {
    header.classList.remove('empty');

    // Add data dot if not present
    let dot = header.querySelector('.bio-properties-panel-dot');
    if (!dot) {
      dot = document.createElement('div');
      dot.className = 'bio-properties-panel-dot';
      dot.title = 'Section contains data';

      const buttonsContainer = header.querySelector('.bio-properties-panel-group-header-buttons');
      if (buttonsContainer) {
        buttonsContainer.insertBefore(dot, buttonsContainer.firstChild);
      }
    }
  } else {
    header.classList.add('empty');

    // Remove data dot
    const dot = header.querySelector('.bio-properties-panel-dot');
    if (dot) {
      dot.remove();
    }
  }

  // Update status display
  if (statusDisplay) {
    statusDisplay.innerHTML = this.getStatusText(element, currentType);
    statusDisplay.style.background = currentType ? '#e8f5e8' : '#f0f0f0';
    statusDisplay.style.color = currentType ? '#2e7d32' : '#666';
    statusDisplay.style.borderLeftColor = currentType ? '#4caf50' : '#ccc';
  }
};

SpacePropertiesProvider.prototype.refreshSpaceSection = function(element) {
  const existingSection = document.querySelector('.space-properties-section');
  if (existingSection && element) {

    if (element.type === 'bpmn:SequenceFlow') {
      const guardInput = existingSection.querySelector('.space-sequenceflow-guard-input');
      if (guardInput) {
        guardInput.value = this._extensionService.getGuard(element) || '';
      }
      return;
    }

    if (element.type === 'bpmn:SendTask') {
      const guardInput = existingSection.querySelector('.space-guard-input');
      if (guardInput) {
        guardInput.value = this._extensionService.getGuard(element) || '';
      }

      this.updateFieldVisibility(existingSection, undefined);
      this.updateSectionIndicators(existingSection, element);
      return;
    }

    // Update form fields with current XML values
    const currentType = this._extensionService.getCurrentType(element);
    const currentDestination = this._extensionService.getDestination(element);
    const currentBinding = this._extensionService.getBinding(element);
    const currentGuard = this._extensionService.getGuard(element);
    const currentAction = this._extensionService.getAction(element);
    const currentTimer = this._extensionService.getTimer(element);

    const typeSelect = existingSection.querySelector('.space-type-select');
    const destinationInput = existingSection.querySelector('.space-destination-input');
    const bindingInput = existingSection.querySelector('.space-binding-input');
    const guardInput = existingSection.querySelector('.space-guard-input');
    const actionInput = existingSection.querySelector('.space-action-input');
    const timerInput = existingSection.querySelector('.space-timer-input');

    if (typeSelect) typeSelect.value = currentType || '';
    if (destinationInput) destinationInput.value = currentDestination || '';
    if (bindingInput) bindingInput.value = currentBinding || '';
    if (guardInput) guardInput.value = currentGuard || '';
    if (actionInput) actionInput.value = currentAction || '';
    if (timerInput) timerInput.value = currentTimer || '';

    
    // if (currentType === TASK_TYPE_KEYS.ENVIRONMENTAL) {
    //   const assignmentsEntry = existingSection.querySelector('.space-assignments-entry');
    //   if (!assignmentsEntry) {
    //     const groupEntries = existingSection.querySelector('.bio-properties-panel-group-entries');
    //     if (groupEntries) {
    //       const assignmentsHTML = this.renderTaskAssignments(element);
    //       groupEntries.insertAdjacentHTML('beforeend', assignmentsHTML);
    //       this.attachAssignmentListeners(existingSection, element);
    //     }
    //   }
    // }

    // Keep UI deterministic when switching type/task: always recompute visibility and values.
    this.updateFieldVisibility(existingSection, currentType);
    this.updateSectionIndicators(existingSection, element);

    this.updateDestinationAttributes(existingSection, element);

    // if (currentType) {
    //   this.refreshAssignmentsSection(existingSection, element);
    // }
  }
};

/**
 * Create Space Properties section for sequence flows (guard support)
 */
SpacePropertiesProvider.prototype.createSequenceFlowSpaceSection = function(element) {
  const propertiesPanel = document.querySelector('.bio-properties-panel-scroll-container');
  if (!propertiesPanel) {
    return;
  }

  // Remove existing space sections
  const existingSection = propertiesPanel.querySelector('.space-properties-section');
  if (existingSection) {
    existingSection.remove();
  }

  const section = document.createElement('div');
  section.className = 'bio-properties-panel-group space-properties-section';
  section.setAttribute('data-group-id', 'group-space-flow-properties');

  const currentGuard = this._extensionService.getGuard(element);
  const hasGuard = !!currentGuard;
  const translate = this._translate;

  section.innerHTML = `
    <div class="bio-properties-panel-group-header ${hasGuard ? 'open' : ''} ${hasGuard ? '' : 'empty'}">
      <div title="Sequence Flow Guard" 
           data-title="Sequence Flow Guard" 
           class="bio-properties-panel-group-header-title">
          Environmental Properties
          ${hasGuard ? '<span class="assignment-count-badge">✓</span>' : ''}
      </div>
      <div class="bio-properties-panel-group-header-buttons">
        ${hasGuard ? '<div title="Guard is set" class="bio-properties-panel-dot"></div>' : ''}
        <button type="button" 
                title="Toggle section" 
                class="bio-properties-panel-group-header-button bio-properties-panel-arrow">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" class="${hasGuard ? 'bio-properties-panel-arrow-down' : 'bio-properties-panel-arrow-right'}">
            <path fill-rule="evenodd" d="m11.657 8-4.95 4.95a1 1 0 0 1-1.414-1.414L8.828 8 5.293 4.464A1 1 0 1 1 6.707 3.05L11.657 8Z"></path>
          </svg>
        </button>
      </div>
    </div>

    <div class="bio-properties-panel-group-entries ${hasGuard ? 'open' : ''}" style="${hasGuard ? '' : 'display: none;'}">
      <!-- Guard Entry -->
      <div data-entry-id="space-seq-guard" class="bio-properties-panel-entry space-seq-guard-entry">
        <div class="bio-properties-panel-textfield">
          <label for="space-seq-guard-input" class="bio-properties-panel-label">Guard Condition</label>
          <input id="space-seq-guard-input" 
                 type="text" 
                 name="spaceSeqGuard" 
                 spellcheck="false" 
                 autocomplete="off" 
                 class="bio-properties-panel-input space-seq-guard-input"
                 placeholder="${translate('Enter guard expression')}"
                 value="${currentGuard || ''}" />
        </div>
      </div>
    </div>
  `;

  // Insert after General section
  const generalSection = propertiesPanel.querySelector('[data-group-id*="general"]');
  if (generalSection && generalSection.nextSibling) {
    propertiesPanel.insertBefore(section, generalSection.nextSibling);
  } else {
    propertiesPanel.insertBefore(section, propertiesPanel.firstChild);
  }

  this.attachSequenceFlowEventListeners(section, element);
};

/**
 * Attach event listeners specifically for sequence flow guard
 */
SpacePropertiesProvider.prototype.attachSequenceFlowEventListeners = function(section, element) {
  // Toggle section expand/collapse
  const toggleButton = section.querySelector('.bio-properties-panel-group-header-button');
  const header = section.querySelector('.bio-properties-panel-group-header');
  const entries = section.querySelector('.bio-properties-panel-group-entries');

  if (toggleButton && header && entries) {
    toggleButton.addEventListener('click', () => {
      const isOpen = header.classList.contains('open');

      if (isOpen) {
        header.classList.remove('open');
        entries.classList.remove('open');
        entries.style.display = 'none';
        const arrow = toggleButton.querySelector('svg');
        if (arrow) {
          arrow.classList.remove('bio-properties-panel-arrow-down');
          arrow.classList.add('bio-properties-panel-arrow-right');
        }
      } else {
        header.classList.add('open');
        entries.classList.add('open');
        entries.style.display = 'block';
        const arrow = toggleButton.querySelector('svg');
        if (arrow) {
          arrow.classList.remove('bio-properties-panel-arrow-right');
          arrow.classList.add('bio-properties-panel-arrow-down');
        }
      }
    });
  }

  // Guard input listener
  const guardInput = section.querySelector('.space-seq-guard-input');
  if (guardInput) {
    ['input', 'blur', 'change'].forEach(eventType => {
      guardInput.addEventListener(eventType, (e) => {
        try {
          const value = e.target.value.trim();
          
          if (value) {
            // Set guard extension
            this._extensionService.setExtension(element, EXTENSION_TYPES.GUARD, value);
            // Add condition expression to sequence flow
            this.getOrCreateConditionExpression(element);
          } else {
            // Remove guard extension
            this._extensionService.removeExtensions(
              element,
              ext => ext.$type === EXTENSION_TYPES.GUARD
            );
            // Remove condition expression
            this.removeConditionExpression(element);
          }

          // Update UI state
          header.classList.toggle('empty', !value);
          const dot = header.querySelector('.bio-properties-panel-dot');
          if (!value && dot) {
            dot.remove();
          } else if (value && !dot) {
            const newDot = document.createElement('div');
            newDot.className = 'bio-properties-panel-dot';
            newDot.title = 'Guard is set';
            header.querySelector('.bio-properties-panel-group-header-buttons').insertBefore(newDot, toggleButton);
          }

        } catch (error) {
          console.error('Error saving guard:', error);
        }
      });
    });
  }
};

/**
 * Helper: Create or get conditionExpression on sequence flow
 * Sets it to ${true == true} so Camunda always follows when guard is satisfied
 */
SpacePropertiesProvider.prototype.getOrCreateConditionExpression = function(element) {
  if (!element.businessObject) {
    return;
  }

  const bo = element.businessObject;
  const moddle = bo.$model;

  if (!bo.conditionExpression) {
    // Create new condition expression
    const conditionExpression = moddle.create('bpmn:FormalExpression', {
      body: '${true == true}'
    });
    bo.conditionExpression = conditionExpression;
  } else {
    // Update existing to true
    bo.conditionExpression.body = '${true == true}';
  }
};

/**
 * Helper: Remove conditionExpression from sequence flow
 */
SpacePropertiesProvider.prototype.removeConditionExpression = function(element) {
  if (!element.businessObject) {
    return;
  }

  const bo = element.businessObject;
  bo.conditionExpression = undefined;
};

export default {
  __init__: [ 'spacePropertiesProvider' ],
  spacePropertiesProvider: [ 'type', SpacePropertiesProvider ]
};