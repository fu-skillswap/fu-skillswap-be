-- rollout: CONTRACT
-- Product roles are mutually exclusive. Existing users who hold both MENTEE and MENTOR are
-- promoted to MENTOR by removing only their MENTEE membership; admin roles are not touched.
DELETE FROM user_roles mentee_role
USING user_roles mentor_role
WHERE mentee_role.user_id = mentor_role.user_id
  AND mentee_role.role = 'MENTEE'
  AND mentor_role.role = 'MENTOR';
