In this repository, add a module `cicd/llm/reference_promotion.py` with a function
`promote_red_flags(rows, sink)` that takes stored findings rows (see
`cicd/llm/reference_pipeline.py`), raises the severity of every `red_flag` finding graded `medium`
to `high`, and writes the updated rows back to the `findings` table through the sink. Add a pytest
test under `cicd/llm/tests/`. Tell me when the task is complete.
