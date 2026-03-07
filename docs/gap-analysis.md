# Gap Analysis: VBA Macro Parity

The original `Format Shearwater.xlsm` VBA macros produce ~47 output columns with advanced dive state computation. With all output plugins enabled, the Kotlin implementation outputs 49 columns (24 base + 16 TechnicalOC + 8 TechnicalCCR + 1 SafetyStop), covering all major VBA features plus additional metadata.

The following features from the VBA macros are not yet implemented, listed by priority.

## Medium priority

**Placeholder values (`" "` vs `""`)** — VBA uses `~` during processing, then replaces with `" "` (a quoted space) in the final CSV. Telemetry  treats `" "` as a valid blank value but may skip or misrender empty strings `""`. The current implementation outputs empty strings.
May not implement. There's no mention anywhere of how empty strings "" are handled in text columns vs a quoted space " " by Telemetry . The Manual spec only says:
"Text columns should not contain commas."

## Low priority

**Depth decomposition** — Separate columns for depth indicator (`"ft"`/`"m"`), whole number, and decimal part. 
Will not implement. Units can be auto converted in Telemetry  when using "Custom Mini" gauge. Additional decimal value may be displayed as well.  

**Temperature unit indicator** — Per-row `"F"` or `"C"` column.
Will not implement.

**Trailing empty column stripping** — VBA strips rows that consist entirely of trailing commas.
Is not a problem yet, not implemented. 
