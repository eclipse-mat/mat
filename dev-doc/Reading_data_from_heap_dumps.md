# Reading data from heap dumps (programmatically)

The Memory Analyzer offers an API which one can use to open a heap dump and
inspect its contents programmatically. This API is used by the MAT tool itself
to offer the different end-user features available in the tool. An overview of
this API is available on this page.

## The ISnapshot interface

The most important interface one can use to extract data from a heap dump is
`ISnapshot`. `ISnapshot` represents a heap dump and offers various methods for
reading object and classes from it, getting the size of objects, etc...

To obtain an instance of `ISnapshot` one can use static methods on the
`SnapshotFactory` class. However, this is only needed if the API is used to
implement a tool independent of Memory Analyzer.

If you are writing extensions to MAT, you typically will receive a reference to
an already opened heap dump either by injection or as a method parameter. See
[Extending Memory Analyzer](Extending_Memory_Analyzer.md).

### Opening a snapshot using `SnapshotFactory`

To open an existing heap dump in one of the supported formats call the
`SnapshotFactory.openSnapshot()` method.

```java
public static ISnapshot openSnapshot(File file, IProgressListener listener) throws SnapshotException
```

As parameters pass the heap dump file and a valid progress listener (see below).

When you are finished with using the `ISnapshot` instance call the
`SnapshotFactory.dispose(ISnapshot)` method to free the resources and unlock any
used files.

### The `IProgressListener` interface

The `IProgressListener` listener interface offers (as the name suggests)
functionality to report the progress of different computations.

Usually if you are extending the tool then MAT will pass an instance of an
object implementing the interface to you.

In case you are opening the heap dump on your own, you may need to create the
listener on your own. The tool provides some helper classes:

| Listener class | Description |
|----------------|-------------|
| `ConsoleProgressListener` | Logs progress to Java stdout/console. |
| `VoidProgressListener` | Ignores progress. |
| `ProgressMonitorWrapper` | Can wrap the `org.eclipse.core.runtime.IProgressMonitor`. |
| `SimpleMonitor` | Can be used to generate several `IProgressListener` objects each handling a proportion of work from a supplied `IProgressListener`. |

## The object model

The following hierarchy of interfaces represents the object model that MAT
builds for objects in the heap.

They can all be found in `org.eclipse.mat.snapshot.model.*` package.

- `IObject` representing any object on the heap.
  - `IClass` represents a `java.lang.Class`.
  - `IInstance` represents an ordinary Java object instance.
    - `IClassLoader` represents a classloader.
  - `IArray` represents an array.
    - `IObjectArray` represents an object array.
    - `IPrimitiveArray` represents a primitive array.

This model is pretty straightforward and easy to understand. However, there is
one major challenge – the memory needed to maintain such a model. As often there
are millions of objects in a heap dump, MAT is not keeping such a model through
the lifetime of an ISnapshot. Instead it gives every object an id (starting from
0 and growing by one) and uses these ids to obtain information about objects
(like its class, size, referenced objects, etc) from the `ISnapshot` instance.

Also most of the heavy computations traversing potentially millions of objects
(e.g. calculating a retained size, computing paths, etc) are done without using
the object model described above. The use of the classes described here is
needed (and recommended) only when the full information about an object is
needed – including its field names and their (possibly primitive) values.

## Single objects, objects id and address

To get an object by its id use the method `getObject(int id)` of `ISnapshot`.

Objects have also addresses (usually visualized as hexadecimal number next to
the object). One can map between object ids and addresses using the following
two methods of `ISnapshot`:

```java
   public long mapIdToAddress(int objectId) throws SnapshotException;
   public int mapAddressToId(long objectAddress) throws SnapshotException;
```

If you already have an instance of `IObject` you can call `getObjectId()` and
`getObjectAddress()` directly.

## Getting classes

The `ISnapshot` interface offers the possibility to get a class by its fully
qualified name or get a collection of classes using a regex pattern.

```java
   public Collection<IClass> getClassesByName(String name, boolean includeSubClasses) throws SnapshotException;
   public Collection<IClass> getClassesByName(Pattern namePattern, boolean includeSubClasses) throws SnapshotException;
```

Both methods return a collection of classes, as classes with the same name but
loaded with different class loaders are treated as separate classes. To get a
collection of all classes available in the heap dump, just call the
`getClasses()` method without any parameters

