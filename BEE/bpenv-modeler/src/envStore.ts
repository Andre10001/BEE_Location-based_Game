import { create } from 'zustand';
import { PhysicalPlace, LogicalPlace, Edge, View } from './envTypes';
import { clearFeatures, removeFeature, drawPlace, drawEdge, getFeature, enablePlaceDrawing, enableEdgeDrawing, disableDrawing, fitFeaturesOnMap} from './utils';
import Map from 'ol/Map.js';

type EnvStore = {
    mapInstance: Map;
    setMapInstance: (map: Map) => void;

    isEditable: boolean;
    setEditable: (editable: boolean) => void;

    activeTool: 'hand' | 'place' | 'edge';
    setActiveTool: (tool: 'hand' | 'place' | 'edge') => void;

    physicalPlaces: PhysicalPlace[];
    logicalPlaces: LogicalPlace[];
    edges: Edge[];
    views: View[];

    addPlace: (place: PhysicalPlace) => void;
    updatePlace: (id: string, updatedPlace: Partial<PhysicalPlace>) => void;
    removePlace: (id: string) => void;

    addEdge: (edge: Edge) => void;
    updateEdge: (id: string, updatedEdge: Partial<Edge>) => void;
    removeEdge: (id: string) => void;

    addLogicalPlace: (logicalPlace: LogicalPlace) => void;
    updateLogicalPlace: (id: string, updatedLogicalPlace: Partial<LogicalPlace>) => void;
    removeLogicalPlace: (id: string) => void;

    addView: (view: View) => void;
    updateView: (id: string, updatedView: Partial<View>) => void;
    removeView: (id: string) => void;

    setModel: (model: {
        physicalPlaces: PhysicalPlace[],
        edges: Edge[],
        logicalPlaces: LogicalPlace[],
        views: View[]
    }) => void;
    clearModel: () => void;
};

export const useEnvStore = create<EnvStore>((set, get) => ({
    mapInstance: new Map(),
    setMapInstance: (map) => set({ mapInstance: map }),

    isEditable: true,
    setEditable: (editable) => set({ isEditable: editable }),

    activeTool: 'hand',
    setActiveTool: (tool) => {
        disableDrawing(get().mapInstance);
        if (tool === 'place')
            enablePlaceDrawing(get().mapInstance);
        else if (tool === 'edge')
            enableEdgeDrawing(get().mapInstance);
        set({ activeTool: tool });
    },

    physicalPlaces: [],
    logicalPlaces: [],
    edges: [],
    views: [],

    addPlace: (place) =>
        set((state) => ({
            physicalPlaces: [...state.physicalPlaces, place]
        })),

    updatePlace: (id, updatedPlace) =>
        set((state) => ({
            physicalPlaces: state.physicalPlaces.map((p) =>
                p.id === id ? { ...p, ...updatedPlace } : p
            )
        })),

    removePlace: (id) => {
        removeFeature(get().mapInstance, id);
        set((state) => ({
            physicalPlaces: state.physicalPlaces.filter((p) => p.id !== id)
        }));

        get().edges.forEach((e) => {
            if (e.source === id || e.target === id) get().removeEdge(e.id);
        });
    },

    addEdge: (edge) =>
        set((state) => ({
            edges: [...state.edges, edge]
        })),

    updateEdge: (id, updatedEdge) =>
        set((state) => ({
            edges: state.edges.map((e) =>
                e.id === id ? { ...e, ...updatedEdge } : e
            )
        })),

    removeEdge: (id) => {
        removeFeature(get().mapInstance, id);
        set((state) => ({
            edges: state.edges.filter((e) => e.id !== id)
        }));
    },

    addLogicalPlace: (logicalPlace) =>
        set((state) => ({
            logicalPlaces: [...state.logicalPlaces, logicalPlace]
        })),

    updateLogicalPlace: (id, updatedLogicalPlace) =>
        set((state) => ({
            logicalPlaces: state.logicalPlaces.map((lp) =>
                lp.id === id ? { ...lp, ...updatedLogicalPlace } : lp
            )
        })),

    removeLogicalPlace: (id) =>
        set((state) => ({
            logicalPlaces: state.logicalPlaces.filter((lp) => lp.id !== id)
        })),

    addView: (view) =>
        set((state) => ({
            views: [...state.views, view]
        })),

    updateView: (id, updatedView) =>
        set((state) => ({
            views: state.views.map((v) =>
                v.id === id ? { ...v, ...updatedView } : v
            )
        })),

    removeView: (id) =>
        set((state) => ({
            views: state.views.filter((v) => v.id !== id)
        })),

    setModel: (model) => {
        clearFeatures(get().mapInstance);
        set(() => ({
            physicalPlaces: model.physicalPlaces,
            edges: model.edges,
            logicalPlaces: model.logicalPlaces,
            views: model.views
        }));

        model.physicalPlaces.forEach((place: PhysicalPlace) => {
            drawPlace(get().mapInstance, place.id, place.coordinates);
        });
        model.edges.forEach((edge: Edge) => {
            const sourceFeature = getFeature(get().mapInstance, edge.source);
            const targetFeature = getFeature(get().mapInstance, edge.target);
            if (sourceFeature && targetFeature)
                drawEdge(get().mapInstance, sourceFeature, targetFeature, edge.id);
        });
    },

    clearModel: () => {
        clearFeatures(get().mapInstance);
        set(() => ({
            physicalPlaces: [],
            logicalPlaces: [],
            edges: [],
            views: []
        }))
    }
}));