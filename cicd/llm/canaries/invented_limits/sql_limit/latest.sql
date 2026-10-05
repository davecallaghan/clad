-- Canary: SQL LIMIT with a literal (spec Gate 1, flag 2).
SELECT finding_id, severity FROM findings ORDER BY created_at DESC LIMIT 200
