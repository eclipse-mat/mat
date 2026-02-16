# Extending Memory Analyzer

## Introduction

The Memory Analyzer tool offers several possibilities to extend it. This page
contains an overview of the different extension points and what can be achieved
with them.

Within the extensions one will usually extract certain pieces of information
from the objects in the heap dump. See [Reading data from heap dumps](Reading_data_from_heap_dumps.md)
for more details on how to read data from a heap dump.

## Setting up a development environment for writing extensions

It is not necessary to download the source of Memory Analyzer to be able to
write extensions. A recent binary version is sufficient.

### Development environment

1. Have a copy of an Eclipse Java Development environment installed
2. Download and install latest copy of Memory Analyzer

### Target platform setup

Create MAT as a target platform:
1. Windows->Preferences->Plug-in Development->Target Platform
2. Add->Nothing->Next
3. Name: MAT
4. Locations->Add->Installation
5. Location: path_to_MAT/mat
6. Finish
7. Select MAT as active target platform

## Creating a plug-in project

1. File->New->Other->Plug-in project
2. Name: MAT Extension
3. ->Next
4. Execution Environment: (pick current Java generation)
5. No activator (unless you are doing something complicated)
6. No UI contribution
7. No API analysis
8. No template
9. ->Finish

### Configure depedencies

1. add `org.eclipse.mat.api` (to allow use of the API)
2. Save (cntl-S)

### Configure extensions

1. select `org.eclipse.mat.api.nameResolver` (to integrate with extensions)
2. ->Finish
3. click on impl
4. Adjust package name and class name to suit
5. ->Finish
6. Add before the class definition an annotation,

    ```java
    @Subject("java.lang.Runtime")
    ```

7. Organize imports (cntl-shift-O)
8. Edit the code to perform the required function. For example

    ```java
    public String resolve(IObject object) {
        // return null;
        return "The Java runtime of size " + object.getUsedHeapSize();
    }
    ```

9. Save

Tip - If using Eclipse, note the javadoc help for `IObject`,
`IClassSpecificNameResolver`. Note the method list for object.

### To test

Select Plug-in, Run As->Eclipse Application.

### To package

1. File->Export->Plug-in Development->Deployable plug-ins and fragments
2. ->next
3. select plug-in
4. Destination: Directory: path_to_MAT/mat
5. ->Finish

## The Name Resolver Extension

The name resolver extension point provides a mechanism to give a readable
description of an object, similar to what a `toString()` method will do. Some
extensions which MAT provides are to show the content of objects representing
String, to show the bundle symbolic name for Equinox classloaders, to show the
thread name for Thread objects, etc. You can create and contribute your own
resolvers.

The extension should implement the `IClassSpecificNameResolver` interface which
defines a single method.

```java
public String resolve(IObject object) throws SnapshotException;
```

The method takes an `IObject` as an argument and should return a string
representation.

To specify the class for which the resolver should be used one can use the
annotation `@Subject`.

```java
@Subject("x.y.z.MyClass")
public class MyClassResolver implements IClassSpecificNameResolver {
    public String resolve(IObject obj) throws SnapshotException {
        // implementation
    }
}
```

The method `getClassSpecificName()` of `IObject` will look for extensions which
match the class of the object and execute the `resolve()` method to return the
resolved String. Thus it is relatively easy to return a description based on one
or more String fields, as strings are already resolved.

Here is a sample implementation that will return the name of an Eclipse Job:

```java
@Subject("org.eclipse.core.runtime.jobs.Job")
public class JobNameResolver implements IClassSpecificNameResolver
{
    @Override
    public String resolve(IObject object) throws SnapshotException
    {
        IObject name = (IObject) object.resolveValue("name");
        if (name != null) return name.getClassSpecificName();
        return null;
    }
}
```

## Queries in Memory Analyzer

### Introduction to Queries

Most of the functionality in Memory Analyzer which is exposed to the user of the
tool is provided via queries implementing the `IQuery` interface, for example
"Histogram", "Retained Set", etc.

Queries extract and process data from the heap dump using the MAT API, and
provide the result to the user in the form of a table, a tree, free text, etc.
Queries show up in the "Queries" menu of the tool, and often in the context
menus on objects.

An important feature of the queries is that they can "collaborate", i.e. the
user can use (part of) the result of one query and pass it as input parameters
to another query.

An example of such "cooperation" - you select "Histogram" to show a class
histogram of all objects, then choose say `java.util.HashMap`. From the context
menu call "Retained Set". The retained set is using as input the results of the
histogram selection. You can then select a line in this retained set and pass
the corresponding objects to yet another query.

### The `IQuery` Interface

To implement a query one needs to implement the `IQuery` interface. The
implementation should provide a (default) constructor without parameters.

