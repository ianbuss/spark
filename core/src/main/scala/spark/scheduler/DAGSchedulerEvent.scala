/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package spark.scheduler

import java.util.Properties

import spark.scheduler.cluster.TaskInfo
import scala.collection.mutable.Map

import spark._
import spark.executor.TaskMetrics

/**
 * Types of events that can be handled by the DAGScheduler. The DAGScheduler uses an event queue
 * architecture where any thread can post an event (e.g. a task finishing or a new job being
 * submitted) but there is a single "logic" thread that reads these events and takes decisions.
 * This greatly simplifies synchronization.
 */
private[spark] sealed trait DAGSchedulerEvent

private[spark] case class JobSubmitted(
    finalRDD: RDD[_],
    func: (TaskContext, Iterator[_]) => _,
    partitions: Array[Int],
    allowLocal: Boolean,
    callSite: String,
    listener: JobListener,
    properties: Properties = null)
  extends DAGSchedulerEvent

private[spark] case class BeginEvent(task: Task[_], taskInfo: TaskInfo) extends DAGSchedulerEvent

private[spark] case class CompletionEvent(
    task: Task[_],
    reason: TaskEndReason,
    result: Any,
    accumUpdates: Map[Long, Any],
    taskInfo: TaskInfo,
    taskMetrics: TaskMetrics)
  extends DAGSchedulerEvent

private[spark] case class ExecutorGained(execId: String, host: String) extends DAGSchedulerEvent

private[spark] case class ExecutorLost(execId: String) extends DAGSchedulerEvent

private[spark] case class TaskSetFailed(taskSet: TaskSet, reason: String) extends DAGSchedulerEvent

private[spark] case object StopDAGScheduler extends DAGSchedulerEvent
