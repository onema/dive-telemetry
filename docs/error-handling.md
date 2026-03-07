# Error Handling

The library uses [Arrow-kt](https://arrow-kt.io/)'s `Raise` DSL for typed error handling. All pipeline operations return `Either<PipelineError, Unit>` — no exceptions are thrown for expected failures.

## Error hierarchy

```
PipelineError (interface)
├── ParseError (sealed)
│   ├── UnexpectedEof(expectedRow: String)
│   ├── MissingColumns(missing: List<String>)
│   ├── InvalidFitFile(reason: String)
│   └── MissingFitData(field: String)
├── WriteError (sealed)
│   └── IoFailure(cause: String)
└── PluginError (sealed)
    └── ExecutionError(message: String)
```

`PipelineError` is a plain interface so that `PluginError` (in the `plugins` package) can implement it alongside `ParseError` and `WriteError` (in the `error` package). Kotlin sealed interfaces cannot be extended across packages. `ParseError` and `WriteError` remain sealed within the `error` package.

## Handling errors at the call site

```kotlin
transformDiveLog(source, sink, ComputerType.SHEARWATER).fold(
    ifLeft = { error ->
        when (error) {
            is ParseError.MissingColumns  -> println("Bad input: ${error.message}")
            is ParseError.InvalidFitFile  -> println("Not a valid FIT file: ${error.message}")
            is PluginError.ExecutionError -> println("Plugin failed: ${error.message}")
            is WriteError.IoFailure       -> println("Could not write output: ${error.message}")
            else                          -> println("Error: ${error.message}")
        }
    },
    ifRight = { println("Success") },
)
```

## Raising errors inside parsers and plugins

Functions that can fail declare a `Raise<E>` receiver and call `raise()` to signal failure. Arrow's `either { }` builder captures raises and wraps them in `Either.Left`.

```kotlin
// Inside a parser:
fun Raise<ParseError>.parse(source: BufferedSource): DiveLog {
    val line = source.readUtf8Line() ?: raise(ParseError.UnexpectedEof("header row"))
    // ...
}

// Inside a plugin:
fun Raise<PluginError>.transform(diveLog: DiveLog): DiveLog {
    if (diveLog.samples.isEmpty())
        raise(PluginError.ExecutionError("Dive log has no samples"))
    // ...
}
```

`raise()` is non-local — it immediately exits the current `Raise` scope without exceptions. This works on the JVM and on Kotlin/Native.
