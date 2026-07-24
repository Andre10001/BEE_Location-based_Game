/**
 * EnvironmentService - Manages environmental configuration data
 *
 * Responsibilities:
 * - Store and manage loaded environment configuration
 * - Provide access to physicalPlaces, edges, logical places, and views
 * - Handle configuration validation and queries
 * - Emit events when configuration changes
 * - Handle manual file loading
 * - Integrate with bpenv-modeler for visual editing
 */
export function EnvironmentService(eventBus, bpenvModeler) {
  this.eventBus = eventBus;
  this.bpenvModeler = bpenvModeler; // Injected dependency
  this.currentConfig = null;
  this.isLoaded = false;
  this.modelerReady = false;

  // Listen for configuration events
  eventBus.on('environment.config.loaded', ({ config }) => {
    this.setConfiguration(config);
  });

  eventBus.on('environment.config.cleared', () => {
    this.clearConfiguration();
  });

  // Initialize the modeler
  this.initializeModeler();
}

EnvironmentService.$inject = [ 'eventBus', 'bpenvModeler' ];

/**
 * Initialize the bpenv-modeler
 */
EnvironmentService.prototype.initializeModeler = function() {
  try {

    // Create container if it doesn't exist
    if (!document.getElementById('bpenv-container')) {
      const container = document.createElement('div');
      container.id = 'bpenv-container';
      container.style.display = 'none'; // Hidden by default
      document.body.appendChild(container);
    }

    // Render the modeler
    this.bpenvModeler.render('bpenv-container');
    // this.bpenvModeler.setEditable(false);
    this.modelerReady = true;

    console.log('BPEnv Modeler initialized successfully');
  } catch (error) {
    console.error('Failed to initialize BPEnv Modeler:', error);
    this.modelerReady = false;
  }
};

/**
 * Set the current environment configuration
 * @param {Object} config - Configuration object with data, fileName, loadedAt
 */
EnvironmentService.prototype.setConfiguration = function(config) {
  this.currentConfig = config;
  this.isLoaded = true;

  // Upload data to modeler if available
  if (this.modelerReady && config?.data) {
    this.uploadToModeler(config.data);
  }

  // Fire event for other modules
  this.eventBus.fire('environment.ready', {
    config: this.currentConfig
  });
};

/**
 * Upload data to the bpenv-modeler
 * @param {Object} data - Environment data to upload
 */
EnvironmentService.prototype.uploadToModeler = function(data) {
  if (!this.modelerReady) {
    console.warn('BPEnv Modeler not ready, cannot upload data');
    return;
  }

  try {

    // Prepare model data in the format expected by bpenv-modeler
    const modelData = {
      physicalPlaces: data.physicalPlaces || [],
      edges: data.edges || [],
      logicalPlaces: data.logicalPlaces || [],
      views: data.views || []
    };

    this.bpenvModeler.setModel(modelData);

    console.log('Environment data uploaded to BPEnv Modeler successfully', {
      physicalPlaces: modelData.physicalPlaces.length,
      edges: modelData.edges.length,
      logicalPlaces: modelData.logicalPlaces.length,
      views: modelData.views.length
    });

    // Fire event for UI updates
    this.eventBus.fire('environment.modeler.updated', {
      data: modelData
    });

  } catch (error) {
    console.error('Failed to upload data to BPEnv Modeler:', error);
    this.eventBus.fire('environment.modeler.error', {
      error: error.message
    });
  }
};

/**
 * Sync data from modeler back to EnvironmentService
 * @returns {boolean} True if sync was successful
 */
EnvironmentService.prototype.syncFromModeler = function() {
  if (!this.modelerReady) {
    console.warn('BPEnv Modeler not ready, cannot sync data');
    return false;
  }

  try {

    // Get current model from modeler
    const modelData = this.bpenvModeler.getModel();

    if (!modelData) {
      console.warn('No model data available from BPEnv Modeler');
      return false;
    }

    // Validate the model data
    if (!this.validateEnvironmentData(modelData)) {
      console.error('Invalid model data from BPEnv Modeler');
      return false;
    }

    // Create new configuration object
    const config = {
      data: modelData,
      fileName: this.currentConfig?.fileName || 'modeler-data.json',
      loadedAt: new Date().toISOString(),
      source: 'modeler'
    };

    // Update configuration
    this.setConfiguration(config);

    console.log('Environment data synced from BPEnv Modeler successfully');
    return true;

  } catch (error) {
    console.error('Failed to sync data from BPEnv Modeler:', error);
    this.eventBus.fire('environment.modeler.error', {
      error: error.message
    });
    return false;
  }
};

/**
 * Get current model from modeler
 * @returns {Object|null} Current model data or null
 */
