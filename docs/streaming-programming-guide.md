---
layout: global
title: Spark Streaming Programming Guide
---

* This will become a table of contents (this text will be scraped).
{:toc}

# Overview
A Spark Streaming application is very similar to a Spark application; it consists of a *driver program* that runs the user's `main` function and continuous executes various *parallel operations* on input streams of data. The main abstraction Spark Streaming provides is a *discretized stream* (DStream), which is a continuous sequence of RDDs (distributed collection of elements) representing a continuous stream of data. DStreams can created from live incoming data (such as data from a socket, Kafka, etc.) or it can be generated by transformation of existing DStreams using parallel operators like map, reduce, and window. The basic processing model is as follows: 
(i) While a Spark Streaming driver program is running, the system receives data from various sources and and divides the data into batches. Each batch of data is treated as a RDD, that is a immutable and parallel collection of data. These input data RDDs are automatically persisted in memory (serialized by default) and replicated to two nodes for fault-tolerance. This sequence of RDDs is collectively referred to as an InputDStream.
(ii) Data received by InputDStreams are processed processed using DStream operations. Since all data is represented as RDDs and all DStream operations as RDD operations, data is automatically recovered in the event of node failures.  

This guide shows some how to start programming with DStreams. 

# Initializing Spark Streaming
The first thing a Spark Streaming program must do is create a `StreamingContext` object, which tells Spark how to access a cluster. A `StreamingContext` can be created by using

{% highlight scala %}
new StreamingContext(master, jobName, batchDuration)
{% endhighlight %}

