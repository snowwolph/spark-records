/*
 * Copyright 2017 Simeon Simeonov and Swoop, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swoop.spark.records

import org.apache.spark.sql.{Column, DataFrame, Dataset, Encoder, functions => f}


/** Base trait for the implicits that provide hints about how records are persisted. */
trait RecordEnvironment extends Serializable {

  /** Returns a filter that identifies error records. */
  def errorFilter: Column

  /** Returns a filter that identifies data records.
    * The default implementation is to invert [[errorFilter]].
    */
  def dataFilter: Column = f.not(errorFilter)

  /** Returns only the error records in the provided dataset */
  def errorRecords[Rec <: Record[_, _]](dsRecords: Dataset[Rec]): Dataset[Rec] =
    dsRecords.where(errorFilter)

  /** Returns a dataframe with the data of data records (the record envelope is removed). */
  def recordDataframe[Rec <: Record[_, _]](dsRecords: Dataset[Rec]): DataFrame =
    dsRecords.where(dataFilter).select("data.*")

  /** Returns a dataset with the data of data records (the record envelope is removed). */
  def recordData[A <: Product, Rec <: Record[A, _]](dsRecords: Dataset[Rec])(implicit enc: Encoder[A]): Dataset[A] =
    dsRecords.flatMap(_.data)
}


/** A flat record environment. */
class FlatRecordEnvironment() extends RecordEnvironment {

  /** Returns an error filter that selects all records where `features` has the error bit set. */
  def errorFilter: Column = f.col("features").bitwiseAND(Issue.Error.featureMask) =!= 0

}

/** A partitioned error environment.
  *
  * @param colName The record category partition column name.
  *                Conventionally, this is `par_cat`.
  * @param errorValue The category value for the partition with error records.
  *                   Conventionally, this is `bad`.
  */
class PartitionedRecordEnvironment(colName: String, errorValue: String) extends RecordEnvironment {

  /** Returns an error filter that only includes the partitions with error data */
  def errorFilter: Column = f.col(colName) === errorValue

  /** Returns an error filter that excludes the partitions with error data */
  override def dataFilter: Column = f.col(colName) =!= errorValue

}
