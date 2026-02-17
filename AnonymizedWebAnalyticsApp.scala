package com.example.spark

import org.apache.spark.sql.{SparkSession, DataFrame}
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions._
import org.apache.spark.storage.StorageLevel

object AnonymizedWebAnalyticsApp {

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("Anonymized Web Analysis")
      .getOrCreate()

    import spark.implicits._

    // 100M Hits Schema
    val hitsSchema = StructType(Array(
      StructField("WatchID", LongType, true),
      StructField("JavaEnable", ShortType, true),
      StructField("Title", StringType, true),
      StructField("GoodEvent", ShortType, true),
      StructField("EventTime", TimestampType, true),
      StructField("EventDate", DateType, true),
      StructField("CounterID", LongType, true),
      StructField("ClientIP", LongType, true),
      StructField("RegionID", LongType, true),
      StructField("UserID", LongType, true),
      StructField("CounterClass", ByteType, true),
      StructField("OS", ShortType, true),
      StructField("UserAgent", ShortType, true),
      StructField("URL", StringType, true),
      StructField("Referer", StringType, true)
    ))

    val visitsSchema = StructType(Array(
      StructField("CounterID", LongType, true),
      StructField("StartDate", DateType, true),
      StructField("Sign", ShortType, true),
      StructField("IsNew", ShortType, true),
      StructField("VisitID", LongType, true),
      StructField("UserID", LongType, true),
      StructField("StartTime", TimestampType, true),
      StructField("Duration", LongType, true),
      StructField("UTCStartTime", TimestampType, true),
      StructField("PageViews", IntegerType, true),
      StructField("Hits", IntegerType, true),
      StructField("IsBounce", ShortType, true)
    ))

    try {
      println(">>> Loading Datasets from HDFS...")
      val hitsDF = spark.read.option("sep", "\t").option("nullValue", "\\N").schema(hitsSchema).csv("hdfs:///user/data/hits_v1.tsv")
      val visitsDF = spark.read.option("sep", "\t").option("nullValue", "\\N").schema(visitsSchema).csv("hdfs:///user/data/visits_v1.tsv")

      hitsDF.createOrReplaceTempView("hits_v1")
      visitsDF.createOrReplaceTempView("visits_v1")

      // ==========================================
      // PART 0: Storage & Cache Demonstration
      // ==========================================
      spark.sparkContext.setJobGroup("Storage_Demo", "Demonstrating RDD/DataFrame Caching")
      println(">>> Demonstration: Storage & Caching")
      
      // 1. Cache a filtered DataFrame with a specific name
      // Named caches are much easier to find in the Storage Tab
      val filteredHits = hitsDF.filter($"RegionID" === 2).setName("High_Priority_Hits_Region_2")
      filteredHits.persist(StorageLevel.MEMORY_ONLY)
      
      // Trigger action to fill the cache
      println(s">>> [Storage] Caching Region 2 hits: ${filteredHits.count()} records")

      // 2. Cache with MEMORY_AND_DISK level
      val heavyUsers = visitsDF
        .groupBy("UserID")
        .agg(sum("PageViews").as("total_pvs"))
        .filter($"total_pvs" > 10)
        .setName("Heavy_Users_PageViews_Analysis")
      
      heavyUsers.persist(StorageLevel.MEMORY_AND_DISK)
      
      // Trigger action
      println(s">>> [Storage] Caching Heavy Users: ${heavyUsers.count()} records")

      // 3. Cache a raw RDD
      val sampleRdd = spark.sparkContext.parallelize(1 to 100000, 20).setName("Custom_Sample_RDD_100K")
      sampleRdd.cache()
      sampleRdd.map(x => (x % 100, 1)).reduceByKey(_ + _).count()

      // ==========================================
      // PART 1: Original Analysis Queries
      // ==========================================

      // Step 1: Basic Stats
      spark.sparkContext.setJobGroup("Step_1", "Query 1: Total Hits and Unique Users")
      println(">>> Query 1: Total Hits and Unique Users")
      spark.sql("SELECT count(*) as total_hits, count(distinct UserID) as unique_users FROM hits_v1").show()

      // Step 2: Top Regions
      spark.sparkContext.setJobGroup("Step_2", "Query 2: Top 10 Regions by Hits")
      println(">>> Query 2: Top 10 Regions by Hits")
      spark.sql("""
        SELECT RegionID, count(*) AS hits_count
        FROM hits_v1
        GROUP BY RegionID
        ORDER BY hits_count DESC
        LIMIT 10
      """).show()

      // Step 3: Visit Duration Analysis
      spark.sparkContext.setJobGroup("Step_3", "Query 3: Average Visit Duration and PageViews")
      println(">>> Query 3: Average Visit Duration and PageViews")
      spark.sql("""
        SELECT
          avg(Duration) as avg_duration,
          avg(PageViews) as avg_pageviews,
          max(Duration) as max_duration
        FROM visits_v1
      """).show()

      // Step 4: Join Analysis (Visits per OS)
      spark.sparkContext.setJobGroup("Step_4", "Query 4: OS distribution for visits")
      println(">>> Query 4: OS distribution for visits")
      spark.sql("""
        SELECT h.OS, count(distinct v.VisitID) as visit_count
        FROM hits_v1 h
        JOIN visits_v1 v ON h.UserID = v.UserID AND h.CounterID = v.CounterID
        WHERE h.EventDate = v.StartDate
        GROUP BY h.OS
        ORDER BY visit_count DESC
        LIMIT 10
      """).show()

      // ==========================================
      // PART 2: 10,000 Jobs Stress Loop
      // ==========================================
      
      println(">>> Preparing for 10,000 Jobs Loop (Sampled & Skewed)...")
      
      // 人为制造倾斜：将 20% 的 UserID 设为 999
      val skewedHits = hitsDF.sample(0.01)
        .withColumn("UserID", when(rand() < 0.2, 999L).otherwise($"UserID"))
        .persist(StorageLevel.MEMORY_AND_DISK)

      val skewedVisits = visitsDF.sample(0.05)
        .withColumn("UserID", when(rand() < 0.2, 999L).otherwise($"UserID"))
        .persist(StorageLevel.MEMORY_AND_DISK)

      val totalIterations = 100
      for (i <- 1 to totalIterations) {
        if (i % 100 == 0) println(s"Processing Stress Job $i / $totalIterations...")

        spark.sparkContext.setJobGroup(s"Stress_Job_$i", s"Iteration $i: Randomized Join & Agg")

        val randomThreshold = scala.util.Random.nextInt(100)
        
        val jobDf = skewedHits
          .filter($"RegionID" % 100 > randomThreshold)
          .join(skewedVisits, Seq("UserID", "CounterID"))
          .groupBy("OS", "CounterID")
          .agg(count("*").as("cnt"))

        jobDf.count()
      }

      println(">>> All tasks completed.")

    } catch {
      case e: Exception => e.printStackTrace()
    } finally {
      spark.stop()
    }
  }
}
