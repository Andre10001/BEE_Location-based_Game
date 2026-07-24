export type PhysicalPlace = {
    id: string;
    name: string;
    coordinates: [number, number][];
    attributes: Record<string, any>;
}

export type Edge = {
    id: string;
    name: string;
    source: string;
    target: string;
    attributes: Record<string, any>;
}

export type LogicalPlace = {
    id: string;
    name: string;
    conditions: {
        attribute: string,
        operator: string,
        value: any
    }[],
    operator: string,
    attributes: Record<string, any>;
}

export type View = {
    id: string;
    name: string;
    logicalPlaces: string[];
    aggregations: Record<string, string>;
}