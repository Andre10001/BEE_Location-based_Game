import LogicalLayer from './logical/LogicalLayer';
import PhysicalLayer from './physical/PhysicalLayer';

const Sidebar = () => {
  return (
    <div className="sidebar bg-dark text-white">
      <LogicalLayer />
      <PhysicalLayer />
    </div>
  );
};

export default Sidebar;