# The Converter

The converter is the step in the data processing pipeline responsible for transforming the standardized `DiveLog` ADT into a set of tabular columns for the final output file.

Its primary responsibilities are:

1.  **Generating Column Headers**: It creates a standard set of 24 column headers, appending the correct unit suffixes (e.g., `(ft)` vs. `(m)`, `(psi)` vs. `(bar)`) based on the `DiveMetadata` from the `DiveLog`.
2.  **Formatting Data**: It transforms the data from the `DiveSample` list into string representations suitable for a CSV file. This includes key transformations like:
    *   Negating depth values (e.g., `30.5` becomes `-30.5`).
    *   Formatting numeric values to a consistent number of decimal places.
    *   Handling `null` values from the domain model and representing them as empty strings in the output.
3.  **Producing a Base Telemetry Set**: It generates the foundational set of 24 columns that form the core of the final output.

This step acts as the bridge between the pure, typed domain model of the dive and the specific, formatted representation required by Telemetry Overlay.

---

## Interaction with Output Plugins

While the pipeline diagram shows a simple linear flow, the interaction between the converter and the `OutputPlugin` chain is a parallel process:

```
                          -> convert -> [Base 24 Columns]   \
[DiveLogPlugin chain] -> DiveLog                             -> merge -> [Final Columns] -> write
                          -> [OutputPlugin chain] -> [Custom Columns]  /
```

1.  The final `DiveLog` object (after all `DiveLogPlugin` transformations) is used as the input for both the converter and the `OutputPlugin` chain.
2.  The converter processes the `DiveLog` to create the base 24 columns.
3.  Simultaneously, each `OutputPlugin` in the chain processes the *exact same* `DiveLog` to generate its own set of custom columns.
4.  Finally, the pipeline merges the converter's base columns with the columns from all output plugins to produce the final set of columns that gets written to the output file.

`OutputPlugin`s do **not** receive the output of the converter; they work on the same input data to ensure all calculations are based on the same source of truth.

---

## Base Output Columns

The converter generates the following 24 columns. The unit suffixes (`$depthSuffix`, `$tempSuffix`, `$pressureSuffix`) are dynamically replaced based on the dive's metadata.

1.  `Time (s)`
2.  `Actual Depth ($depthSuffix)`
3.  `First Stop Depth`
4.  `Time To Surface`
5.  `Average PP02 (text)`
6.  `Fraction O2 (text)`
7.  `Fraction He (text)`
8.  `First Stop Time`
9.  `Current NDL`
10. `Current Circuit Mode`
11. `Water Temp ($tempSuffix)`
12. `Gas Switch Needed`
13. `External PPO2`
14. `Tank 1 pressure ($pressureSuffix)`
15. `Tank 2 pressure ($pressureSuffix)`
16. `Tank 3 pressure ($pressureSuffix)`
17. `Tank 4 pressure ($pressureSuffix)`
18. `Seconds`
19. `Display Minutes (text)`
20. `Display Seconds (text)`
21. `Max Depth (text)`
22. `Actual Time (text)`
23. `AM/PM Indicator (text)`
24. `Gas Mixture (text)`
