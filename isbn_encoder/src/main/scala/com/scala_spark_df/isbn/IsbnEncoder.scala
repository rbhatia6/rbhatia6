package com.scala_spark_df.isbn

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._
import org.apache.spark.sql.SparkSession
import scala.collection.mutable.ArrayBuffer


object IsbnEncoder {
  implicit def dmcEncoder(df: DataFrame) = new IsbnEncoderImplicit(df)
}

class IsbnEncoderImplicit(df: DataFrame) extends Serializable {

  private var spark: SparkSession = null

  /**
    * Creates a new row for each element of the ISBN code
    *
    * @return a data frame with new rows for each element of the ISBN code
    */
  def explodeIsbn(): DataFrame = {
    var recordsBuf : Seq[(String, Int, String)] = Seq()
    val spark = SparkSession.builder().appName("IsbnEncoderTest").master("local").getOrCreate()

    df.rdd.collect().foreach(row => {
      val name = row.get(0).asInstanceOf[String]
      val year = row.get(1).asInstanceOf[Int]
      val isbn = row.get(2).asInstanceOf[String]
      val isbnregex = """[ISBN: ]+\d{3}-\d{10}""".r
      val regexmatch = isbn match {
        case isbnregex() => true
        case _ => false
      }

      if (regexmatch) {
        val ean = isbn.substring(6, 9)
        val group = isbn.substring(10, 12)
        val publisher = isbn.substring(12, 16)
        val title = isbn.substring(16, 19)

        val r1 = ((name, year, "ISBN-EAN: ".concat(ean)))
        val r2 = ((name, year, "ISBN-GROUP: ".concat(group)))
        val r3 = ((name, year, "ISBN-PUBLISHER: ".concat(publisher)))
        val r4 = ((name, year, "ISBN-TITLE: ".concat(title)))

        recordsBuf ++:= Seq(r1, r2, r3, r4)
      }
    })
    val dfnew = spark.createDataFrame(recordsBuf)
    df.union(dfnew)
  }
}