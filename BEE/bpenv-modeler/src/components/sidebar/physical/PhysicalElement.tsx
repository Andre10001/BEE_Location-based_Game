import { useState, useRef } from 'react';
import { PhysicalPlace, Edge } from '../../../envTypes';
import { useEnvStore } from '../../../envStore';
import { BsTrash } from 'react-icons/bs';
import PhysicalAttributes from './PhysicalAttributes';
import { highlightFeature, unhighlightFeature, fitFeaturesOnMap } from '../../../utils';

type Props = {
  element: PhysicalPlace | Edge;
  type: 'place' | 'edge';
};

const PhysicalElement = ({ element, type }: Props) => {
  const [newName, setNewName] = useState(element.name || '');
  const mapInstance = useEnvStore((state) => state.mapInstance);
  const isEditable = useEnvStore((state) => state.isEditable);
  const hoverTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const updateElement =
    type === 'place'
      ? useEnvStore((state) => state.updatePlace)
      : useEnvStore((state) => state.updateEdge);

  const removeElement =
    type === 'place'
      ? useEnvStore((state) => state.removePlace)
      : useEnvStore((state) => state.removeEdge);

  const handleNameBlur = () => {
    const trimmed = newName.trim();
    if (trimmed === '' || trimmed === element.name) return;

    const list =
      type === 'place'
        ? useEnvStore.getState().physicalPlaces
        : useEnvStore.getState().edges;

    const isDuplicate = list.some(
      (el) => el.name === trimmed && el.id !== element.id
    );

    if (isDuplicate)
      setNewName(element.name);
    else updateElement?.(element.id, { name: trimmed });
  };

  const handleDelete = () => {
    if (
      confirm(
        `Are you sure you want to delete ${element.name}?`
      )
    ) {
      removeElement(element.id);
    }
  };

  return (
    <li
      className="list-group-item bg-dark text-white"
      onMouseEnter={() => {
        highlightFeature(mapInstance, element.id);

        // hoverTimeoutRef.current = setTimeout(() => {
        //   fitFeaturesOnMap(mapInstance, [element.id]);
        // }, 2000);
      }}
      onMouseLeave={() => {
        unhighlightFeature(mapInstance, element.id);

        // if (hoverTimeoutRef.current) {
        //   clearTimeout(hoverTimeoutRef.current);
        //   hoverTimeoutRef.current = null;
        // }
      }}
    >
      <div className="d-flex justify-content-between align-items-center">
        <input
          className="form-control form-control-md border-0 bg-transparent text-white p-0 custom-placeholder no-focus-outline"
          value={newName}
          placeholder={type === 'place' ? 'Place Name' : 'Edge Name'}
          onChange={(e) => setNewName(e.target.value)}
          onBlur={handleNameBlur}
          disabled={!isEditable}
          spellCheck={false}
        />
        <div className="btn-group btn-group-sm">
          <button
            className="btn btn-outline-light p-1 me-1"
            onClick={() => {
              const updatedAttributes = { ...element.attributes };
              updatedAttributes['key'] = 'value';
              updateElement?.(element.id, { attributes: updatedAttributes });
            }}
            title="Add attribute"
            disabled={
              Object.keys(element.attributes).some((key) => key === '' || key === 'key')
            }
            hidden={!isEditable}
          >
            +
          </button>
          <button
            className="btn btn-outline-danger p-1"
            onClick={handleDelete}
            title="Delete"
            hidden={!isEditable}
          >
            <BsTrash />
          </button>
        </div>
      </div>

      <PhysicalAttributes elementId={element.id} type={type} initialAttributes={element.attributes} />
    </li>
  );
};

export default PhysicalElement;