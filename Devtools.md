# DevTools

There are console, monitor, and command tool available.

## Console

TBA

## Monitor

TBA

## Commands

### Implementation

All command implementations are located under the package `src/main/kotlin/devtools/cmd`.

In short, dev can create command by implementing the `Command<T>` interface. They have to define `commandId`, description, required arguments, and the execution logic inside the `execute` method. They should also register the command implementation with `CommandDispatcher.register` from `ServerContext` (typically in `Application.kt`).

Consult the code directly for implementation details. Help and usage details are available in the DevTools itself.
