package com.scala_spark_df.pivot

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import org.apache.spark.sql.{Row, SparkSession}

import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer, ListBuffer, Map}


object Pivot {
  val TypeMeasurement = "measurement"
  val TypeTest = "test"

  val TestPassed = "passed"
  val TestFailed = "failed"

  val AggFirst = "first"
  val AggLast = "last"

  implicit def pivot(df: DataFrame) = new PivotImplicit(df)
}

class PivotImplicit(df: DataFrame) extends Serializable {

  case class features(name: String, value: Double)

  val spark = SparkSession.builder().appName("IsbnEncoderTest").master("local").getOrCreate()

  val schemaRes =
    StructType(
      Array(
        StructField("part", StringType),
        StructField("location", StringType),
        StructField("test", StringType),
        StructField("testResult", StringType),
        StructField("aggregation", StringType),
        StructField("features", ArrayType(StructType(
          Array(
            StructField("name", StringType),
            StructField("value", DoubleType)
          )
        )))
      )
    )

  /**
    * Pivots machine data
    *
    * @return machine data pivoted
    */

  def getTests(): DataFrame = {
    var recordsBuf: Seq[Row] = Seq()
    var firstTestSuite = true
    var saveLocation = ""
    var part = ""
    var location = ""
    var name = ""
    var mtype = ""
    var unixTimestamp = 0L
    var value = 0d
    var testResult = ""
    var testMap: Map[String, Double] = Map()
    var mtypeTestListBuf: ListBuffer[(String, String)] = ListBuffer()
    var mtypeBuf: ArrayBuffer[String] = ArrayBuffer()
    var consecutiveRepeat = false

    df.rdd.collect().foreach(row => {
      part = row.get(0).asInstanceOf[String]
      location = row.get(1).asInstanceOf[String]
      name = row.get(2).asInstanceOf[String]
      mtype = row.get(3).asInstanceOf[String]
      unixTimestamp = row.get(4).asInstanceOf[Long]
      value = row.get(5).asInstanceOf[Double]
      testResult = row.get(6).asInstanceOf[String]

      if (mtypeBuf.contains(mtype)) {
        if (mtypeBuf(mtypeBuf.length - 1) != mtype) {
          mtypeBuf.clear()
          mtypeBuf += mtype
          consecutiveRepeat = false
        }
        else
          consecutiveRepeat = true
      }
      else {
        mtypeBuf += mtype
      }

      if (mtype == Pivot.TypeMeasurement) {
        if (!firstTestSuite && !consecutiveRepeat) {

          var testBuf: ArrayBuffer[Row] = ArrayBuffer()
          testMap.foreach { kv => testBuf += Row(kv._1, kv._2) }

          var r1 = Row(part,
                       saveLocation,
                       mtypeTestListBuf(0)._1,
                       mtypeTestListBuf(0)._2,
                       "first",
                       testBuf)
          var r2 = Row(part,
                       saveLocation,
                       {if (mtypeTestListBuf.length == 1) mtypeTestListBuf(0)._1 else mtypeTestListBuf(1)._1},
                       {if (mtypeTestListBuf.length == 1) mtypeTestListBuf(0)._2 else mtypeTestListBuf(1)._2},
                       "first",
                       testBuf)

          recordsBuf ++= Seq(r1, r2)
          testMap.clear()
          mtypeTestListBuf.clear()
        }

        firstTestSuite = false
        saveLocation = location
        testMap += (name -> value)
      }
      else {  // mtype == Pivot.TypeTest
        saveLocation = location
        mtypeTestListBuf += ((name, testResult))
      }
    })

    var testBuf: ArrayBuffer[Row] = ArrayBuffer()
    testMap.foreach{kv => testBuf += Row(kv._1, kv._2)}
    var r1 = Row(part,
                 location,
                 mtypeTestListBuf(0)._1,
                 mtypeTestListBuf(0)._2,
                 {if (firstTestSuite) "first" else "last"},
                 testBuf)
    var r2 = Row(part,
                 location,
                 {if (mtypeTestListBuf.length == 1) mtypeTestListBuf(0)._1 else mtypeTestListBuf(1)._1},
                 {if (mtypeTestListBuf.length == 1) mtypeTestListBuf(0)._2 else mtypeTestListBuf(1)._2},
                 "last",
                 testBuf)

    recordsBuf ++= Seq(r1, r2)

    val rows2Rdd = spark.sparkContext.parallelize(recordsBuf, 4)
    val dfnew = spark.createDataFrame(rows2Rdd, schemaRes)
    dfnew
  }
}