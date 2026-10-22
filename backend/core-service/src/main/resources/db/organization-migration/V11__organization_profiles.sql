ALTER TABLE department
    ADD COLUMN description VARCHAR(2000),
    ADD COLUMN phone_number VARCHAR(30),
    ADD COLUMN email VARCHAR(100);
ALTER TABLE staff
    ADD COLUMN date_of_birth DATE,
    ADD COLUMN gender VARCHAR(10),
    ADD COLUMN address VARCHAR(255),
    ADD COLUMN hire_date DATE,
    ADD COLUMN qualification VARCHAR(100),
    ADD CONSTRAINT ck_staff_profile_gender CHECK (gender IS NULL OR gender IN ('MALE','FEMALE','OTHER')),
    ADD CONSTRAINT ck_staff_profile_dates CHECK (date_of_birth IS NULL OR hire_date IS NULL OR hire_date > date_of_birth);
