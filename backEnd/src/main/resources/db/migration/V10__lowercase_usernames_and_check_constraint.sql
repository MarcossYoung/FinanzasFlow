-- ============================================================
-- V10: Normalize existing usernames to lowercase and enforce
--      the invariant with a CHECK constraint.
--
-- Strategy for duplicate collisions (e.g. 'Marcos' and 'marcos'):
--   Keep the account with the lowest id (earliest created).
--   Rename all later colliding accounts to username_dup_<id>
--   so they are still accessible/recoverable and are not lost.
-- ============================================================

-- Step 1: Rename non-earliest duplicates to make them unique
--         after lowercasing.
UPDATE usuarios
SET username = username || '_dup_' || CAST(id AS VARCHAR(20))
WHERE id NOT IN (
    SELECT MIN(id)
    FROM usuarios
    GROUP BY LOWER(username)
)
  AND LOWER(username) IN (
    SELECT LOWER(username)
    FROM usuarios
    GROUP BY LOWER(username)
    HAVING COUNT(*) > 1
);

-- Step 2: Lowercase all usernames.
UPDATE usuarios
SET username = LOWER(username);

-- Step 3: Enforce the lowercase invariant at the DB level.
--         The existing UNIQUE constraint on username remains in place;
--         this CHECK prevents mixed-case values from bypassing the
--         application layer.
--         Syntax is valid for PostgreSQL (Neon) and H2 2.x.
ALTER TABLE usuarios
    ADD CONSTRAINT usuarios_username_lowercase
    CHECK (username = LOWER(username));
