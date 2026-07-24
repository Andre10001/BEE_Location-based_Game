import { Map, Feature, View } from 'ol';
import { useGeographic } from 'ol/proj';
import { Polygon, LineString, Point, Geometry } from 'ol/geom';
import { Extent, extend, isEmpty } from 'ol/extent';
import { Draw, Snap, Select } from 'ol/interaction';
import { Style, Fill, Stroke, Text } from 'ol/style';
import { OSM } from 'ol/source';
import { Tile as TileLayer } from 'ol/layer';
import VectorSource from 'ol/source/Vector';
import VectorLayer from 'ol/layer/Vector';
import { click } from 'ol/events/condition';

import { PhysicalPlace, Edge } from './envTypes';
import { useEnvStore } from './envStore';

let drawInteraction: Draw | null = null;
let snapInteraction: Snap | null = null;
let selectInteraction: Select | null = null;

// Utils

export function initMap(mapRef: HTMLDivElement): Map {
    useGeographic();
    const map = new Map({
        layers: [new TileLayer({
            source: new OSM(),
            zIndex: 0,
        })],
        target: mapRef,
        view: new View({
            center: [13.068307772123394, 43.139407493133405],
            zoom: 14,
        }),
        controls: [],
    });

    map.addLayer(new VectorLayer({
        source: new VectorSource(),
        style: (feature) => {
            const geometry = feature.getGeometry();
            const name = feature.get('name') || '';
            const highlighted = feature.get('highlighted') === true;

            if (geometry instanceof Polygon) {
                return new Style({
                    stroke: new Stroke({ color: 'red', width: 2 }),
                    fill: new Fill({
                        color: highlighted ? 'rgba(255, 255, 0, 0.4)' : 'rgba(0, 0, 0, 0.1)',
                    }),
                    // text: new Text({
                    //     text: name,
                    //     font: '8px sans-serif',
                    //     fill: new Fill({ color: '#000' }),
                    //     stroke: new Stroke({ color: '#fff', width: 2 }),
                    //     textAlign: 'center',
                    //     textBaseline: 'middle',
                    //     overflow: true,
                    // }),
                });
            }

            if (geometry instanceof LineString) {
                const styles: Style[] = [];

                styles.push(new Style({
                    stroke: new Stroke({
                        color: highlighted ? 'rgb(255, 255, 0)' : 'rgba(255, 0, 0, 0.2)',
                        width: 3,
                    }),
                    // text: new Text({
                    //     text: name,
                    //     font: '8px sans-serif',
                    //     fill: new Fill({ color: '#000' }),
                    //     stroke: new Stroke({ color: '#fff', width: 2 }),
                    //     textAlign: 'center',
                    //     textBaseline: 'middle',
                    //     overflow: true,
                    // }),
                }));

                // geometry.forEachSegment((start, end) => {
                //     const dx = end[0] - start[0];
                //     const dy = end[1] - start[1];
                //     const rotation = Math.atan2(dy, dx);
                //     const length = 0.0004;

                //     const leftWing = new LineString([
                //         end,
                //         [end[0] - length, end[1] + length],
                //     ]);
                //     leftWing.rotate(rotation, end);

                //     const rightWing = new LineString([
                //         end,
                //         [end[0] - length, end[1] - length],
                //     ]);
                //     rightWing.rotate(rotation, end);

                //     const arrowStroke = new Stroke({
                //         color: highlighted ? 'rgb(255, 255, 0)' : 'rgba(255, 0, 0, 0.8)',
                //         width: 4,
                //     });

                //     styles.push(new Style({ geometry: leftWing, stroke: arrowStroke }));
                //     styles.push(new Style({ geometry: rightWing, stroke: arrowStroke }));
                // });

                return styles;
            }

            return undefined;
        }
    }));

    return map;
}

function getVectorLayer(map: Map): VectorLayer<VectorSource> | null {
    const layers = map.getLayers().getArray();
    return layers.find(
        (layer): layer is VectorLayer<VectorSource> =>
            layer instanceof VectorLayer &&
            layer.getSource() instanceof VectorSource
    ) || null;
}

export function getFeature(map: Map, id: string): Feature | undefined {
    const source = getVectorLayer(map)?.getSource();
    if (source)
        return source.getFeatures().find(f => f.get('name') === id);
}

export function removeFeature(map: Map, id: string) {
    const source = getVectorLayer(map)?.getSource();
    if (source) {
        const feature = getFeature(map, id);

        if (feature)
            source.removeFeature(feature);
    }
}

export function clearFeatures(map: Map) {
    const source = getVectorLayer(map)?.getSource();
    if (source)
        source.clear();
}

export function highlightFeature(map: Map, id: string | null) {
    getVectorLayer(map)?.getSource()?.getFeatures().forEach((f) => {
        if (f.get('name') === id) f.set('highlighted', true);
    });
}

export function unhighlightFeature(map: Map, id: string | null) {
    getVectorLayer(map)?.getSource()?.getFeatures().forEach((f) => {
        if (f.get('name') === id) f.set('highlighted', false);
    });
}

export function fitFeaturesOnMap(map: Map, featureIds: string[]) {
    const source = getVectorLayer(map)!.getSource();
    if (!source) return;

    let combinedExtent: Extent | null = null;

    source.getFeatures().forEach((feature: Feature<Geometry>) => {
        const name = feature.get('name');
        if (featureIds.includes(name)) {
            const geom = feature.getGeometry();
            if (!geom) return;

            const extent = geom.getExtent();

            if (!combinedExtent) {
                combinedExtent = [...extent] as Extent;
            } else {
                extend(combinedExtent, extent);
            }
        }
    });

    if (combinedExtent && !isEmpty(combinedExtent)) {
        map.getView().fit(combinedExtent, {
            padding: [200, 200, 200, 200],
            duration: 500,
            maxZoom: 20,
        });
    }
}

