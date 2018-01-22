# Cached File task in SBT

An attempt to write a function that takes a `Task[Set[File]]` (or a `TaskKey` for one) and produces another `Task[Set[File]]`, wrapping it in `FileFunction.cached` so that the original task is only invoked according to the contract in `FileFunction.cached`.

The goal is to wrap a task that invokes a Main class that is part of the project and produces files (could be sources, or something like Swagger documentation), and to invoke said task whenever `compile` is run, but only when a certain set of input files have changed.

In point form:

1. Have a normal main class in the project that does something
2. Define a task that runs that main class, using `runMain`
3. Use a function to Wrap the task, invoking it only through `FileFunction.cached`, producing another Task
4. Define the Task as being `triggeredBy` `compile in Compile.

## Problem

Currently, the code in `master` enters an infinite loop, continuously invoking the `runMain` task, possibly because `runMain` depends on `compile`, which triggers the task that invokes `runMain`, which depends on `compile`, etc etc.

<h5 id="repro">To reproduce</h5>, simply run `sbt compile`.

However, there is a workaround in the branch called `workaround`, but it doesn't seem ideal.