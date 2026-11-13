-- A6: persistent source of incident ids (INC1001, INC1002, ...), so ids never repeat across restarts.
-- Incident saves are upserts, so a repeated id would silently overwrite an existing incident.
CREATE SEQUENCE incident_id_seq START WITH 1001 INCREMENT BY 1;
