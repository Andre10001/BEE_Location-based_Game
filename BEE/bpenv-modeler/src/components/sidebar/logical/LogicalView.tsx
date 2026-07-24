import { useState } from 'react';
import { View } from '../../../envTypes';
import { useEnvStore } from '../../../envStore';
import LogicalPlace from './LogicalPlace';
import LogicalViewEditor from './LogicalViewEditor';

const LogicalView = ({ view }: { view: View }) => {
    const [isOpen, setIsOpen] = useState(false);
    const [isEditing, setIsEditing] = useState(false);

    const views = useEnvStore((state) => state.views);
    const removeView = useEnvStore((state) => state.removeView);
    const logicalPlaces = useEnvStore((state) => state.logicalPlaces);
    const removeLogicalPlace = useEnvStore((state) => state.removeLogicalPlace);
    const isEditable = useEnvStore((state) => state.isEditable);

    const placesInView = logicalPlaces.filter((lp) =>
        view.logicalPlaces.includes(lp.id)
    );

    return (
        <div className="mb-2">
            <div
                className="btn btn-outline-light w-100 text-start d-flex justify-content-between align-items-center"
                onClick={() => setIsOpen(!isOpen)}
                style={{ cursor: 'pointer' }}
            >
                <span>{isOpen ? '▼' : '▶'} {view.name}</span>
                <div className="btn-group btn-group-sm">
                    <button
                        className="btn btn-sm btn-outline-light p-1 me-1"
                        onClick={(e) => {
                            e.stopPropagation();
                            setIsEditing(true);
                        }}
                        title="Edit view"
                        hidden={!isEditable}
                    >
                        ✎
                    </button>
                    <button
                        className="btn btn-outline-danger p-1"
                        onClick={(e) => {
                            e.stopPropagation();
                            removeView(view.id);
                            view.logicalPlaces.forEach((lp) => {
                                const usedElsewhere = views.some(
                                    (v) => v.id !== view.id && v.logicalPlaces.includes(lp)
                                );

                                if (!usedElsewhere) removeLogicalPlace(lp);
                            });
                        }}
                        title="Delete View"
                        hidden={!isEditable}
                    >
                        x
                    </button>
                </div>
            </div>

            {isOpen && (
                <ul className="list-group list-group-flush">
                    {placesInView.map((lp) => (
                        <LogicalPlace key={lp.id} item={lp} />
                    ))}
                </ul>
            )}

            {isEditing && (
                <LogicalViewEditor
                    initialView={view}
                    onClose={() => setIsEditing(false)}
                />
            )}
        </div>
    );
};

export default LogicalView;