The IQuery interface defines just one method:

```java
public IResult execute(IProgressListener listener) throws Exception;
```

As a parameter one gets only a progress listener `IProgressListener` to report
progress. All other input that a query needs is deaclared with annotations on
the fields of the query and injected from Memory Analyzer at runtime. The
fields used for arguments injection should be declared public.

For more details on getting input, see the Passing Arguments section (below).

The return type of the execute method is `IResult`, which is a marker interface.
The different result types are described in the section Query Results (below).

### Query Scope

Queries are stateless. Every time the user executes a query a new instance of
the `IQuery` implementation is created. The required input is injected into the
fields of the instance and the execute method is called.

### Describing the Query with Annotations

When you write a query it will appear in the context menus, and Memory Analyzer
will open an Arguments Wizard for specifying the required arguments. The wizard
will also show some help for the query and its arguments.

The metadata - how a query will be named, under which category (sub-menu) it
will appear, the help text, etc are all provided by annotating the query.

The following meta-data related annotations are available and should be at the
`Class` level for the query.

| Annotation | Description |
|------------|-------------|
| @CommandName | used for command line and query browser |
| @Name | the visible name on the menu, `nn\|` to set order |
| @Category | the menu section (sub-menu), `/` to cascade, `nn\|` to set order |
| @Help | explanation of query |
| @HelpUrl | link into the help system |
| @Icon | icon for the query (shown in the menu) |
| @Usage | example usage – defaults to command name + args |

The values can also be externalized. To do so, put them into an
`annotations.properties` file in the package directory.

#### Example annotations

```java
@Category("Sample Queries")
@Name("List Jobs Query")
@Help("This is a sample query, which lists all jobs with a given name")
public class SampleQuery implements IQuery {
    ...
}
```

To externalize these values, `annotations.properties` will look something like:

```properties
SampleQuery.category = Sample Queries
SampleQuery.name = List Jobs Query
SampleQuery.help = This is a sample query, which lists all jobs with a given name
```

### Passing Arguments to a Query

A nice property of queries is that they can interact with each other. In other
words, parts of the result from one query (say one line in a histogram) can be
passed to a different query using the context menus.

To support this queries need to declare what kind of arguments they require and
delegate to the Memory Analyzer to collect this information. Memory Analyzer
will inject it into the queries before executing them. If needed, Memory
Analyzer does this by opening the Arguments Wizard.

To declare an input parameter a query has to define a public field and annotate
it with the `@Argument` annotation. To provide a help message specific on the
concrete argument, add also the `@Help` annotation to the public field.

The following types are currently supported as arguments:

| Argument type | Description |
|---------------|-------------|
| `ISnapshot` | the snapshot corresponding to the currently open editor |
| `IHeapObjectArgument` | good way of getting objects |
| `String`, `Pattern`, `int`, `boolean`, `float`, `double` | supplied directly via wizard or command line |
| `IContextObject` | row with one object |
| `IContextObjectSet` | row with multiple objects or OQL query to return those objects |
| `IQueryContext` | a more general way of extracting information about the snapshot which is not tied to the snapshot API |
| arrays or lists of the above | for multiple items |
| enums | can be used to provide a fixed choice list |
| `File` | for input or output files |

### Comparison Queries

Comparison queries are run from the Compare Basket but are invoked in a similar
way. Each row of the Compare Basket is a whole result of a previous query;
either a tree or table.

Queries with arguments suitable for a comparison operation are only offered in
the Compare Menu and not from the editor pane. Comparison arguments are as
follows, all arguments should be a `List` or `[]` array of:

| Compare Argument | Description |
|------------------|-------------|
| `IResultTable` | for comparison queries only operating on tables |
| `IResultTree` | for comparison queries only operating on trees |
| `IStructuredResult` | for comparison queries operating on tables and trees |
| `RefinedTable` | for comparison queries only operating on tables, uses the filtered and sorted version of the previous result with any derived columns like retained size |
| `RefinedTree` | for comparison queries only operating on tables, uses the filtered and sorted version of the previous result with any derived columns like retained size |
| `RefinedStructuredResult` | for comparison queries operating on tables and trees, uses the filtered and sorted version of the previous result with any derived columns like retained size |
| `ISnapshot` | the snapshots corresponding to the tables / trees, in the same order |

Consider using `RefinedStructuredResult` for your comparison queries as the query may then be more flexible for the end user.

Some standard arguments are also available to comparison queries:

| Argument type | Description |
|---------------|-------------|
| `String`, `Pattern`, `int`, `boolean`, `float`, `double` | supplied directly via wizard or command line |
| enums | can be used to provide a fixed choice list |
| `File` | for input or output files |

### Qualifications on Query Arguments

