-- to create function for getting total plans, brought by customer

DELIMITER $$
CREATE FUNCTION getTotalPlans(cus_id int)
returns int
DETERMINISTIC
BEGIN
    DECLARE totalPlans int default 10;
    set totalPlans = (SELECT count(*) FROM payment WHERE payment.customerId = cus_id);
    return totalPlans;
END; $$
DELIMITER $$


-- Create trigger to update dob 

DELIMITER $$ 
create TRIGGER cust_age_trigger_insert 
BEFORE INSERT  on customer for each row 
BEGIN
    set new.age = TIMESTAMPDIFF(YEAR, NEW.DOB, CURDATE());
END; $$
DELIMITER ;;

