package org.unicam.intermediate.models.pojo;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class Condition {
    private String attribute;
    private String operator;
    private Object value;
}