Parameters on the `@Argument` annotation can be used to specify some further
restrictions and hints to be followed by the wizard and during injection.

```java
@Argument
public ISnapshot snapshot;

@Argument(advice = Advice.HEAP_OBJECT, isMandatory = false, flag = Argument.UNFLAGGED)
public int[] objects;

@Argument(isMandatory = false, flag = "t")
public int thresholdPercent = 1;
```

| Annotation parameter | Description |
|----------------------|-------------|
| isMandatory | a boolean parameter to tell MAT if it can execute the query without the argument |
| flag | a String used instead of the field name to identify the argument in the command line and in the query browser |
| Advice | qualifies the way data is inserted into the field (see below) |

Advice arguments can be found in `org.eclipse.mat.query.annotations.Argument` as
the `Advice` enum.

| Advice argument | Description |
|-----------------|-------------|
| Advice.HEAP_OBJECT | the int or Integer is an object id, not a number |
| Advice.SECONDARY_SNAPSHOT | the snapshot is another snapshot, which should be prompted for, not the current one |
| Advice.CLASS_NAME_PATTERN | the pattern will be used to match class names |
| Advice.DIRECTORY | the file parameter is meant to be a directory |
| Advice.SAVE | the file parameter is meant to be used to save data |

#### Reading data from supplied arguments

See [Reading data from heap dumps](Reading_data_from_heap_dumps.md) for how to
extract data from supplied arguments, including use of `ISnapshot`, `IObject`
and the use of object IDs.

### Calling One Query from Another

Supplied queries are not a Memory Analyzer API, so user written queries should
not link to them directly. It is possible to call them by name, though the query
names and arguments can vary from release to release.

```java
String query = "SELECT s, toString(s) from java.lang.String s";
IResult ir = SnapshotQuery.lookup("oql", snapshot).setArgument("queryString", query).execute(listener);
```

An enum argument may need to be set using a parsed string, as calling via
`setArgument` often doesn't work as the enum type is inaccessible.

```java
SnapshotQuery query = SnapshotQuery.parse("dominator_tree -groupby BY_CLASSLOADER", snapshot);
IResultTree t = (IResultTree)query.execute(new VoidProgressListener());
```

### Query Results

- `TextResult` - A simple result that renders its input as text.
- `IStructuredResult` - A way of display data about lots of objects
    - `IResultTable` - A table of objects
        - `Histogram` - A table of objects where each row is all the objects of
        one class
        - `ListResult` - A way of displaying a Java List of things, where the
        fields from each thing are also given
        - `PropertyResult` - A way of displaying details about one object based
        on a list of attributes
    - `IResultTree` - A tree of objects
- `IResultPie` - A pie chart
- `QuerySpec` - A good way of displaying the results of executing another query

## Reports in Memory Analyzer

Several queries can be combined into a report which could then be run from the
Run Report... menu option or in batch mode.

### Report definition

The report definition is written in XML and can be validated using the schema
held in `org.eclipse.mat.report/schema/report.xsd`. The result of running a
report will be an HTML page or a CSV data file. Queries can be run using the
`query` element, using the command `element` to specify which command should be
run.

Other reports can be run using the `template` element. The `section` element can
be used to combine multiple `query`, `template` and `section` elements and is
displayed in a report as a collapsible part or a separate file.

Two examples show how the report definition is written. `overview.xml` has a
section and `suspects.xml` also has a `template` element referencing another
report to be run and included. They are available in `plugins/org.eclipse.mat.api/META-INF/reports/`.

The `param` element allows output to be controlled:

- The params can be used to control generation of tables - as HTML or CSV files,
to limit or increase the number of lines displayed, and to omit, sort or filter
columns.
- Some params just control the current section - some also control any inner
sections unless overridden.
- A param can also be used elsewhere in the report as `${param_name}` - in the
command name and other param values.
- Values are documented in `org.eclipse.mat.report.Params` interface as well as
in the `Params.Html` and `Params.Rendering` javadoc.
- Parameters can be passed to a report definition using `ParseHeapDump`
- A param value given on the command line will override a value given in a
report definition.

```bash
ParseHeapDump myheapdump.hprof -myparam=myparam_value myreport.xml
```

The report definition can be tested as Run Expert System Test > Run Report.

### Report definition extension point

If a report definition is incorporated into a plug-in then the report definition
extension point should be used. Then when the new plug-in is installed into MAT
the report will be available to run from the Memory Analyzer GUI.

Values in the report definition can then be externalized using a definition in
the `plugin.properties` file and referred to by `%myval Default value`.

### Executing a Report in Unattended Mode

Once you create a Report extension you can find it next to other reports like
the "Leak Suspects" in the Memory Analyzer GUI. Besides this, one can execute
reports in an unattended mode by running the "org.eclipse.mat.api.parse"
application.

