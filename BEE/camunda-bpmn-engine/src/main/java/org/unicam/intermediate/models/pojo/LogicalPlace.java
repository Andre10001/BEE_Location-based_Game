package org.unicam.intermediate.models.pojo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class LogicalPlace {
    private String id;
    private String name;
    private List<Condition> conditions;
    private String expression;
    private String place;
}
