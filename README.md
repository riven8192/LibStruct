LibStruct
=========

# Stack allocated and mapped structs for Java


## Defining a struct (Vec3)
```java

@StructType(sizeof=12)
public class Vec3
{
   @StructField(offset=0) public float x;
   @StructField(offset=4) public float y;
   @StructField(offset=8) public float z;
   
   public Vec3(float x, float y, float z) {
      this.x = x;
      this.y = y;
      this.z = z;
   }
   
   public void add(Vec3 that) {
      this.x += that.x;
	  this.y += that.y;
	  this.z += that.z;
   }
}
```

## Stack allocating a struct
```java
// surprise! no object creation
Vec3 struct = new Vec3(x, y, z);
struct.x += 33.77;

// prints: "<struct@2380744>", showing the address of the struct
System.out.println(struct);

Vec3 vector = new Vec3(2.3f, 3.4f, 4.5f);
struct.add(vector);
```



## Stack allocating an array of structs
```java
// There are 'len' (any variable) structs allocated on the stack
Vec3[] array = new Vec3[len];
for(Vec3 struct: array) {
   System.out.println(struct);
}
// prints: "<struct@2380744>"
// prints: "<struct@2380756>"
// prints: "<struct@2380768>", etc


// freedom to use array elements as you wish
Vec3 tmp = array[3];
array[3] = array[9];
array[0].x *= array[7].y;
array[1] = new Vec3();
array[9] = tmp;
array[5] = null;
array[5].x += 9.9f; // segfault much?
```

## Mapping an array of structs to a ByteBuffer
```java
// mapping structs to a native ByteBuffer
ByteBuffer bb = ByteBuffer.allocateDirect(12*100).order(ByteOrder.nativeOrder());
Vec3[] mapped = StructUtil.map(Vec3.class, bb);
```



## How to enable structs in your application
Create a text file (e.g.: structdef.txt) and put it in your classpath.
```
test/net/indiespot/struct/Vec3
your/project/YourStruct
etc, etc
```

Launch the JVM with the provided Java Agent attached, and pass it the name of the file listing your struct types:
```
-javaagent:struct-agent.jar=structdef.txt
```
The agent will scan your classpath for the bytecode of these classes - in this
case for "test/net/indiespot/struct/Vec3.class" (note that it doesn't actually
load the class). Once it found all structs, it will start (lazily) rewriting
all classes that any classloader attempts to load and resolve.