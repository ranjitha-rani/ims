import streamlit as st
import mysql.connector
import pandas as pd

db = mysql.connector.connect(
    host='localhost',
    user='root',
    password='password',
    database='IMS_549'
)

c = db.cursor(buffered=True)
c.execute('CREATE DATABASE IF NOT EXISTS IMS_549')

# create functions----------------------------
def create_admin():
    c.execute('CREATE TABLE IF NOT EXISTS admin (id INT AUTO_INCREMENT PRIMARY KEY,firstName varchar(50), lastName varchar(50), email varchar(60), password VARCHAR(255))')

def create_customer():
    c.execute('CREATE TABLE IF NOT EXISTS customer (id INT AUTO_INCREMENT PRIMARY KEY,firstName varchar(50), lastName varchar(50),mobile varchar(10), email varchar(60), password VARCHAR(255), dob date, age varchar(3) )')
    
def create_plan():
    c.execute('CREATE TABLE IF NOT EXISTS plan (id INT AUTO_INCREMENT PRIMARY KEY,name varchar(50), description varchar(50), cost varchar(50), duration varchar(50), adminId int, foreign key (adminId) references admin(id) ON UPDATE CASCADE ON DELETE CASCADE)')

def create_payment():
    c.execute('CREATE TABLE IF NOT EXISTS payment (id INT AUTO_INCREMENT PRIMARY KEY, purpose varchar(50), date datetime, customerId int, planId int,adminId int, foreign key (customerId) references customer(id) ON UPDATE CASCADE ON DELETE CASCADE, foreign key (planId) references plan(id) ON UPDATE CASCADE ON DELETE CASCADE, foreign key (adminId) references admin(id) ON UPDATE CASCADE ON DELETE CASCADE)')

def create_claims():
    c.execute('CREATE TABLE IF NOT EXISTS claims (id INT AUTO_INCREMENT PRIMARY KEY,reason varchar(50), status varchar(50), date datetime, paymentId int, customerId int, planId int,adminId int,foreign key (paymentId) references payment(id) ON UPDATE CASCADE ON DELETE CASCADE, foreign key (customerId) references customer(id) ON UPDATE CASCADE ON DELETE CASCADE, foreign key (planId) references plan(id) ON UPDATE CASCADE ON DELETE CASCADE, foreign key (adminId) references admin(id) ON UPDATE CASCADE ON DELETE CASCADE)')



# login functions----------------------- 

def login_admin(email, password):
    c.execute('SELECT * FROM admin WHERE email = %s AND password = %s',
              (email, password))
    data = c.fetchall()
    return data

def login_customer(email, password):
    c.execute('SELECT * FROM customer WHERE email = %s AND password = %s',
              (email, password))
    data = c.fetchall()
    return data

# insert functions -------------------------

def add_admin(firstName, lastName, email, password):
    create_admin()
    c.execute('INSERT INTO admin (firstName, lastName, email, password) VALUES (%s, %s, %s, %s)',
              (firstName, lastName, email, password))
    db.commit()

def add_customer(firstName, lastName, mobile, email, password, dob):
    create_customer()
    c.execute('INSERT INTO customer (firstName, lastName, mobile, email, password, dob) VALUES (%s, %s, %s, %s, %s, %s)',
              (firstName, lastName, mobile, email, password, dob))
    db.commit()

def add_plan(name, description, cost, duration, adminId):
    create_plan()
    c.execute('INSERT INTO plan (name, description, cost, duration, adminId) VALUES (%s, %s, %s, %s, %s)',
              (name, description, cost, duration, adminId))
    db.commit()

def add_payment(purpose, date, customerId, planId, adminId):
    create_payment()
    c.execute('INSERT INTO payment (purpose, date, customerId, planId, adminId) VALUES (%s, %s, %s, %s, %s)',
              (purpose, date, customerId, planId, adminId))
    db.commit()

def add_claims(reason, status, date, paymentId, customerId, planId, adminId):
    create_claims()
    c.execute('INSERT INTO claims (reason, status, date, paymentId, customerId, planId, adminId) VALUES (%s, %s, %s, %s, %s, %s, %s)',
              (reason, status, date, paymentId, customerId, planId, adminId))
    db.commit()