EnvironmentService.prototype.getModelerData = function() {
  if (!this.modelerReady) {
    return null;
  }

  try {
    return this.bpenvModeler.getModel();
  } catch (error) {
    console.error('Failed to get data from BPEnv Modeler:', error);
    return null;
  }
};

/**
 * Check if modeler is in edit mode
 * @returns {boolean} True if modeler is editable
 */
EnvironmentService.prototype.isModelerEditable = function() {
  if (!this.modelerReady) {
    return false;
  }

  try {
    return this.bpenvModeler.isEditable();
  } catch (error) {
    console.error('Failed to check modeler edit state:', error);
    return false;
  }
};

/**
 * Set modeler edit mode
 * @param {boolean} editable - Whether modeler should be editable
 */
EnvironmentService.prototype.setModelerEditable = function(editable) {
  if (!this.modelerReady) {
    console.warn('BPEnv Modeler not ready, cannot set edit mode');
    return;
  }

  try {
    this.bpenvModeler.setEditable(editable);
    console.log(`BPEnv Modeler edit mode set to: ${editable}`);
  } catch (error) {
    console.error('Failed to set modeler edit mode:', error);
  }
};

/**
 * Clear the current configuration
 */
EnvironmentService.prototype.clearConfiguration = function() {
  this.currentConfig = null;
  this.isLoaded = false;

  // Clear modeler data
  if (this.modelerReady) {
    try {
      this.bpenvModeler.setModel({
        physicalPlaces: [],
        edges: [],
        logicalPlaces: [],
        views: []
      });
    } catch (error) {
      console.error('Failed to clear BPEnv Modeler data:', error);
    }
  }

  console.log('Environment configuration cleared');

  this.eventBus.fire('environment.cleared');
};

/**
 * Handle manual file loading from user input
 * @param {File} file - File object from input
 */
EnvironmentService.prototype.handleManualFileLoad = function(file) {
  const reader = new FileReader();

  reader.onload = (e) => {
    try {
      const content = e.target.result;
      const data = JSON.parse(content);

      // Validate the data structure
      if (!this.validateEnvironmentData(data)) {
        this.eventBus.fire('environment.manual.loaded', {
          success: false,
          error: 'Invalid environment file format'
        });
        return;
      }

      // Create configuration object
      const config = {
        data: data,
        fileName: file.name,
        loadedAt: new Date().toISOString(),
        source: 'manual'
      };

      // Set the configuration (this will also upload to modeler)
      this.setConfiguration(config);

      // Fire success event
      this.eventBus.fire('environment.manual.loaded', {
        success: true,
        config: config
      });

    } catch (error) {
      console.error('Failed to parse environment file:', error);
      this.eventBus.fire('environment.manual.loaded', {
        success: false,
        error: 'Failed to parse JSON file: ' + error.message
      });
    }
  };

  reader.onerror = () => {
    this.eventBus.fire('environment.manual.loaded', {
      success: false,
      error: 'Failed to read file'
    });
  };

  reader.readAsText(file);
};

/**
 * Export current modeler data as JSON
 * @returns {string|null} JSON string or null if no data
 */
EnvironmentService.prototype.exportModelerData = function() {
  const modelData = this.getModelerData();
  if (!modelData) {
    return null;
  }

  try {
    return JSON.stringify(modelData, null, 2);
  } catch (error) {
    console.error('Failed to export modeler data:', error);
    return null;
  }
};

/**
 * Check if modeler is ready
 * @returns {boolean} True if modeler is ready
 */
EnvironmentService.prototype.isModelerReady = function() {
  return this.modelerReady;
};

/**
 * Validate environment data structure
 * @param {Object} data - Data to validate
 * @returns {boolean} True if valid
 */
EnvironmentService.prototype.validateEnvironmentData = function(data) {
  if (!data || typeof data !== 'object') {
    return false;
  }

  // Check for required arrays
  const requiredArrays = [ 'physicalPlaces', 'edges', 'logicalPlaces', 'views' ];
  for (const key of requiredArrays) {
    if (!Array.isArray(data[key])) {
      console.warn(`Missing or invalid ${key} array in environment data`);
      return false;
    }
  }

  // Validate physicalPlaces structure if not empty
  if (data.physicalPlaces.length > 0) {
    const place = data.physicalPlaces[0];
    if (!place.id || !place.name) {
      console.warn('Places must have id and name properties');
      return false;
    }
  }

  return true;
};

/**
 * Check if we have any configuration or modeler data
 * @returns {boolean} True if data is available
 */
