from delta.tables import DeltaTable


def upsert(spark, updates):
    DeltaTable.forName(spark, 'main.dd.findings').alias('t').merge(updates.alias('s'), 't.id = s.id').whenMatchedUpdateAll().execute()
