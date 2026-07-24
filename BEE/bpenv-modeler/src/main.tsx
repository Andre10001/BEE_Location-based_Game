import ReactDOM from 'react-dom/client';
import Header from './components/Header';
import Map from './components/Map';
import { PhysicalPlace, LogicalPlace, Edge, View } from './envTypes';
import { useEnvStore } from './envStore';

import 'bootstrap/dist/css/bootstrap.min.css';
import 'ol/ol.css';
import './style.css';

let apis: any = null;

if (!import.meta.env.PROD) {
  render('bpenv-container');
}

function render(containerId: string, headless: boolean = false) {
  const container = document.getElementById(containerId);
  if (!container) throw new Error(`Container element with id "${containerId}" not found.`);

  const root = ReactDOM.createRoot(container);

  root.render(
    <div style={{ height: '100%' }}>
      {!headless && <Header />}
      <Map />
    </div>
  );

  function getPhysicalPlaces(): PhysicalPlace[] {
    return useEnvStore.getState().physicalPlaces;
  }

  function getEdges(): Edge[] {
    return useEnvStore.getState().edges;
  }

  function getLogicalPlaces(): LogicalPlace[] {
    return useEnvStore.getState().logicalPlaces;
  }

  function getViews(): View[] {
    return useEnvStore.getState().views;
  }

  function getModel(): any {
    return {
      physicalPlaces: getPhysicalPlaces(),
      edges: getEdges(),
      logicalPlaces: getLogicalPlaces(),
      views: getViews(),
    };
  }

  function setModel(model: {
    physicalPlaces: PhysicalPlace[];
    edges: Edge[];
    logicalPlaces: LogicalPlace[];
    views: View[];
  }) {
    useEnvStore.getState().setModel(model);
  }

  function isEditable(): boolean {
    return useEnvStore.getState().isEditable;
  }

  function setEditable(isEditable: boolean) {
    useEnvStore.getState().setEditable(isEditable);
  }

  apis = {
    getPhysicalPlaces,
    getEdges,
    getLogicalPlaces,
    getViews,
    getModel,
    setModel,
    isEditable,
    setEditable,
  };

  return apis;
}

function getPhysicalPlaces() {
  if (!apis) throw new Error('Modeler not initialized. Call render() first.');
  return apis.getPhysicalPlaces();
}

function getEdges() {
  if (!apis) throw new Error('Modeler not initialized. Call render() first.');
  return apis.getEdges();
}

function getLogicalPlaces() {
  if (!apis) throw new Error('Modeler not initialized. Call render() first.');
  return apis.getLogicalPlaces();
}

function getViews() {
  if (!apis) throw new Error('Modeler not initialized. Call render() first.');
  return apis.getViews();
}

function getModel() {
  if (!apis) throw new Error('Modeler not initialized. Call render() first.');
  return apis.getModel();
}

function setModel(model: {
  physicalPlaces: PhysicalPlace[];
  edges: Edge[];
  logicalPlaces: LogicalPlace[];
  views: View[];
}) {
  if (!apis) throw new Error('Modeler not initialized. Call render() first.');
  apis.setModel(model);
}

function isEditable() {
  if (!apis) throw new Error('Modeler not initialized. Call render() first.');
  return apis.isEditable();
}

function setEditable(isEditable: boolean) {
  if (!apis) throw new Error('Modeler not initialized. Call render() first.');
  apis.setEditable(isEditable);
}

const bpenvModeler = {
  render,
  getPhysicalPlaces,
  getEdges,
  getLogicalPlaces,
  getViews,
  getModel,
  setModel,
  isEditable,
  setEditable,
};

export default bpenvModeler;