# view functions -------------------------

def view_admin(email, password):
    c.execute('SELECT * FROM admin where email= "{}" and password = "{}"'.format(email, password))
    data = c.fetchall()
    return data

def view_customer(email, password):
    c.execute('SELECT * FROM customer where email= "{}" and password = "{}"'.format(email, password))
    data = c.fetchall()
    return data

def view_plan(adminId):
    c.execute('SELECT * FROM plan where adminId= "{}"'.format(adminId))
    data = c.fetchall()
    return data

def view_payment(customerId):
    c.execute('SELECT * FROM payment where customerId= "{}"'.format(customerId))
    data = c.fetchall()
    return data

def view_claims_customer(customerId):
    c.execute('SELECT * FROM claims where customerId= "{}"'.format(customerId))
    data = c.fetchall()
    return data

def view_claims_admin(adminId):
    # st.write(adminId)
    c.execute('SELECT * FROM claims where adminId= "{}"'.format(adminId))
    data = c.fetchall()
    return data


# delete functions -------------------------

def delete_admin(id):
    c.execute('DELETE FROM admin WHERE id = "{}"'.format(id))
    db.commit()

def delete_customer(id):
    c.execute('DELETE FROM customer WHERE id = "{}"'.format(id))
    db.commit()

def delete_plan(id):
    c.execute('DELETE FROM plan WHERE id = "{}"'.format(id))
    db.commit()

def delete_payment(id):
    c.execute('DELETE FROM payment WHERE id = "{}"'.format(id))
    db.commit()

def delete_claims(id):
    c.execute('DELETE FROM claims WHERE id = "{}"'.format(id))
    db.commit()

# update functions

def update_customer(firstName, lastName, mobile, email, password, id):
    c.execute('UPDATE customer set firstName= "{}" , lastName="{}", mobile="{}", email="{}", password="{}" where id = {} '.format(firstName, lastName, mobile, email, password,id))
    db.commit()
    c.execute('SELECT * FROM customer where id= "{}"'.format(id))
    data = c.fetchall()
    return data

def update_admin(firstName, lastName, email, password, id):
    c.execute('UPDATE admin set firstName= "{}" , lastName="{}", email="{}", password="{}" where id = {}'.format(firstName, lastName, email, password, id))
    db.commit()
    c.execute('SELECT * FROM admin where id= "{}"'.format(id))
    data = c.fetchall()
    return data

def update_claim(status, id):
    c.execute('UPDATE CLAIMS set status = "{}" where id = "{}"'.format(status, id))
    db.commit()
 
def update_plan(planName, planDescription, planPrice, planDuration, id):
    c.execute('UPDATE plan set name = "{}" , description = "{}", cost = "{}", duration = "{}" where id = "{}"'.format(planName, planDescription, planPrice, planDuration, id))
    db.commit()

# extra functions

def get_all_admins():
    c.execute('SELECT id, firstName, lastName, email FROM ADMIN')
    data = c.fetchall()
    return data

def get_plan_using_id(planId):
    c.execute('SELECT * FROM plan where id= "{}"'.format(planId))
    data = c.fetchall()
    return data

def get_customer_using_id(id):
    c.execute('SELECT * FROM customer where id= "{}"'.format(id))
    data = c.fetchall()
    return data

def get_admin_using_id(id):
    c.execute('SELECT * FROM admin where id= "{}"'.format(id))
    data = c.fetchall()
    return data

def get_claimdetails_using_id(id):
    c.execute('SELECT * FROM claims where id = "{}"'.format(id))
    data = c.fetchall()
    return data

def get_payments_using_custid(id):
    c.execute('SELECT * FROM payment where adminId = "{}"'.format(id))
    data = c.fetchall()
    return data

def get_payment_using_id(id):
    c.execute('SELECT * FROM payment where id = "{}"'.format(id))
    data = c.fetchall()
    return data

def get_claims_using_custid(id):
    c.execute('SELECT * FROM claims where customerId = "{}"'.format(id))
    data = c.fetchall()
    return data

# call functions

def get_total_plans_cust(id):
    c.execute('select getTotalPlans({})'.format(id))
    data = c.fetchall()
    return data