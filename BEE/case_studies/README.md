# BPM26 Case Studies

This folder contains four case-study scenarios, one per domain, each including:

- one or more BPMN files (`.bpmn`) with process models
- one environment file (`*.json`) with places, edges, logical places, and views
- one or more log files (`.log`) with execution traces

## Structure

```text
BPM26_case_studies/
├── city/
│   ├── city.json
│   ├── rental.bpmn
│   ├── rental-fix.bpmn
│   ├── rental.log
│   └── rental-fix.log
├── emergency /
│   ├── emergency.json
│   ├── firefighters-firecontrolsystem.bpmn
│   ├── firefighters-firecontrolsystem.log
│   ├── nurse-patient-ambulance.bpmn
│   └── nurse-patient-ambulance.log
├── farm/
│   ├── farm.json
│   ├── planter-farmer.bpmn
│   ├── planter-farmer.log
│   ├── sprinkler-farmer.bpmn
│   └── sprinkler-farmer.log
└── university/
    ├── university.json
    ├── attendant-supervisor.bpmn
    ├── attendant-supervisor.log
    ├── student-librarian.bpmn
    ├── student-librarian.log
    ├── student-tutor.bpmn
    └── student-tutor.log
```

## Notes

- `city/`: urban scenario (for example, vehicle rental/return).
- `farm/`: agricultural scenario (field preparation, planting, irrigation).
- `university/`: university scenario (students, tutor, library).
- `emergency/`: emergency scenario (nurse/ambulance, fire control).