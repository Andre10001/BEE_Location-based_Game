import { useEnvStore } from '../envStore';

const Header = () => {
  const clearModel = useEnvStore((state) => state.clearModel);
  const setModel = useEnvStore((state) => state.setModel);

  const handleImport = () => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.json';
    input.onchange = (e) => {
      const file = (e.target as HTMLInputElement).files?.[0];
      if (file) {
        const reader = new FileReader();
        reader.onload = (event) => {
          try {
            const model = JSON.parse(event.target?.result as string);
            setModel(model);
          } catch (error) {
            console.log('Error parsing JSON:', error);
          }
        };
        reader.readAsText(file);
      }
    };
    input.click();
  };

  const handleExport = () => {
    const model = {
      physicalPlaces: useEnvStore.getState().physicalPlaces,
      edges: useEnvStore.getState().edges.map((edge) => {
        edge.name = useEnvStore.getState().physicalPlaces.find(place => place.id === edge.source)?.name + '>' +
          useEnvStore.getState().physicalPlaces.find(place => place.id === edge.target)?.name;
        return edge;
      }),
      logicalPlaces: useEnvStore.getState().logicalPlaces,
      views: useEnvStore.getState().views,
    };
    const blob = new Blob([JSON.stringify(model, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'model.json';
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <nav className="navbar navbar-dark bg-dark px-3 d-flex justify-content-between align-items-center">
      <span className="navbar-brand mb-0 h1">🌎 BPEnv Modeler</span>
      <div className="btn-group">
        <button className="btn btn-outline-light btn-sm" onClick={handleImport}>
          ⬆️
        </button>
        <button className="btn btn-outline-light btn-sm" onClick={handleExport}>
          ⬇️
        </button>
        <button className="btn btn-outline-light btn-sm" onClick={() => {
          if (confirm('Are you sure you want to clear the model?'))
            clearModel();
        }}>
          🗑️
        </button>
      </div>
    </nav>
  );
};

export default Header;