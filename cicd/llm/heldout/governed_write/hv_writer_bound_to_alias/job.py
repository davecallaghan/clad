def publish(sink, rows):
    put = sink.write_rows
    put('findings', rows)
