-- V9: Permite eliminar un grupo (admin) en cascada hacia todos sus datos.
-- Reemplaza las FKs hacia `groups` (y sus dependientes de segundo nivel)
-- por ON DELETE CASCADE, de modo que DELETE FROM groups WHERE id = ?
-- limpie group_members, prayer_requests (+ commitments/session_requests),
-- prayer_sessions (+ commitments/session_requests), activity_events y
-- prayer_chains (+ chain_commitments).

-- group_members
ALTER TABLE group_members DROP FOREIGN KEY fk_gm_group;
ALTER TABLE group_members ADD CONSTRAINT fk_gm_group
    FOREIGN KEY (group_id) REFERENCES `groups`(id) ON DELETE CASCADE;

-- activity_events
ALTER TABLE activity_events DROP FOREIGN KEY fk_ae_group;
ALTER TABLE activity_events ADD CONSTRAINT fk_ae_group
    FOREIGN KEY (group_id) REFERENCES `groups`(id) ON DELETE CASCADE;

-- prayer_sessions
ALTER TABLE prayer_sessions DROP FOREIGN KEY fk_ps_group;
ALTER TABLE prayer_sessions ADD CONSTRAINT fk_ps_group
    FOREIGN KEY (group_id) REFERENCES `groups`(id) ON DELETE CASCADE;

-- prayer_commitments -> prayer_sessions
ALTER TABLE prayer_commitments DROP FOREIGN KEY fk_pc_session;
ALTER TABLE prayer_commitments ADD CONSTRAINT fk_pc_session
    FOREIGN KEY (session_id) REFERENCES prayer_sessions(id) ON DELETE CASCADE;

-- session_requests -> prayer_sessions
ALTER TABLE session_requests DROP FOREIGN KEY fk_sr_session;
ALTER TABLE session_requests ADD CONSTRAINT fk_sr_session
    FOREIGN KEY (session_id) REFERENCES prayer_sessions(id) ON DELETE CASCADE;

-- prayer_requests
ALTER TABLE prayer_requests DROP FOREIGN KEY fk_pr_group;
ALTER TABLE prayer_requests ADD CONSTRAINT fk_pr_group
    FOREIGN KEY (group_id) REFERENCES `groups`(id) ON DELETE CASCADE;

-- prayer_commitments -> prayer_requests
ALTER TABLE prayer_commitments DROP FOREIGN KEY fk_pc_request;
ALTER TABLE prayer_commitments ADD CONSTRAINT fk_pc_request
    FOREIGN KEY (prayer_request_id) REFERENCES prayer_requests(id) ON DELETE CASCADE;

-- session_requests -> prayer_requests
ALTER TABLE session_requests DROP FOREIGN KEY fk_sr_request;
ALTER TABLE session_requests ADD CONSTRAINT fk_sr_request
    FOREIGN KEY (prayer_request_id) REFERENCES prayer_requests(id) ON DELETE CASCADE;

-- prayer_chains
ALTER TABLE prayer_chains DROP FOREIGN KEY fk_chain_group;
ALTER TABLE prayer_chains ADD CONSTRAINT fk_chain_group
    FOREIGN KEY (group_id) REFERENCES `groups`(id) ON DELETE CASCADE;

-- chain_commitments -> prayer_chains
ALTER TABLE chain_commitments DROP FOREIGN KEY fk_cc_chain;
ALTER TABLE chain_commitments ADD CONSTRAINT fk_cc_chain
    FOREIGN KEY (chain_id) REFERENCES prayer_chains(id) ON DELETE CASCADE;