EnvironmentService.prototype.hasConfiguration = function() {

  // Always return true if we loaded a file
  if (this.isLoaded && this.currentConfig !== null) {
    return true;
  }

  // Check modeler for any data
  if (this.modelerReady) {
    try {
      const physicalPlaces = this.bpenvModeler.getPhysicalPlaces() || [];
      const logicalPlaces = this.bpenvModeler.getLogicalPlaces() || [];
      const edges = this.bpenvModeler.getEdges() || [];
      const views = this.bpenvModeler.getViews() || [];

      const hasData = physicalPlaces.length > 0 || logicalPlaces.length > 0 ||
                     edges.length > 0 || views.length > 0;

      if (hasData) {
        console.log('Modeler has data - keeping config visible');
        return true;
      }
    } catch (error) {
      console.warn('Error checking modeler data (not hiding config):', error);
    }
  }

  return false;
};

/**
 * Get the current configuration
 * @returns {Object|null} Current configuration or null
 */
EnvironmentService.prototype.getConfiguration = function() {
  return this.currentConfig;
};

/**
 * Get all physicalPlaces (physical + logical) from modeler or config
 * @returns {Array} Array of place objects
 */
EnvironmentService.prototype.getPlaces = function() {
  if (this.modelerReady) {
    try {
      const physicalPlaces = this.bpenvModeler.getPhysicalPlaces() || [];
      const logicalPlaces = this.bpenvModeler.getLogicalPlaces() || [];

      // Combine both types for destinations
      const allPlaces = [ ...physicalPlaces, ...logicalPlaces ];

      if (allPlaces.length > 0) {
        console.log(`Found ${physicalPlaces.length} physical + ${logicalPlaces.length} logical physicalPlaces`);
        return allPlaces;
      }
    } catch (error) {
      console.warn('Failed to get physicalPlaces from modeler, falling back to config:', error);
    }
  }
  return this.currentConfig?.data?.physicalPlaces || [];
};

/**
 * Get all edges from modeler or config
 * @returns {Array} Array of edge objects
 */
EnvironmentService.prototype.getEdges = function() {
  if (this.modelerReady) {
    try {
      const edges = this.bpenvModeler.getEdges();
      if (edges && Array.isArray(edges)) {
        return edges;
      }
    } catch (error) {
      console.warn('Failed to get edges from modeler, falling back to config:', error);
    }
  }
  return this.currentConfig?.data?.edges || [];
};

/**
 * Get all logical physicalPlaces from modeler or config
 * @returns {Array} Array of logical place objects
 */
EnvironmentService.prototype.getLogicalPlaces = function() {
  if (this.modelerReady) {
    try {
      const logicalPlaces = this.bpenvModeler.getLogicalPlaces();
      if (logicalPlaces && Array.isArray(logicalPlaces)) {
        return logicalPlaces;
      }
    } catch (error) {
      console.warn('Failed to get logical physicalPlaces from modeler, falling back to config:', error);
    }
  }
  return this.currentConfig?.data?.logicalPlaces || [];
};

/**
 * Get all views from modeler or config
 * @returns {Array} Array of view objects
 */
EnvironmentService.prototype.getViews = function() {
  if (this.modelerReady) {
    try {
      const views = this.bpenvModeler.getViews();
      if (views && Array.isArray(views)) {
        return views;
      }
    } catch (error) {
      console.warn('Failed to get views from modeler, falling back to config:', error);
    }
  }
  return this.currentConfig?.data?.views || [];
};

/**
 * Get current model from modeler (preferred) or internal configuration
 * @returns {Object} Complete model data
 */
EnvironmentService.prototype.getCurrentModel = function() {
  if (this.modelerReady) {
    try {
      const model = this.bpenvModeler.getModel();
      if (model) {
        return model;
      }
    } catch (error) {
      console.warn('Failed to get model from modeler, falling back to config:', error);
    }
  }

  return this.currentConfig?.data || {
    physicalPlaces: [],
    edges: [],
    logicalPlaces: [],
    views: []
  };
};

/**
 * Find a place by ID (searches both physical and logical)
 * @param {string} placeId - Place ID to find
 * @returns {Object|null} Place object or null if not found
 */
EnvironmentService.prototype.findPlaceById = function(placeId) {
  const physicalPlaces = this.getPlaces();
  return physicalPlaces.find(place => place.id === placeId) || null;
};

/**
 * Find a place by name (searches both physical and logical)
 * @param {string} placeName - Place name to find
 * @returns {Object|null} Place object or null if not found
 */
EnvironmentService.prototype.findPlaceByName = function(placeName) {
  const physicalPlaces = this.getPlaces();
  return physicalPlaces.find(place => place.name === placeName) || null;
};

/**
 * Get physicalPlaces by zone
 * @param {string} zone - Zone identifier (e.g., "A", "B")
 * @returns {Array} Array of physicalPlaces in the specified zone
 */
