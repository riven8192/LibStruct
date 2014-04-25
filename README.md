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
// mapping structs to a native ByteBuffer, never goes out of scope
ByteBuffer bb = ByteBuffer.allocateDirect(12*100).order(ByteOrder.nativeOrder());
Vec3[] mapped = StructUtil.map(Vec3.class, bb);
```


## Handling stack allocated structs (responsibly)
```java
public class Vec3
{
   ...
   
   public void mul(float factor) {
      Vec3 tmp = new Vec3(factor, factor, factor);
      this.mul(tmp);
	  // when this method returns, the struct
	  // referenced by 'tmp' goes out of scope!
   }
   
   
   
   // normally we can't return a newly allocated struct,
   // because by the time the callsite uses the returned
   // struct, it will be out of scope. we can tell the
   // compiler that any returned struct should be copied
   // to the end of the stack of the callsite, like so:
   
   @CopyStruct // indicate providing a copy to the callsite
   public Vec3 normalize() {
      float len = (float)Math.sqrt(x*x + y*y + z*z);
      return new Vec3(x / len, y / len, z / len);
   }
   
   
   
   // let's say we want to return 'this', as to enable
   // chaining operations on a single struct. it would 
   // be undesired (and inefficient) to copy the struct
   // every time we return it, as we know the reference
   // to 'this' struct is still valid in the callsite:
   
   @TakeStruct // no copy is made!
   public Vec3 normalizeSelf() {
      float len = (float)Math.sqrt(x*x + y*y + z*z);
	  x /= len;
	  y /= len;
	  z /= len;
      return this;
   }
   
   @TakeStruct // no copy :o(
   public Vec3 plainWrong() {
      return new Vec3(); // goes out of scope!
   }
   
   
   
   // GOTCHA!
   public static final Vec3 ZERO = new Vec3(0,0,0);
   
   // this code is compiled (by javac) as:   
   public static final Vec3 ZERO;
   static // this is actually a normal method, which terminates
          //and reclaims/reuses its stack for new allocations
   {
      ZERO = new Vec3(0,0,0); // stack allocated struct immediately goes out of scope!
   }
}

public static void callsite() {
   Vec3 vec = new Vec3();
   
   // here we get a locally stack allocated struct, which
   // is a copy of the struct allocated in the method.
   Vec3 nor = vec.normalize();
   
   // here we get our reference to 'vec' back, and use it
   // to chain method calls on a single struct
   vec.normalizeSelf().add(1,2,3);
   
   // once a stack allocated struct that went out of scope
   // is accessed, it can contain *any* data, be in use by
   // another stack allocated struct, and accessing it will
   // lead to undefined results!
   Vec3 ohNo = vec.plainWrong();
   float undefined = ohNo.z;
   ohNo.x = 38.1f; // the horror!
}
```


## Performance
+ Allocating structs on the stack is about 3x faster than instantiating an equal instance.
+ Accessing fields of structs is just as fast as accessing fields of instances.
+ Calling methods on fields is just as fast as calling methods on instances.
+ Due to structs being sequentially allocated, they are guaranteed to be in a contiguous block of memory (regardless of whether you call new Vec3() in a loop, map(Vec3.class, n) or new Vec3[n]), leading to less cache misses and higher throughput.
+ Due to the lack of actual objects, the GC will never have to collect your structs, you can create and discard tens of millions per second, without GC hickups.

## How to enable structs in your application
Create a text file (e.g.: structdef.txt) and put it in your classpath.
```
test.net.indiespot.struct.Vec3
your.project.math.Vec2
your.project.math.Vec3
your.project.math.Vec4
```

Launch the JVM with the provided Java Agent attached, and pass it the name of the file listing your struct types:
```
-javaagent:struct-agent.jar=structdef.txt
```
The agent will scan your classpath for the bytecode of these classes - in this
case for "test/net/indiespot/struct/Vec3.class" (note that it doesn't actually
load the class). Once it found all structs, it will start your application and
will (lazily) rewrite all classes that any classloader attempts to load and resolve.


## Note about the immaturity of the library
It's currently a hobby project where the reliability is measured by the ability to execute the limited amount of
unit tests in the class test.net.indiespot.struct.StructTest. Please use this library for hobby projects only, until
we can make more guarantees about it functioning correctly. The main culprit of issues is the (currently) simplistic
control-flow analysis, as we need to know the types on the stack and localvars at any instruction in any method,
regardless of how we ended up at that instruction.

If you happen to find a bug that either causes a failure to rewrite a class, or a failure to produce valid bytecode,
or a failure to produce correct bytecode, please set StructEnv.PRINT_LOG to true and rerun the application, and post
the output in your bug report.