The `master` parameter is either the [Mesos master URL](running-on-mesos.html) (for running on a cluster)or the special "local" string (for local mode) that is used to create a Spark Context. For more information about this please refer to the [Spark programming guide](scala-programming-guide.html). The `jobName` is the name of the streaming job, which is the same as the jobName used in SparkContext. It is used to identify this job in the Mesos web UI. The `batchDuration` is the size of the batches (as explained earlier). This must be set carefully such the cluster can keep up with the processing of the data streams. Starting with something conservative like 5 seconds maybe a good start. See [Performance Tuning](#setting-the-right-batch-size) section for a detailed discussion.

This constructor creates a SparkContext object using the given `master` and `jobName` parameters. However, if you already have a SparkContext or you need to create a custom SparkContext by specifying list of JARs, then a StreamingContext can be created from the existing SparkContext, by using
{% highlight scala %}
new StreamingContext(sparkContext, batchDuration)
{% endhighlight %}



# Attaching Input Sources - InputDStreams
The StreamingContext is used to creating InputDStreams from input sources:

{% highlight scala %}
// Assuming ssc is the StreamingContext
ssc.textFileStream(directory)      // Creates a stream by monitoring and processing new files in a HDFS directory
ssc.socketStream(hostname, port)   // Creates a stream that uses a TCP socket to read data from hostname:port
{% endhighlight %}

We also provide a input streams for Kafka, Flume, Akka actor, etc. For a complete list of input streams, take a look at the [StreamingContext API documentation](api/streaming/index.html#spark.streaming.StreamingContext).



# DStream Operations
Data received from the input streams can be processed using _DStream operations_. There are two kinds of operations - _transformations_ and _output operations_. Similar to RDD transformations, DStream transformations operate on one or more DStreams to create new DStreams with transformed data. After applying a sequence of transformations to the input streams, you'll need to call the output operations, which writies data out to an external source. 

## Transformations

DStreams support many of the transformations available on normal Spark RDD's:

<table class="table">
<tr><th style="width:30%">Transformation</th><th>Meaning</th></tr>
<tr>
  <td> <b>map</b>(<i>func</i>) </td>
  <td> Returns a new DStream formed by passing each element of the source DStream through a function <i>func</i>. </td>
</tr>
<tr>
  <td> <b>filter</b>(<i>func</i>) </td>
  <td> Returns a new DStream formed by selecting those elements of the source DStream on which <i>func</i> returns true. </td>
</tr>
<tr>
  <td> <b>flatMap</b>(<i>func</i>) </td>
  <td> Similar to map, but each input item can be mapped to 0 or more output items (so <i>func</i> should return a <code>Seq</code> rather than a single item). </td>
</tr>
<tr>
  <td> <b>mapPartitions</b>(<i>func</i>) </td>
  <td> Similar to map, but runs separately on each partition (block) of the DStream, so <i>func</i> must be of type
    Iterator[T] => Iterator[U] when running on an DStream of type T. </td>
</tr>
<tr>
  <td> <b>union</b>(<i>otherStream</i>) </td>
  <td> Return a new DStream that contains the union of the elements in the source DStream and the argument DStream. </td>
</tr>
<tr>
  <td> <b>count</b>() </td>
  <td> Returns a new DStream of single-element RDDs by counting the number of elements in each RDD of the source DStream.  </td>
</tr>
<tr>
  <td> <b>reduce</b>(<i>func</i>) </td>
  <td> Returns a new DStream of single-element RDDs by aggregating the elements in each RDD of the source DStream using a function <i>func</i> (which takes two arguments and returns one). The function should be associative so that it can be computed in parallel. </td>
</tr>
<tr>
  <td> <b>countByValue</b>() </td>
  <td> When called on a DStream of elements of type K, returns a new DStream of (K, Long) pairs where the value of each key is its frequency in each RDD of the source DStream.  </td>
</tr>
<tr>
  <td> <b>groupByKey</b>([<i>numTasks</i>]) </td>
  <td> When called on a DStream of (K, V) pairs, returns a new DStream of (K, Seq[V]) pairs by grouping together all the values of each key in the RDDs of the source DStream. <br />
  <b>Note:</b> By default, this uses Spark's default number of parallel tasks (2 for local machine, 8 for a cluser) to do the grouping. You can pass an optional <code>numTasks</code> argument to set a different number of tasks.
</td>
</tr>
<tr>
  <td> <b>reduceByKey</b>(<i>func</i>, [<i>numTasks</i>]) </td>
  <td> When called on a DStream of (K, V) pairs, returns a new DStream of (K, V) pairs where the values for each key are aggregated using the given reduce function. Like in <code>groupByKey</code>, the number of reduce tasks is configurable through an optional second argument. </td>
</tr>
<tr>
  <td> <b>join</b>(<i>otherStream</i>, [<i>numTasks</i>]) </td>
  <td> When called on two DStreams of (K, V) and (K, W) pairs, returns a new DStream of (K, (V, W)) pairs with all pairs of elements for each key. </td>
</tr>
<tr>
  <td> <b>cogroup</b>(<i>otherStream</i>, [<i>numTasks</i>]) </td>
  <td> When called on DStream of (K, V) and (K, W) pairs, returns a new DStream of (K, Seq[V], Seq[W]) tuples.</td>
</tr>
<tr>
  <td> <b>transform</b>(<i>func</i>) </td>
  <td> Returns a new DStream by applying func (a RDD-to-RDD function) to every RDD of the stream. This can be used to do arbitrary RDD operations on the DStream. </td>
</tr>
<tr>
  <td> <b>updateStateByKey</b>(<i>func</i>) </td>
  <td> Return a new "state" DStream where the state for each key is updated by applying the given function on the previous state of the key and the new values of each key. This can be used to track session state by using the session-id as the key and updating the session state as new data is received.</td>
</tr>

</table>

Spark Streaming features windowed computations, which allow you to apply transformations over a sliding window of data. All window functions take a <i>windowDuration</i>, which represents the width of the window and a <i>slideTime</i>, which represents the frequency during which the window is calculated.

<table class="table">
<tr><th style="width:30%">Transformation</th><th>Meaning</th></tr>
<tr>
  <td> <b>window</b>(<i>windowDuration</i>, </i>slideDuration</i>) </td>
  <td> Return a new DStream which is computed based on windowed batches of the source DStream. <i>windowDuration</i> is the width of the window and <i>slideTime</i> is the frequency during which the window is calculated. Both times must be multiples of the batch interval.
  </td>
</tr>
<tr>
  <td> <b>countByWindow</b>(<i>windowDuration</i>, </i>slideDuration</i>) </td>
  <td> Return a sliding count of elements in the stream. <i>windowDuration</i> and <i>slideDuration</i> are exactly as defined in <code>window()</code>.
  </td>
</tr>
<tr>
  <td> <b>reduceByWindow</b>(<i>func</i>, <i>windowDuration</i>, <i>slideDuration</i>) </td>
  <td> Return a new single-element stream, created by aggregating elements in the stream over a sliding interval using <i>func</i>. The function should be associative so that it can be computed correctly in parallel. <i>windowDuration</i> and <i>slideDuration</i> are exactly as defined in <code>window()</code>.
  </td>
</tr>
<tr>
  <td> <b>groupByKeyAndWindow</b>(<i>windowDuration</i>, <i>slideDuration</i>, [<i>numTasks</i>])
  </td>
  <td> When called on a DStream of (K, V) pairs, returns a new DStream of (K, Seq[V]) pairs by grouping together values of each key over batches in a sliding window. <br />
<b>Note:</b> By default, this uses Spark's default number of parallel tasks (2 for local machine, 8 for a cluser) to do the grouping. You can pass an optional <code>numTasks</code> argument to set a different number of tasks.</td>
</tr>
<tr>
  <td> <b>reduceByKeyAndWindow</b>(<i>func</i>, <i>windowDuration</i>, <i>slideDuration</i>, [<i>numTasks</i>]) </td>
  <td> When called on a DStream of (K, V) pairs, returns a new DStream of (K, V) pairs where the values for each key are aggregated using the given reduce function <i>func</i> over batches in a sliding window. Like in <code>groupByKeyAndWindow</code>, the number of reduce tasks is configurable through an optional second argument.
 <i>windowDuration</i> and <i>slideDuration</i> are exactly as defined in <code>window()</code>.
</td> 
</tr>
<tr>
  <td> <b>reduceByKeyAndWindow</b>(<i>func</i>, <i>invFunc</i>, <i>windowDuration</i>, <i>slideDuration</i>, [<i>numTasks</i>]) </td>
  <td> A more efficient version of the above <code>reduceByKeyAndWindow()</code> where the reduce value of each window is calculated
  incrementally using the reduce values of the previous window. This is done by reducing the new data that enter the sliding window, and "inverse reducing" the old data that leave the window. An example would be that of "adding" and "subtracting" counts of keys as the window slides. However, it is applicable to only "invertible reduce functions", that is, those reduce functions which have a corresponding "inverse reduce" function (taken as parameter <i>invFunc</i>. Like in <code>groupByKeyAndWindow</code>, the number of reduce tasks is configurable through an optional second argument.
 <i>windowDuration</i> and <i>slideDuration</i> are exactly as defined in <code>window()</code>.
</td>
</tr>
<tr>
  <td> <b>countByValueAndWindow</b>(<i>windowDuration</i>, <i>slideDuration</i>, [<i>numTasks</i>]) </td>
  <td> When called on a DStream of (K, V) pairs, returns a new DStream of (K, Long) pairs where the value of each key is its frequency within a sliding window. Like in <code>groupByKeyAndWindow</code>, the number of reduce tasks is configurable through an optional second argument.
 <i>windowDuration</i> and <i>slideDuration</i> are exactly as defined in <code>window()</code>.
</td>
</tr>

</table>

A complete list of DStream operations is available in the API documentation of [DStream](api/streaming/index.html#spark.streaming.DStream) and [PairDStreamFunctions](api/streaming/index.html#spark.streaming.PairDStreamFunctions).

## Output Operations
When an output operator is called, it triggers the computation of a stream. Currently the following output operators are defined:

<table class="table">
<tr><th style="width:30%">Operator</th><th>Meaning</th></tr>
<tr>
  <td> <b>foreach</b>(<i>func</i>) </td>
  <td> The fundamental output operator. Applies a function, <i>func</i>, to each RDD generated from the stream. This function should have side effects, such as printing output, saving the RDD to external files, or writing it over the network to an external system. </td>
</tr>

<tr>
  <td> <b>print</b>() </td>
  <td> Prints first ten elements of every batch of data in a DStream on the driver. </td>
</tr>

<tr>
  <td> <b>saveAsObjectFiles</b>(<i>prefix</i>, [<i>suffix</i>]) </td>
  <td> Save this DStream's contents as a <code>SequenceFile</code> of serialized objects. The file name at each batch interval is generated based on <i>prefix</i> and <i>suffix</i>: <i>"prefix-TIME_IN_MS[.suffix]"</i>.
  </td>
</tr>

<tr>
  <td> <b>saveAsTextFiles</b>(<i>prefix</i>, [<i>suffix</i>]) </td>
  <td> Save this DStream's contents as a text files. The file name at each batch interval is generated based on <i>prefix</i> and <i>suffix</i>: <i>"prefix-TIME_IN_MS[.suffix]"</i>. </td>
</tr>

<tr>
  <td> <b>saveAsHadoopFiles</b>(<i>prefix</i>, [<i>suffix</i>]) </td>
  <td> Save this DStream's contents as a Hadoop file. The file name at each batch interval is generated based on <i>prefix</i> and <i>suffix</i>: <i>"prefix-TIME_IN_MS[.suffix]"</i>. </td>
</tr>

</table>

# Starting the Streaming computation
All the above DStream operations are completely lazy, that is, the operations will start executing only after the context is started by using
{% highlight scala %}
ssc.start()
{% endhighlight %}

Conversely, the computation can be stopped by using
{% highlight scala %}
ssc.stop()
{% endhighlight %}

# Example
A simple example to start off is the [NetworkWordCount](https://github.com/mesos/spark/tree/master/examples/src/main/scala/spark/streaming/examples/NetworkWordCount.scala). This example counts the words received from a network server every second. Given below is the relevant sections of the source code. You can find the full source code in `<Spark repo>/streaming/src/main/scala/spark/streaming/examples/NetworkWordCount.scala` .

{% highlight scala %}
import spark.streaming.{Seconds, StreamingContext}
import spark.streaming.StreamingContext._
...

// Create the context and set up a network input stream to receive from a host:port
val ssc = new StreamingContext(args(0), "NetworkWordCount", Seconds(1))
val lines = ssc.socketTextStream(args(1), args(2).toInt)

// Split the lines into words, count them, and print some of the counts on the master
val words = lines.flatMap(_.split(" "))
val wordCounts = words.map(x => (x, 1)).reduceByKey(_ + _)
wordCounts.print()

// Start the computation
ssc.start()
{% endhighlight %}

The `socketTextStream` returns a DStream of lines received from a TCP socket-based source. The `lines` DStream is _transformed_ into a DStream using the `flatMap` operation, where each line is split into words. This `words` DStream is then mapped to a DStream of `(word, 1)` pairs, which is finally reduced to get the word counts. `wordCounts.print()` will print 10 of the counts generated every second.

To run this example on your local machine, you need to first run a Netcat server by using

{% highlight bash %}
$ nc -lk 9999
{% endhighlight %}

Then, in a different terminal, you can start NetworkWordCount by using

{% highlight bash %}
$ ./run spark.streaming.examples.NetworkWordCount local[2] localhost 9999
{% endhighlight %}

This will make NetworkWordCount connect to the netcat server. Any lines typed in the terminal running the netcat server will be counted and printed on screen.

<table>
<td>
{% highlight bash %}
# TERMINAL 1
# RUNNING NETCAT

$ nc -lk 9999
hello world





...
{% endhighlight %}
</td>
<td>
{% highlight bash %}
# TERMINAL 2: RUNNING NetworkWordCount
...
2012-12-31 18:47:10,446 INFO SparkContext: Job finished: run at ThreadPoolExecutor.java:886, took 0.038817 s
-------------------------------------------
Time: 1357008430000 ms
-------------------------------------------
(hello,1)
(world,1)

2012-12-31 18:47:10,447 INFO JobManager: Total delay: 0.44700 s for job 8 (execution: 0.44000 s)
...
{% endhighlight %}
</td>
</table>

You can find more examples in `<Spark repo>/streaming/src/main/scala/spark/streaming/examples/`. They can be run in the similar manner using `./run spark.streaming.examples....` . Executing without any parameter would give the required parameter list. Further explanation to run them can be found in comments in the files.

# DStream Persistence
Similar to RDDs, DStreams also allow developers to persist the stream's data in memory. That is, using `persist()` method on a DStream would automatically persist every RDD of that DStream in memory. This is useful if the data in the DStream will be computed multiple times (e.g., multiple operations on the same data). For window-based operations like `reduceByWindow` and `reduceByKeyAndWindow` and state-based operations like `updateStateByKey`, this is implicitly true. Hence, DStreams generated by window-based operations are automatically persisted in memory, without the developer calling `persist()`.

For input streams that receive data from the network (that is, subclasses of NetworkInputDStream like FlumeInputDStream and KafkaInputDStream), the default persistence level is set to replicate the data to two nodes for fault-tolerance.

Note that, unlike RDDs, the default persistence level of DStreams keeps the data serialized in memory. This is further discussed in the [Performance Tuning](#memory-tuning) section. More information on different persistence levels can be found in [Spark Programming Guide](scala-programming-guide.html#rdd-persistence).

# RDD Checkpointing within DStreams
DStreams created by stateful operations like `updateStateByKey` require the RDDs in the DStream to be periodically saved to HDFS files for checkpointing. This is because, unless checkpointed, the lineage of operations of the state RDDs can increase indefinitely (since each RDD in the DStream depends on the previous RDD). This leads to two problems - (i) the size of Spark tasks increase proportionally with the RDD lineage leading higher task launch times, (ii) no limit on the amount of recomputation required on failure. Checkpointing RDDs at some interval by writing them to HDFS allows the lineage to be truncated. Note that checkpointing also incurs the cost of saving to HDFS which may cause the corresponding batch to take longer to process. Hence, the interval of checkpointing needs to be set carefully. At small batch sizes (say 1 second), checkpointing every batch may significantly reduce operation throughput. Conversely, checkpointing too slowly causes the lineage and task sizes to grow which may have detrimental effects. Typically, a checkpoint interval of 5 - 10 times of sliding interval of a DStream is good setting to try.

To enable checkpointing, the developer has to provide the HDFS path to which RDD will be saved. This is done by using

{% highlight scala %}
ssc.checkpoint(hdfsPath) // assuming ssc is the StreamingContext
{% endhighlight %}

The interval of checkpointing of a DStream can be set by using

{% highlight scala %}
dstream.checkpoint(checkpointInterval) // checkpointInterval must be a multiple of slide duration of dstream
{% endhighlight %}

For DStreams that must be checkpointed (that is, DStreams created by `updateStateByKey` and `reduceByKeyAndWindow` with inverse function), the checkpoint interval of the DStream is by default set to a multiple of the DStream's sliding interval such that its at least 10 seconds.


# Performance Tuning
Getting the best performance of a Spark Streaming application on a cluster requires a bit of tuning. This section explains a number of the parameters and configurations that can tuned to improve the performance of you application. At a high level, you need to consider two things:
<ol>
<li>Reducing the processing time of each batch of data by efficiently using cluster resources.</li>
<li>Setting the right batch size such that the data processing can keep up with the data ingestion.</li>
</ol>

## Reducing the Processing Time of each Batch
There are a number of optimizations that can be done in Spark to minimize the processing time of each batch. These have been discussed in detail in [Tuning Guide](tuning.html). This section highlights some of the most important ones.

### Level of Parallelism
Cluster resources maybe under-utilized if the number of parallel tasks used in any stage of the computation is not high enough. For example, for distributed reduce operations like `reduceByKey` and `reduceByKeyAndWindow`, the default number of parallel tasks is 8. You can pass the level of parallelism as an argument (see the [`spark.PairDStreamFunctions`](api/streaming/index.html#spark.PairDStreamFunctions) documentation), or set the system property `spark.default.parallelism` to change the default.

### Data Serialization
The overhead of data serialization can be significant, especially when sub-second batch sizes are to be achieved. There are two aspects to it.

* **Serialization of RDD data in Spark**: Please refer to the detailed discussion on data serialization in the [Tuning Guide](tuning.html). However, note that unlike Spark, by default RDDs are persisted as serialized byte arrays to minimize pauses related to GC.

* **Serialization of input data**: To ingest external data into Spark, data received as bytes (say, from the network) needs to deserialized from bytes and re-serialized into Spark's serialization format. Hence, the deserialization overhead of input data may be a bottleneck.

### Task Launching Overheads
If the number of tasks launched per second is high (say, 50 or more per second), then the overhead of sending out tasks to the slaves maybe significant and will make it hard to achieve sub-second latencies. The overhead can be reduced by the following changes:

* **Task Serialization**: Using Kryo serialization for serializing tasks can reduced the task sizes, and therefore reduce the time taken to send them to the slaves.

* **Execution mode**: Running Spark in Standalone mode or coarse-grained Mesos mode leads to better task launch times than the fine-grained Mesos mode. Please refer to the [Running on Mesos guide](running-on-mesos.html) for more details.
These changes may reduce batch processing time by 100s of milliseconds, thus allowing sub-second batch size to be viable.

## Setting the Right Batch Size
For a Spark Streaming application running on a cluster to be stable, the processing of the data streams must keep up with the rate of ingestion of the data streams. Depending on the type of computation, the batch size used may have significant impact on the rate of ingestion that can be sustained by the Spark Streaming application on a fixed cluster resources. For example, let us consider the earlier WordCountNetwork example. For a particular data rate, the system may be able to keep up with reporting word counts every 2 seconds (i.e., batch size of 2 seconds), but not every 500 milliseconds.

A good approach to figure out the right batch size for your application is to test it with a conservative batch size (say, 5-10 seconds) and a low data rate. To verify whether the system is able to keep up with data rate, you can check the value of the end-to-end delay experienced by each processed batch (in the Spark master logs, find the line having the phrase "Total delay"). If the delay is maintained to be less than the batch size, then system is stable. Otherwise, if the delay is continuously increasing, it means that the system is unable to keep up and it therefore unstable. Once you have an idea of a stable configuration, you can try increasing the data rate and/or reducing the batch size. Note that momentary increase in the delay due to temporary data rate increases maybe fine as long as the delay reduces back to a low value (i.e., less than batch size).

## 24/7 Operation
By default, Spark does not forget any of the metadata (RDDs generated, stages processed, etc.). But for a Spark Streaming application to operate 24/7, it is necessary for Spark to do periodic cleanup of it metadata. This can be enabled by setting the Java system property `spark.cleaner.ttl` to the number of seconds you want any metadata to persist. For example, setting `spark.cleaner.ttl` to 600 would cause Spark periodically cleanup all metadata and persisted RDDs that are older than 10 minutes. Note, that this property needs to be set before the SparkContext is created.

This value is closely tied with any window operation that is being used. Any window operation would require the input data to be persisted in memory for at least the duration of the window. Hence it is necessary to set the delay to at least the value of the largest window operation used in the Spark Streaming application. If this delay is set too low, the application will throw an exception saying so.

## Memory Tuning
Tuning the memory usage and GC behavior of Spark applications have been discussed in great detail in the [Tuning Guide](tuning.html). It is recommended that you read that. In this section, we highlight a few customizations that are strongly recommended to minimize GC related pauses in Spark Streaming applications and achieving more consistent batch processing times.

* **Default persistence level of DStreams**: Unlike RDDs, the default persistence level of DStreams serializes the data in memory (that is, [StorageLevel.MEMORY_ONLY_SER](api/core/index.html#spark.storage.StorageLevel$) for DStream compared to [StorageLevel.MEMORY_ONLY](api/core/index.html#spark.storage.StorageLevel$) for RDDs). Even though keeping the data serialized incurs a higher serialization overheads, it significantly reduces GC pauses.

* **Concurrent garbage collector**: Using the concurrent mark-and-sweep GC further minimizes the variability of GC pauses. Even though concurrent GC is known to reduce the overall processing throughput of the system, its use is still recommended to achieve more consistent batch processing times.

# Fault-tolerance Properties
In this section, we are going to discuss the behavior of Spark Streaming application in the event of a node failure. To understand this, let us remember the basic fault-tolerance properties of Spark's RDDs.

 1. An RDD is an immutable, and deterministically re-computable, distributed dataset. Each RDD remembers the lineage of deterministic operations that were used on a fault-tolerant input dataset to create it.
 1. If any partition of an RDD is lost due to a worker node failure, then that partition can be re-computed from the original fault-tolerant dataset using the lineage of operations.

Since all data transformations in Spark Streaming are based on RDD operations, as long as the input dataset is present, all intermediate data can recomputed. Keeping these properties in mind, we are going to discuss the failure semantics in more detail.

## Failure of a Worker Node

There are two failure behaviors based on which input sources are used.

1. _Using HDFS files as input source_ - Since the data is reliably stored on HDFS, all data can re-computed and therefore no data will be lost due to any failure.
1. _Using any input source that receives data through a network_ - For network-based data sources like Kafka and Flume, the received input data is replicated in memory between nodes of the cluster (default replication factor is 2). So if a worker node fails, then the system can recompute the lost from the the left over copy of the input data. However, if the worker node where a network receiver was running fails, then a tiny bit of data may be lost, that is, the data received by the system but not yet replicated to other node(s). The receiver will be started on a different node and it will continue to receive data.

Since all data is modeled as RDDs with their lineage of deterministic operations, any recomputation always leads to the same result. As a result, all DStream transformations are guaranteed to have _exactly-once_ semantics. That is, the final transformed result will be same even if there were was a worker node failure. However, output operations (like `foreach`) have _at-least once_ semantics, that is, the transformed data may get written to an external entity more than once in the event of a worker failure. While this is acceptable for saving to HDFS using the `saveAs*Files` operations (as the file will simply get over-written by the same data), additional transactions-like mechanisms may be necessary to achieve exactly-once semantics for output operations.

## Failure of the Driver Node
A system that is required to operate 24/7 needs to be able tolerate the failure of the driver node as well. Spark Streaming does this by saving the state of the DStream computation periodically to a HDFS file, that can be used to restart the streaming computation in the event of a failure of the driver node. This checkpointing is enabled by setting a HDFS directory for checkpointing using `ssc.checkpoint(<checkpoint directory>)` as described [earlier](#rdd-checkpointing-within-dstreams). To elaborate, the following state is periodically saved to a file.

1. The DStream operator graph (input streams, output streams, etc.)
1. The configuration of each DStream (checkpoint interval, etc.)
1. The RDD checkpoint files of each DStream

All this is periodically saved in the file `<checkpoint directory>/graph`. To recover, a new Streaming Context can be created with this directory by using

{% highlight scala %}
val ssc = new StreamingContext(checkpointDirectory)
{% endhighlight %}

On calling `ssc.start()` on this new context, the following steps are taken by the system

1. Schedule the transformations and output operations for all the time steps between the time when the driver failed and when it was restarted. This is also done for those time steps that were scheduled but not processed due to the failure. This will make the system recompute all the intermediate data from the checkpointed RDD files, etc.
1. Restart the network receivers, if any, and continue receiving new data.

In the current _alpha_ release, there are two different failure behaviors based on which input sources are used.

1. _Using HDFS files as input source_ - Since the data is reliably stored on HDFS, all data can re-computed and therefore no data will be lost due to any failure.
1. _Using any input source that receives data through a network_ - As aforesaid, the received input data is replicated in memory to multiple nodes. Since, all the data in the Spark worker's memory is lost when the Spark driver fails, the past input data will not be accessible and driver recovers. Hence, if stateful and window-based operations are used (like `updateStateByKey`, `window`, `countByValueAndWindow`, etc.), then the intermediate state will not be recovered completely.

In future releases, this behaviour will be fixed for all input sources, that is, all data will be recovered irrespective of which input sources are used. Note that for non-stateful transformations like `map`, `count`, and `reduceByKey`, with _all_ input streams, the system, upon restarting, will continue to receive and process new data.

To better understand the behavior of the system under driver failure with a HDFS source, lets consider what will happen with a file input stream Specifically, in the case of the file input stream, it will correctly identify new files that were created while the driver was down and process them in the same way as it would have if the driver had not failed. To explain further in the case of file input stream, we shall use an example. Lets say, files are being generated every second, and a Spark Streaming program reads every new file and output the number of lines in the file. This is what the sequence of outputs would be with and without a driver failure.

<table class="table">
    <!-- Results table headers -->
    <tr>
      <th> Time </th>
      <th> Number of lines in input file </th>
      <th> Output without driver failure </th>
      <th> Output with driver failure </th>
    </tr>
    <tr>
      <td>1</td>
      <td>10</td>
      <td>10</td>
      <td>10</td>
    </tr>
    <tr>
      <td>2</td>
      <td>20</td>
      <td>20</td>
      <td>20</td>
    </tr>
    <tr>
      <td>3</td>
      <td>30</td>
      <td>30</td>
      <td>30</td>
    </tr>
    <tr>
      <td>4</td>
      <td>40</td>
      <td>40</td>
      <td>[DRIVER FAILS]<br />no output</td>
    </tr>
    <tr>
      <td>5</td>
      <td>50</td>
      <td>50</td>
      <td>no output</td>
    </tr>
    <tr>
      <td>6</td>
      <td>60</td>
      <td>60</td>
      <td>no output</td>
    </tr>
    <tr>
      <td>7</td>
      <td>70</td>
      <td>70</td>
      <td>[DRIVER RECOVERS]<br />40, 50, 60, 70</td>
    </tr>
    <tr>
      <td>8</td>
      <td>80</td>
      <td>80</td>
      <td>80</td>
    </tr>
    <tr>
      <td>9</td>
      <td>90</td>
      <td>90</td>
      <td>90</td>
    </tr>
    <tr>
      <td>10</td>
      <td>100</td>
      <td>100</td>
      <td>100</td>
    </tr>
</table>

If the driver had crashed in the middle of the processing of time 3, then it will process time 3 and output 30 after recovery.

# Java API

Similar to [Spark's Java API](java-programming-guide.html), we also provide a Java API for Spark Streaming which allows all its features to be accessible from a Java program. This is defined in [spark.streaming.api.java] (api/streaming/index.html#spark.streaming.api.java.package) package and includes [JavaStreamingContext](api/streaming/index.html#spark.streaming.api.java.JavaStreamingContext) and [JavaDStream](api/streaming/index.html#spark.streaming.api.java.JavaDStream) classes that provide the same methods as their Scala counterparts, but take Java functions (that is, Function, and Function2) and return Java data and collection types. Some of the key points to note are:

1. Functions for transformations must be implemented as subclasses of [Function](api/core/index.html#spark.api.java.function.Function) and [Function2](api/core/index.html#spark.api.java.function.Function2)
1. Unlike the Scala API, the Java API handles DStreams for key-value pairs using a separate [JavaPairDStream](api/streaming/index.html#spark.streaming.api.java.JavaPairDStream) class(similar to [JavaRDD and JavaPairRDD](java-programming-guide.html#rdd-classes). DStream functions like `map` and `filter` are implemented separately by JavaDStreams and JavaPairDStream to return DStreams of appropriate types.

Spark's [Java Programming Guide](java-programming-guide.html) gives more ideas about using the Java API. To extends the ideas presented for the RDDs to DStreams, we present parts of the Java version of the same NetworkWordCount example presented above. The full source code is given at `<spark repo>/examples/src/main/java/spark/streaming/examples/JavaNetworkWordCount.java`

The streaming context and the socket stream from input source is started by using a `JavaStreamingContext`, that has the same parameters and provides the same input streams as its Scala counterpart.

{% highlight java %}
JavaStreamingContext ssc = new JavaStreamingContext(mesosUrl, "NetworkWordCount", Seconds(1));
JavaDStream<String> lines = ssc.socketTextStream(ip, port);
{% endhighlight %}


Then the `lines` are split into words by using the `flatMap` function and [FlatMapFunction](api/core/index.html#spark.api.java.function.FlatMapFunction).

{% highlight java %}
JavaDStream<String> words = lines.flatMap(
  new FlatMapFunction<String, String>() {
    @Override
    public Iterable<String> call(String x) {
      return Lists.newArrayList(x.split(" "));
    }
  });
{% endhighlight %}

The `words` is then mapped to a [JavaPairDStream](api/streaming/index.html#spark.streaming.api.java.JavaPairDStream) of `(word, 1)` pairs using `map` and [PairFunction](api/core/index.html#spark.api.java.function.PairFunction). This is  reduced by using `reduceByKey` and [Function2](api/core/index.html#spark.api.java.function.Function2).

{% highlight java %}
JavaPairDStream<String, Integer> wordCounts = words.map(
  new PairFunction<String, String, Integer>() {
    @Override
    public Tuple2<String, Integer> call(String s) throws Exception {
      return new Tuple2<String, Integer>(s, 1);
    }
  }).reduceByKey(
  new Function2<Integer, Integer, Integer>() {
    @Override
    public Integer call(Integer i1, Integer i2) throws Exception {
      return i1 + i2;
    }
  });
{% endhighlight %}



# Where to Go from Here
* Documentation - [Scala](api/streaming/index.html#spark.streaming.package) and [Java](api/streaming/index.html#spark.streaming.api.java.package)
* More examples - [Scala](https://github.com/mesos/spark/tree/master/examples/src/main/scala/spark/streaming/examples) and [Java](https://github.com/mesos/spark/tree/master/examples/src/main/java/spark/streaming/examples)