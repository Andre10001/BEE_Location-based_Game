import { useState } from 'react';
import LogicalView from './LogicalView';
import LogicalViewEditor from './LogicalViewEditor';
import { useEnvStore } from '../../../envStore';

const LogicalLayer = () => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const isEditable = useEnvStore((state) => state.isEditable);
  const views = useEnvStore((state) => state.views);

  return (
    <div className="mb-3">
      <h5 className="text-white mb-3">Logical Layer</h5>

      {views.length === 0 ? (
        <h6 className="text-secondary mb-3">Nothing to see here...</h6>
      ) : (
        views.map((view) => <LogicalView key={view.id} view={view} />)
      )}

      {isEditable && (
        <button
          className="btn btn-outline-light w-100 mt-3"
          onClick={() => setIsModalOpen(true)}
        >
          + Add View
        </button>
      )}

      {isModalOpen && (
        <LogicalViewEditor onClose={() => setIsModalOpen(false)} />
      )}
    </div>
  );
};

export default LogicalLayer;