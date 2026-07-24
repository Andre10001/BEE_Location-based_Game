import { useEffect, useRef } from 'react';
import Toolbar from './Toolbar';
import Sidebar from './sidebar/Sidebar';
import { useEnvStore } from '../envStore';
import { initMap } from '../utils';

const Map = () => {
    const mapRef = useRef<HTMLDivElement | null>(null);
    const mapInstance = useEnvStore((state) => state.mapInstance);
    const setMapInstance = useEnvStore((state) => state.setMapInstance);
    const isEditable = useEnvStore((state) => state.isEditable);

    useEffect(() => {
        if (!mapRef.current) return;

        setMapInstance(
            initMap(mapRef.current)
        );

        return () => mapInstance.setTarget(undefined);
    }, []);

    return (
        <div className="map-wrapper">
            <div id="map" ref={mapRef} className="map" />
            {isEditable && <Toolbar />}
            <Sidebar />
        </div>
    );
};

export default Map;