import { useState } from 'react';
import { useEnvStore } from '../../../envStore';
import PhysicalElement from './PhysicalElement';

const PhysicalLayer = () => {
    const [isPlacesOpen, setIsPlacesOpen] = useState(true);
    const [isEdgesOpen, setIsEdgesOpen] = useState(true);

    const places = useEnvStore((state) => state.physicalPlaces);
    const edges = useEnvStore((state) => state.edges);

    return (
        <div className="mb-3">
            <h5 className="text-white mb-3">Physical Layer</h5>

            <button
                className="btn btn-outline-light w-100 text-start mb-2"
                onClick={() => setIsPlacesOpen(!isPlacesOpen)}
            >
                {isPlacesOpen ? '▼' : '▶'} Places
            </button>
            {isPlacesOpen && (
                places.length === 0 ? (
                    <h6 className="text-secondary mb-3">Nothing to see here...</h6>
                ) : (
                    <ul className="list-group list-group-flush mb-3">
                        {places.map((p) => (
                            <PhysicalElement key={p.id} element={p} type="place" />
                        ))}
                    </ul>
                )
            )}

            <button
                className="btn btn-outline-light w-100 text-start mb-2"
                onClick={() => setIsEdgesOpen(!isEdgesOpen)}
            >
                {isEdgesOpen ? '▼' : '▶'} Edges
            </button>
            {isEdgesOpen && (
                edges.length === 0 ? (
                    <h6 className="text-secondary mb-3">Nothing to see here...</h6>
                ) : (
                    <ul className="list-group list-group-flush">
                        {edges.map((e) => (
                            <PhysicalElement key={e.id} element={e} type="edge" />
                        ))}
                    </ul>
                ))}
        </div>
    );
};

export default PhysicalLayer;