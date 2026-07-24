import { useState } from 'react';
import { useEnvStore } from '../../../envStore';
import LogicalPlaceEditor from './LogicalPlaceEditor';
import Modal from '../../shared/Modal';
const LogicalViewEditor = ({
  onClose,
  initialView,
}: {
  onClose: () => void;
  initialView?: { id: string; name: string; logicalPlaces: string[]; aggregations: Record<string, string> };
}) => {
  const [viewName, setViewName] = useState<string>(initialView?.name ?? '');
  type Aggregation = { attribute: string; operation: string };
  const [viewAggregations, setViewAggregations] = useState<Aggregation[]>(
    initialView
      ? Object.entries(initialView.aggregations).map(([attribute, operation]) => ({ attribute, operation }))
      : [{ attribute: '', operation: 'MIN' }]
  );
  const [selectedLogicalPlaceIds, setSelectedLogicalPlaceIds] = useState<string[]>(
    initialView?.logicalPlaces ?? []
  );

  const addView = useEnvStore((state) => state.addView);
  const logicalPlaces = useEnvStore((state) => state.logicalPlaces);
  const views = useEnvStore((state) => state.views);
  const allAttributes = [
    ...new Set(useEnvStore.getState().physicalPlaces.flatMap((p) => Object.keys(p.attributes))),
  ];

  const addAggregation = () =>
    setViewAggregations([...viewAggregations, { attribute: '', operation: 'MIN' }]);

  const removeAggregation = (index: number) =>
    setViewAggregations(viewAggregations.filter((_, i) => i !== index));

  const updateAggregation = (index: number, updates: Partial<Aggregation>) =>
    setViewAggregations(
      viewAggregations.map((agg, i) => (i === index ? { ...agg, ...updates } : agg))
    );

  const handleCheckboxChange = (id: string) => {
    setSelectedLogicalPlaceIds((prev) =>
      prev.includes(id) ? prev.filter((pid) => pid !== id) : [...prev, id]
    );
  };

  const handleSaveView = () => {
    const updatedView = {
      id: initialView?.id ?? 'View_' + Math.random().toString(36).substring(2, 8),
      name: viewName,
      logicalPlaces: selectedLogicalPlaceIds,
      aggregations: Object.fromEntries(
        viewAggregations
          .filter((agg) => agg.attribute && agg.operation)
          .map((agg) => [agg.attribute, agg.operation])
      ),
    };

    if (initialView) {
      const updatedViews = views.map((v) => (v.id === updatedView.id ? updatedView : v));
      useEnvStore.setState({ views: updatedViews });
    } else {
      addView(updatedView);
    }

    useEnvStore.setState((state) => ({
      logicalPlaces: state.logicalPlaces.map((lp) => {
        if (!selectedLogicalPlaceIds.includes(lp.id)) return lp;

        return {
          ...lp,
          attributes: {
            ...viewAggregations
              .filter((agg) => agg.attribute && agg.operation)
              .reduce((acc, agg) => {
                acc[agg.attribute] = agg.operation;
                return acc;
              }, {} as Record<string, any>)
          }
        };
      })
    }));

    onClose();
  };


  return (
    <Modal
      title={initialView ? 'Edit View' : 'Create New View'}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            Close
          </button>
          <button
            type="button"
            className="btn btn-success"
            onClick={handleSaveView}
            disabled={
              !viewName.trim() ||
              selectedLogicalPlaceIds.length === 0 ||
              views.some(
                (v) => v.name === viewName && v.id !== initialView?.id
              )
            }
          >
            Save View
          </button>
        </>
      }
    >
      <div className="mb-3">
        <input
          className="form-control"
          placeholder="View Name"
          value={viewName}
          onChange={(e) => setViewName(e.target.value)}
        />
      </div>

      <div>
        <div className="mb-3">
          <label className="form-label">Aggregations</label>

          {viewAggregations.map((agg, i) => (
            <div key={i} className="d-flex align-items-center gap-2 mb-2">

              {/* Select attributo */}
              <select
                className="form-select"
                style={{ width: '50%' }}
                value={agg.attribute}
                onChange={(e) => updateAggregation(i, { attribute: e.target.value })}
              >
                <option value="">Select attribute</option>
                {allAttributes.map((attr) => (
                  <option key={attr} value={attr}>
                    {attr}
                  </option>
                ))}
              </select>

              {/* Select operazione */}
              <select
                className="form-select"
                style={{ width: '30%' }}
                value={agg.operation}
                onChange={(e) => updateAggregation(i, { operation: e.target.value })}
              >
                <option value="MIN">MIN</option>
                <option value="MAX">MAX</option>
                <option value="AVG">AVG</option>
                <option value="SUM">SUM</option>
                <option value="OR">OR</option>
                <option value="AND">AND</option>
              </select>

              <button
                className="btn btn-outline-danger btn-sm"
                onClick={() => removeAggregation(i)}
              >
                -
              </button>
            </div>
          ))}

          <button className="btn btn-sm btn-outline-light w-100 mt-2" onClick={addAggregation}>
            Add Aggregation
          </button>
        </div>
      </div>

      <div className="mb-3">
        <label className="form-label">Select Logical Places</label>
        {logicalPlaces.map((lp) => (
          <div key={lp.id} className="form-check">
            <input
              className="form-check-input"
              type="checkbox"
              checked={selectedLogicalPlaceIds.includes(lp.id)}
              onChange={() => handleCheckboxChange(lp.id)}
              id={`lp-${lp.id}`}
            />
            <label className="form-check-label" htmlFor={`lp-${lp.id}`}>
              {lp.name}
            </label>
          </div>
        ))}
      </div>

      <div className="mb-3">
        <label className="form-label">Create New Logical Place</label>
        <LogicalPlaceEditor />
      </div>
    </Modal>
  );
}

export default LogicalViewEditor;