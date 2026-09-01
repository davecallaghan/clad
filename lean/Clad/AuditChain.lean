import Mathlib.Data.List.Basic

namespace Clad

-- Abstract types for the hash chain model
-- We parameterize over Content and Digest types rather than using axioms
variable {Content : Type} [DecidableEq Content]
variable {Digest : Type} [DecidableEq Digest]

-- An audit record contains content and its chain hash
structure AuditRecord (Content Digest : Type) where
  content : Content
  chainHash : Digest
  deriving DecidableEq

-- Chain integrity: each record's hash = hash(content, previous hash)
-- For the first record, previous hash is the genesis digest
def chainIntegral (hash : Content → Digest → Digest) (genesis : Digest) :
    List (AuditRecord Content Digest) → Prop
  | [] => True
  | r :: rest =>
    r.chainHash = hash r.content genesis ∧
    chainIntegral hash r.chainHash rest

-- Empty chain is trivially integral
theorem empty_chain_integral (hash : Content → Digest → Digest) (genesis : Digest) :
    chainIntegral hash genesis [] := trivial

-- Singleton chain is integral iff its hash matches
theorem singleton_chain_integral (hash : Content → Digest → Digest) (genesis : Digest)
    (r : AuditRecord Content Digest) :
    chainIntegral hash genesis [r] ↔ r.chainHash = hash r.content genesis := by
  simp [chainIntegral]

-- THEOREM 3a: Tamper-Evident Audit Chain
-- Part 1: Content change detection
-- If a chain is integral and we change a record's content but keep its hash,
-- then collision resistance proves the content didn't actually change
theorem content_change_detected
    (hash : Content → Digest → Digest)
    (hash_injective : ∀ c₁ c₂ d, hash c₁ d = hash c₂ d → c₁ = c₂)
    (genesis : Digest)
    (record : AuditRecord Content Digest)
    (h_hash_eq : record.chainHash = hash record.content genesis)
    (tampered : Content)
    (h_same_hash : record.chainHash = hash tampered genesis)
    : record.content = tampered := by
  have : hash record.content genesis = hash tampered genesis := by
    rw [← h_hash_eq, h_same_hash]
  exact hash_injective _ _ _ this

-- Alternative formulation: attempting to tamper leads to contradiction
theorem content_tamper_contradiction
    (hash : Content → Digest → Digest)
    (hash_injective : ∀ (c₁ c₂ : Content) (d : Digest), hash c₁ d = hash c₂ d → c₁ = c₂)
    (r_orig r_tampered : AuditRecord Content Digest)
    (prevHash : Digest)
    (h_orig_hash : r_orig.chainHash = hash r_orig.content prevHash)
    (h_same_chain_hash : r_tampered.chainHash = r_orig.chainHash)
    (h_tampered_hash : r_tampered.chainHash = hash r_tampered.content prevHash)
    (h_different : r_tampered.content ≠ r_orig.content) :
    False := by
  have h1 : hash r_orig.content prevHash = hash r_tampered.content prevHash := by
    rw [← h_orig_hash, ← h_tampered_hash, h_same_chain_hash]
  have h2 : r_orig.content = r_tampered.content := hash_injective _ _ _ h1
  exact h_different h2.symm

-- Theorem 3a, Part 2: Hash change breaks chain
-- If we change a record's hash, the NEXT record's integrity check fails
-- (because the next record was computed from the original hash)
theorem hash_change_breaks_next
    (hash : Content → Digest → Digest)
    (next : AuditRecord Content Digest)
    (originalHash newHash : Digest)
    (h_ne : originalHash ≠ newHash)
    (h_next_linked : next.chainHash = hash next.content originalHash)
    (hash_injective_digest : ∀ c d₁ d₂, hash c d₁ = hash c d₂ → d₁ = d₂)
    : next.chainHash ≠ hash next.content newHash := by
  intro h_eq
  have : originalHash = newHash := by
    exact hash_injective_digest _ _ _ (by rw [← h_next_linked, h_eq])
  exact h_ne this

-- Alternative formulation for hash change breaking successor
theorem hash_change_breaks_successor
    (hash : Content → Digest → Digest)
    (hash_injective2 : ∀ (c : Content) (d₁ d₂ : Digest), hash c d₁ = hash c d₂ → d₁ = d₂)
    (r_modified r_next : AuditRecord Content Digest)
    (original_hash : Digest)
    (h_next_linked : r_next.chainHash = hash r_next.content original_hash)
    (h_hash_changed : r_modified.chainHash ≠ original_hash) :
    r_next.chainHash ≠ hash r_next.content r_modified.chainHash := by
  intro h_eq
  rw [h_next_linked] at h_eq
  have := hash_injective2 r_next.content original_hash r_modified.chainHash h_eq
  exact h_hash_changed this.symm

-- Chain integrity implies all records are correctly linked
theorem chain_integral_all_linked
    (hash : Content → Digest → Digest) (genesis : Digest)
    (records : List (AuditRecord Content Digest))
    (h : chainIntegral hash genesis records) :
    ∀ r ∈ records, ∃ prevHash, r.chainHash = hash r.content prevHash := by
  induction records generalizing genesis with
  | nil => intro r hr; simp at hr
  | cons head tail ih =>
    intro r hr
    simp [chainIntegral] at h
    cases hr with
    | head => exact ⟨genesis, h.1⟩
    | tail _ hmem => exact ih head.chainHash h.2 _ hmem

-- Digest determinism: same content and same previous hash → same chain hash
-- (This is trivially true since hash is a Lean function — all functions are pure)
theorem digest_determinism (hash : Content → Digest → Digest)
    (content : Content) (prevHash : Digest) :
    hash content prevHash = hash content prevHash := rfl

end Clad