export function polygonsOverlap(
    coordsA: [number, number][],
    coordsB: [number, number][]
): boolean {
    const close = (coords: [number, number][]) => {
        const first = coords[0];
        const last = coords[coords.length - 1];
        return first[0] === last[0] && first[1] === last[1] ? coords : [...coords, first];
    };

    const isOnEdge = (point: [number, number], polygon: Polygon): boolean => {
        const coords = polygon.getCoordinates()[0];
        for (let i = 0; i < coords.length - 1; i++) {
            const [x1, y1] = coords[i];
            const [x2, y2] = coords[i + 1];
            const [px, py] = point;

            const cross = (py - y1) * (x2 - x1) - (px - x1) * (y2 - y1);
            if (Math.abs(cross) > 1e-10) continue;

            const dot = (px - x1) * (px - x2) + (py - y1) * (py - y2);
            if (dot > 1e-10) continue;

            return true;
        }
        return false;
    }

    const polygonA = new Polygon([close(coordsA)]);
    const polygonB = new Polygon([close(coordsB)]);

    // Check if any point in A is strictly inside B
    for (const point of coordsA) {
        if (
            polygonB.intersectsCoordinate(point) &&
            !isOnEdge(point, polygonB)
        ) return true;
    }

    // Check if any point in B is strictly inside A
    for (const point of coordsB) {
        if (
            polygonA.intersectsCoordinate(point) &&
            !isOnEdge(point, polygonA)
        ) return true;
    }

    return false;
}

// Drawing functions

export function enablePlaceDrawing(map: Map) {
    const source = getVectorLayer(map)!.getSource()!;

    drawInteraction = new Draw({
        source,
        type: 'Polygon',
    });

    snapInteraction = new Snap({
        source,
    });

    map.addInteraction(drawInteraction);
    map.addInteraction(snapInteraction);

    drawInteraction.on('drawend', (event) => {
        const uid = "Place_" + Math.random().toString(36).substring(2, 15);
        const feature = event.feature;
        feature.set('name', uid);

        const place: PhysicalPlace = {
            id: uid,
            name: uid,
            coordinates: event.target.sketchLineCoords_,
            attributes: []
        };

        // TODO: fix this
        // if (useEnvStore.getState().physicalPlaces.some(p => polygonsOverlap(p.coordinates, place.coordinates))) {
        //     alert(`Place overlaps with existing place.`);
        //     setTimeout(() => removeFeature(map, uid), 0);
        // } else useEnvStore.getState().addPlace(place);
        useEnvStore.getState().addPlace(place);
    });
}

export function drawPlace(
    map: Map,
    id: string,
    coordinates: [number, number][]
) {
    const source = getVectorLayer(map)!.getSource()!;
    const feature = new Feature({
        geometry: new Polygon([[...coordinates]]),
        name: id,
    });

    source.addFeature(feature);
}

export function enableEdgeDrawing(map: Map) {
    const selectedPolygons: Feature[] = [];

    selectInteraction = new Select({
        condition: click,
        filter: (feature) => feature.getGeometry() instanceof Polygon
    });

    map.addInteraction(selectInteraction);

    selectInteraction.on('select', (e) => {
        const selected = e.selected[0];

        if (selected && !selectedPolygons.includes(selected)) {
            selectedPolygons.push(selected);
        }

        if (selectedPolygons.length === 2) {
            const [p1, p2] = selectedPolygons;
            const id1 = p1.getProperties().name;
            const id2 = p2.getProperties().name;

            if (!id1 || !id2) {
                console.warn('Missing IDs on features');
            } else {
                const uid = "Edge_" + Math.random().toString(36).substring(2, 15);
                const newEdge: Edge = {
                    id: uid,
                    name: uid,
                    source: id1,
                    target: id2,
                    attributes: []
                };

                if (!useEnvStore.getState().edges.some(edge => edge.source === newEdge.source && edge.target === newEdge.target)) {
                    useEnvStore.getState().addEdge(newEdge);
                    drawEdge(map, p1, p2, uid);
                } else alert(`Edge with same source and target already exists.`);
            }

            selectedPolygons.length = 0;
            selectInteraction?.getFeatures().clear();
        }
    });
}

export function drawEdge(map: Map, p1: Feature, p2: Feature, uid: string) {
    const center1 = (p1.getGeometry() as Polygon)?.getInteriorPoint().getCoordinates();
    const center2 = (p2.getGeometry() as Polygon)?.getInteriorPoint().getCoordinates();

    if (!center1 || !center2) return;

    const line = new Feature({
        geometry: new LineString([center1, center2])
    });
    line.set('name', uid);

    const arrow = new Feature({
        geometry: new Point(center2)
    });

    getVectorLayer(map)!.getSource()?.addFeatures([line, arrow]);
}

export function disableDrawing(map: Map) {
    if (drawInteraction) {
        map.removeInteraction(drawInteraction);
        drawInteraction = null;
    }

    if (snapInteraction) {
        map.removeInteraction(snapInteraction);
        snapInteraction = null;
    }

    if (selectInteraction) {
        map.removeInteraction(selectInteraction);
        selectInteraction = null;
    }
}
