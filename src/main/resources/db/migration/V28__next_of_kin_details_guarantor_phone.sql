-- Next of kin gets a fuller profile (email, relationship to the member, and their level of
-- authority to act on the member's behalf), and every guarantor — not just the ones matched to
-- an existing member — gets a phone number on file alongside their email.
ALTER TABLE members ADD next_of_kin_email NVARCHAR(255) NULL;
ALTER TABLE members ADD next_of_kin_relationship NVARCHAR(100) NULL;
ALTER TABLE members ADD next_of_kin_authority_level NVARCHAR(100) NULL;

ALTER TABLE member_guarantors ADD phone NVARCHAR(30) NULL;
