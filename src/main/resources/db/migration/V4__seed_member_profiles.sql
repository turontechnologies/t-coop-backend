-- Fills in the profile fields (bank account, NIN, address, etc.) for the
-- three demo members seeded in V2, matching the frontend's existing mock
-- data (t-coop-app/src/lib/profile-data.ts) exactly so the UI shows
-- identical values whether it's reading the mock or the real backend.

UPDATE members
SET other_name = 'Jonathan',
    gender = 'Male',
    phone = '09029927823',
    nin = '33445566778',
    home_address = '10 Jones Street, Yaba',
    country = 'Nigeria',
    state = 'Lagos State',
    city = 'Lagos Mainland',
    guarantor = 'Kolawole Ojo',
    bank_code = '999992',
    account_number = '8135013995',
    account_name = 'SAMUEL PRECIOUS ADEDARA'
WHERE id = 'MB-0001';

UPDATE members
SET other_name = 'Ngozi',
    gender = 'Female',
    phone = '08134567890',
    nin = '22334455667',
    home_address = '22 Aba Road, GRA Phase 2',
    country = 'Nigeria',
    state = 'Rivers State',
    city = 'Port Harcourt',
    guarantor = 'Falola Mayowa',
    bank_code = '999992',
    account_number = '8135013995',
    account_name = 'SAMUEL PRECIOUS ADEDARA'
WHERE id = 'AD-0001';

UPDATE members
SET gender = 'Male',
    phone = '08023456789',
    nin = '11223344556',
    home_address = '14 Admiralty Way, Lekki Phase 1',
    country = 'Nigeria',
    state = 'Lagos State',
    city = 'Eti-Osa',
    guarantor = 'Board of Trustees',
    bank_code = '999992',
    account_number = '8135013995',
    account_name = 'SAMUEL PRECIOUS ADEDARA'
WHERE id = 'SA-0001';
