import { FaHandPaper, FaRegCircle, FaArrowRight } from 'react-icons/fa';
import { useEnvStore } from '../envStore';

const Toolbar = () => {
    const activeTool = useEnvStore((state) => state.activeTool);
    const mapInstance = useEnvStore((state) => state.mapInstance);
    const setActiveTool = useEnvStore((state) => state.setActiveTool);

    const handleHandTool = () => {
        if (mapInstance) setActiveTool('hand');
    };

    const handlePlaceTool = () => {
        if (mapInstance) setActiveTool('place');
    };

    const handleEdgeTool = () => {
        if (mapInstance) setActiveTool('edge');
    };

    return (
        <div className="toolbar draggable">
            <button className={activeTool === 'hand' ? "toolbar-btn" : "toolbar-btn-selected"} onClick={handleHandTool}><FaHandPaper /></button>
            <button className={activeTool === 'place' ? "toolbar-btn" : "toolbar-btn-selected"} onClick={handlePlaceTool}><FaRegCircle /></button>
            <button className={activeTool === 'edge' ? "toolbar-btn" : "toolbar-btn-selected"} onClick={handleEdgeTool}><FaArrowRight /></button>
        </div>
    );
};

export default Toolbar;