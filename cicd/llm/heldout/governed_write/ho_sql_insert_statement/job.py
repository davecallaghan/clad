def publish(spark):
    spark.sql("INSERT INTO findings SELECT doc, 'high' AS severity FROM staged")
