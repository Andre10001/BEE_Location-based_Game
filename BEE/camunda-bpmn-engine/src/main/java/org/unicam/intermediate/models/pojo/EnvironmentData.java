package org.unicam.intermediate.models.pojo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class EnvironmentData {
    private List<PhysicalPlace> physicalPlaces;
    private List<Edge> edges;
    private List<LogicalPlace> logicalPlaces;
    private List<View> views;
}