## Get all instances of a class

To get all instances of a certain class first obtain the class (or collection
of classes) and then call the `getObjectIds` method on the `IClass` instance:

```java
   public int[] getObjectIds() throws SnapshotException;
```

The returned `int[]` contains the ids of all objects of the class.

## Inspecting referenced objects

There are various possibilities to explore the outgoing references of an object.
The most performant way is to use the `getOutboundReferentIds(int)` methods of
`ISnapshot`.

```java
   public int[] getOutboundReferentIds(int objectId) throws SnapshotException;
```

This method takes an object id and returns an array containing all the ids of
all referenced objects. The reference objects include also object referenced by
artificially modeled references. This method gives a fast way to traverse the
object graph.

The `IObject` interface also provides several ways to explore its references:

```java
   public List<NamedReference> getOutboundReferences();
```

A `NamedReference` allows you to look at the name of the reference, get the id
and the address of the referenced object, and also get the referenced object as
`IObject`.

If you are looking for the value of a specific field or reference, then the most
convenient way to achieve it is to use the `resolveValue` method.

```java
   public Object resolveValue(String field) throws SnapshotException;
```

It takes as an argument a dot-separated path to the field of interest. This
means that one can access not only the fields of the object itself, but to
provide a path through some of the references. Here is an example:

```java
IObject myObject = snapshot.getObject(objectId);
IObject fName = myObject.resolveValue(“department.customer.firstName”);
```

This code will go through the fields of myObject and will search for a field
named "department". If department itself is not a primitive MAT will find its
field "customer", and then find the field "firstName" in the object referenced
through "customer".

If the field is of primitive type, then `resolveValue()` will return the
corresponding boxed class, allowing you to read these directly:

```java
IObject hashMap = … ; // some IObject representing a HashMap
int size = hashMap.resolveValue(“size”);
```

## Printing objects

The IObject interface defines several methods for getting a String
representation of the object.

| Method | Description |
|--------|-------------|
| `getTechnicalName()` | returns a string in the format `<class name> @ <address>`. |
| `getClassSpecificName()` | returns a string in the format as described by a resolver (see below). |
| `getDisplayName()` | convenience method returning a combination of the technical name appended by the class specific name. |

### Class Specific Name Resolvers

Calling `getClassSpecificName()` on an object with a defined name resolver
allows for specific code to interpret the object (including referred) and
return a more readable string description.

For example if you call it on an `IObject` representing a `java.lang.String`,
then it will return the value of the string.

If you call it on an `IObject` representing a `java.lang.Thread`, it will return
the name of the Thread. This method however is not the toString() method of the
real objects that were put in the heap dump. The heap dump only contains the
objects and their values, but it is not possible to call methods of the
corresponding classes. The Memory Analyzer extracts information from the fields
of the objects and models the toString() behavior.

It is possible to easily extend MAT by adding new ClassSpecificNameResolvers
using a defined extension point. The existing resolvers are available in
`org.eclipse.mat.inspections.*` package. You can register or contribute
additional resolvers to support a more pleasant experience.

See the relevant section in [Extending Memory Analyzer](Extending_Memory_Analyzer.md)

## Object sizes

### Shallow size

To get the shallow size of a single object, use the `getHeapSize` method of
`ISnapshot`:

```java
   public long getHeapSize(int objectId) throws SnapshotException;
```

If you have to compute the shallow size of a set of objects (e.g. the sum of the
shallow sizes of each instance of a certain class), then we recommend to use the
`getHeapSize(int[] objectIds)` method of ISnapshot and pass the ids of all
objects of interest as an array. This method uses some internal structures and
is executing the task in several threads (if more than one CPU is available),
therefore it will have better performance than looping over the objects and
calling `getHeapSize()` for each single object.

### Retained Size

To get the retained size of a single object, use the `getRetainedHeapSize()`
method of ISnapshot.

```java
   public long getRetainedHeapSize(int objectId) throws SnapshotException;
```

To get the retained size of a set of objects, first compute the retained set
using `int[] getRetainedSet(int[], IProgressListener)` and then call the
`getHeapSize(int[])` on the returned array with ids.

The `getRetainedSet` method has two other “advanced” variants. Consult the API
reference inside the tool for more details.