EnvironmentService.prototype.getPlacesByZone = function(zone) {
  const physicalPlaces = this.getPlaces();
  return physicalPlaces.filter(place => place.attributes?.zone === zone);
};

/**
 * Get physicalPlaces by purpose
 * @param {string} purpose - Purpose identifier (e.g., "teaching", "studying")
 * @returns {Array} Array of physicalPlaces with the specified purpose
 */
EnvironmentService.prototype.getPlacesByPurpose = function(purpose) {
  const physicalPlaces = this.getPlaces();
  return physicalPlaces.filter(place => place.attributes?.purpose === purpose);
};

/**
 * Get available destinations (place names)
 * @returns {Array} Array of place names that can be used as destinations
 */
EnvironmentService.prototype.getAvailableDestinations = function() {
  const physicalPlaces = this.getPlaces();
  return physicalPlaces.map(place => place.name).filter(name => name);
};

/**
 * Get physicalPlaces with free seats
 * @param {number} minSeats - Minimum number of free seats (optional)
 * @returns {Array} Array of physicalPlaces with available seats
 */
EnvironmentService.prototype.getAvailablePlaces = function(minSeats = 1) {
  const physicalPlaces = this.getPlaces();
  return physicalPlaces.filter(place => {
    const freeSeats = place.attributes?.freeSeats;
    return freeSeats && freeSeats >= minSeats;
  });
};

/**
 * Get configuration summary using current data
 * @returns {Object} Summary of current data
 */
EnvironmentService.prototype.getConfigSummary = function() {
  if (!this.hasConfiguration()) {
    return { loaded: false };
  }

  let summary = { physicalPlaces: 0, edges: 0, logicalPlaces: 0, views: 0 };
  let zones = [];
  let purposes = [];

  if (this.modelerReady) {
    try {
      const physicalPlaces = this.bpenvModeler.getPhysicalPlaces() || [];
      const logicalPlaces = this.bpenvModeler.getLogicalPlaces() || [];
      const edges = this.bpenvModeler.getEdges() || [];
      const views = this.bpenvModeler.getViews() || [];

      summary = {
        physicalPlaces: physicalPlaces.length,
        edges: edges.length,
        logicalPlaces: logicalPlaces.length,
        views: views.length
      };

      // Extract zones and purposes from both place types
      const allPlaces = [ ...physicalPlaces, ...logicalPlaces ];
      zones = [ ...new Set(allPlaces.map(p => p.attributes?.zone).filter(Boolean)) ];
      purposes = [ ...new Set(allPlaces.map(p => p.attributes?.purpose).filter(Boolean)) ];
    } catch (error) {
      console.warn('Error getting modeler summary:', error);
    }
  }

  // Fallback to config data
  if (summary.physicalPlaces === 0 && this.currentConfig?.data) {
    const data = this.currentConfig.data;
    summary = {
      physicalPlaces: data.physicalPlaces?.length || 0,
      edges: data.edges?.length || 0,
      logicalPlaces: data.logicalPlaces?.length || 0,
      views: data.views?.length || 0
    };
    zones = [ ...new Set(data.physicalPlaces?.map(p => p.attributes?.zone).filter(Boolean)) ];
    purposes = [ ...new Set(data.physicalPlaces?.map(p => p.attributes?.purpose).filter(Boolean)) ];
  }

  return {
    loaded: true,
    fileName: this.currentConfig?.fileName || 'modeler-data.json',
    loadedAt: this.currentConfig?.loadedAt || new Date().toISOString(),
    source: this.currentConfig?.source || 'modeler',
    summary,
    zones,
    purposes
  };
};

/**
 * Validate that a destination exists in the configuration
 * @param {string} destination - Destination name to validate
 * @returns {boolean} True if destination exists
 */
EnvironmentService.prototype.isValidDestination = function(destination) {
  if (!destination || !this.hasConfiguration()) return false;

  const physicalPlaces = this.getPlaces();
  return physicalPlaces.some(place => place.name === destination);
};

/**
 * Get suggestions for destinations based on partial input
 * @param {string} partial - Partial destination name
 * @param {number} maxSuggestions - Maximum number of suggestions (default: 5)
 * @returns {Array} Array of suggested destination names
 */
EnvironmentService.prototype.getDestinationSuggestions = function(partial, maxSuggestions = 5) {
  if (!partial || !this.hasConfiguration()) return [];

  const physicalPlaces = this.getPlaces();
  const partialLower = partial.toLowerCase();

  const suggestions = physicalPlaces
    .filter(place => place.name && place.name.toLowerCase().includes(partialLower))
    .map(place => place.name)
    .slice(0, maxSuggestions);

  return suggestions;
};