Here is an example how to setup a Run Configuration to execute the report
"my_repory" located in the plugin "my_report_plugin":

- Run As -> Run Configurations
- Set "Run An Application" to "org.eclipse.mat.api.parse"
- Set the Program arguments to
```bash
${file_prompt} my_report_plugin:my_report
```

When you run this you will get a popup to select a heap dump file. It will be
then parsed and the report will be executed for it. The result however is not
open in the IDE, it is saved in the file system next to the dump.

#### Parameters

You can also define options on the command line. This can be as

```bash
${file_prompt} "-my_parm=my special value" my_report_plugin:my_report
```
and then access the variable inside the report XML as

```xml
<section name="My report" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns="http://www.eclipse.org/mat/report.xsd"
    xsi:schemaLocation="http://www.eclipse.org/mat/report.xsd platform:/resource/org.eclipse.mat.report/schema/report.xsd">
    <param key="my_parm" value="default value" />
    <query name="OQL Query command">
        <command>my_query -myopt "${my_parm}"</command>
    </query>
</section>
```

## Request Resolvers

A request resolver is a piece of coding which is capable of extracting details
about what a thread was doing, using the information from the thread object and
its java local objects. The information provided by a request resolver is
included in the Leak Suspects report.

When is this useful? There are often OutOfMemoryErrors which are not caused by a
memory leak, but rather by some "greedy" operation - an attempt to load a huge
file fully into memory, an attempt to build scan a whole DB table and keep the
results in memory, etc... In such cases the Leak Suspect report will often point
to the thread as the suspect object, because its local objects (objects on the
thread's stack) are eating too much memory. In such cases it is very helpful to
know what the thread was doing and to get some insights on this activity.

Examples:

- tell that a thread was processing an HTTP request and extract the concrete request and some parameters
- show an SQL statement that has been processed when an OOM error occured
- display the name of the Eclipse Job which has been processed
- etc...

### The `IRequestDetailsResolver` Interface

To implement a request resolver one needs to implement the
`IRequestDetailsResolver` interface. To specify for which type of local objects
this request resolver can provide information, use the the `@Subject` annotation
on the implementation class.

The interface defines just one method:

```java
void complement(ISnapshot snapshot, IThreadInfo thread, int[] javaLocals, int thisJavaLocal,
                IProgressListener listener) throws SnapshotException;
```

The complement method will be called by Memory Analyzer whenever it collects
information for a thread and the thread has a local object (somewhere on the
stack) which matches the type specified by `@Subject`. As parameters one will
receive all necessary context information to extract the needed information:

| parameters | description |
|------------|-------------|
| snapshot | the whole dump |
| thread | a IThreadInfo object representing the thread being analyzed |
| javaLocals | all the local variables, as object IDs |
| thisJavaLocal | the object ID of the local object matching the `@Subject` |

Within the `complement()` method one should extract helpful information about
the activity of the thread and add it to the `IThreadInfo` object using the
`addRequest()` method. The `addRequest()` method takes two parameters - a
String with short description appearing on the first page of the Leak Suspects
report, and an IResult with more details about the request (could be a table
with all properties, etc...).

Here is a code sample for a request resolver:

```java
/* Specify that I can extract information from ProgressManager$JobMonitor objects */
@Subject("org.eclipse.ui.internal.progress.ProgressManager$JobMonitor")
public class JobRequestResolver implements IRequestDetailsResolver {

    @Override
    public void complement(ISnapshot snapshot, IThreadInfo thread,
        int[] javaLocals, int thisJavaLocal, IProgressListener listener)
	    throws SnapshotException {
        IObject monitor = snapshot.getObject(thisJavaLocal); // get the IOjbect for the JobMonitor
        IObject job = (IObject) monitor.resolveValue("job"); // get the value of the job field
        String jobName = job.getClassSpecificName(); // get the symbolic represenation of the job

        String summary = "This thread executes the job [" + jobName + "]";
        IResult sampleDetails = new TextResult("Job object is = [" + job.getDisplayName() + "]");

        thread.addRequest(summary, sampleDetails); // add the request information
        thread.addKeyword(jobName); // add the job name to the keywords
    }
}
```

This sample request resolver will add to the leak suspect report a line like:

```
This thread executes the job [Sample greedy job]
```

if the thread was processing an Eclipse job. It will add as details the object
instance of the Job implementation.

## Adding a New Heap Dump Format

Memory Analyzer can also be extended to support more heap dump formats. A
detailed description how to do this can be found in the page
[Adding a new heapdump format](Adding_a_new_heapdump_format.md).

## Contributing back to the project

If your extension to Memory Analyzer would be useful to other people, please
consider contributing it back to the project.
