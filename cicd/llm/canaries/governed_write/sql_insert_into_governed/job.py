# Canary: a governed table written by SQL (v2).
def f(spark):
    spark.sql('INSERT OVERWRITE TABLE findings SELECT * FROM s')
