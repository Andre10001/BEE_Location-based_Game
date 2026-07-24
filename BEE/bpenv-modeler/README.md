# BPEnv Modeler



BPEnv Modeler is a visual modeling tool based on React, designed to define and manage environment models used in environmental-aware business process modeling. It provides an interactive map interface to define physical and logical places, edges, and views.

The modeler can be integrated into other applications and offers an API to interact programmatically with the model.

---

📦 **Installation**

Install the package via NPM:

```bash
npm install bpenv-modeler
```

---

🚀 **Usage**

To render the modeler in a DOM element:

```js
import bpenvModeler from 'bpenv-modeler';

bpenvModeler.render('container-id');
```

Use `headless: true` if you want to display only the map without UI controls.

---

🧠 **API**

Make sure to call `render()` before using the following methods.

- `getPhysicalPlaces(): PhysicalPlace[]`  
    Returns the list of physical places in the model.

- `getEdges(): Edge[]`  
    Returns all edges defined between physical places.

- `getLogicalPlaces(): LogicalPlace[]`  
    Returns all logical places, defined by conditions on the attributes of physical places.

- `getViews(): View[]`  
    Returns all logical views (groups of logical places).

- `getModel(): { places, edges, logicalPlaces, views }`  
    Returns the complete current model as an object.

- `setModel(model: { places, edges, logicalPlaces, views })`  
    Replaces the current model with the provided one.

- `isEditable(): boolean`  
    Indicates whether the model is currently editable.

- `setEditable(flag: boolean)`  
    Enables or disables edit mode.

---

🛠️ **Development**

When running in development mode (`!import.meta.env.PROD`), the modeler will automatically render itself in a container with ID `bpenv-container`.
