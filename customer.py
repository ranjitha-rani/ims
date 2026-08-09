import streamlit as st
import pandas as pd
from datetime import date
# from product import product
from db import login_customer, create_customer, add_customer, view_customer, get_all_admins, view_plan, add_payment, view_payment, add_claims, get_plan_using_id, get_customer_using_id, update_customer, get_payments_using_custid, get_payment_using_id, get_claims_using_custid, get_total_plans_cust
from plans import plan

def customer():
    st.title("Welcome Back!!")

    menu = ["Choose", "LOGIN", "SIGNUP"]
    choice = st.sidebar.selectbox("New Customer, SignUp else Login:", menu)

    if choice == 'LOGIN':
        st.subheader("Login")

        email = st.text_input("email :")
        password = st.text_input("Password :", type='password')

        if st.checkbox('login'):
            result = login_customer(email, password)
            if result:
                st.success("Logged in as {}".format(email))
                profile = view_customer(email, password)
                df = pd.DataFrame(profile, columns=['ID', 'First Name', 'Last Name','Mobile', 'Email', 'Password', 'Date of birth', 'Age'])
                with st.expander("View Profile"):
                    st.dataframe(df)
                
                menu2 = ["choose" ,"Edit Profile","Buy new Plan", "View my plans", "Submit claim request", "Status" ]

                choice2 = st.selectbox("Choose :", menu2)

                if choice2 == "Edit Profile" : 
                    customerId = df.iloc[:, 0]
                    custDetails = get_customer_using_id(int(customerId))
                    if custDetails:
                        oldFirstName = custDetails[0][1]
                        oldLastName = custDetails[0][2]
                        oldMobile = custDetails[0][3]
                        oldEmail = custDetails[0][4]
                        oldPassword = custDetails[0][5]
                        
                        new_firstName1 = st.text_input("First Name :", oldFirstName)
                        new_lastName1 = st.text_input("Last Name :", oldLastName)
                        new_mobile1 = st.text_input("Mobile :", oldMobile)
                        new_email1 = st.text_input("Email :", oldEmail)
                        new_password1 = st.text_input("Password :", oldPassword)

                        if st.button("Update Profile "):
                            update_customer(new_firstName1, new_lastName1, new_mobile1, new_email1, new_password1,int(customerId) )
                            st.success("Profile details updated successfully")

                if choice2 == "Buy new Plan": 
                    
                    customerId = df.iloc[:, 0]
                    result2 = get_all_admins()
                    
                    if st.button("View all agents") : 
                        df = pd.DataFrame(result2, columns=[
                                        'ID', 'First Name', 'Last Name', 'Email'])
                        st.dataframe(df)
                    list_of_agents = [i[0] for i in result2]
                    selected_agent_id = st.selectbox("Choose agent :", list_of_agents)
                    # st.write(selected_agent_id)
                    plans = view_plan(int(selected_agent_id))

                    df3 = pd.DataFrame(plans, columns=[
                                'ID', 'Plan Name', 'Plan Description', 'Plan Price', 'Plan Duration', 'Admin Id'])
                    st.dataframe(df3)

                    list_of_plans = [i[0] for i in plans]
                    selected_plan_id = st.selectbox("Choose Plan to Buy:", list_of_plans)
                    if len(list_of_plans)>0:
                        if st.checkbox("Buy ") :
                            new_purpose = st.text_input("Purpose of buying insurance {}:".format(selected_plan_id))
                            date2 = date.today()
                            if st.button("Continue "):
                                add_payment(new_purpose, date2 ,int(customerId), int(selected_plan_id), int(selected_agent_id))
                                st.success("Successfully bought plan {}".format(selected_plan_id))
            
                elif choice2 == "View my plans":
                    customerId = df.iloc[:, 0]
                    x = get_total_plans_cust(int(customerId))
                    st.write("Total number of plans : {}".format(x[0][0]))
                    
                    result3 = view_payment(int(customerId))
                    df = pd.DataFrame(result3, columns=[
                                 'Id' ,'Purpose' , 'Date' ,'Customer Id' , 'Plan Id' ,'Admin Id'])
                    # with st.expander("View Plans"):
                    st.dataframe(df)

                elif choice2 == "Submit claim request":
                    customerId = df.iloc[:, 0]
                    result4 = view_payment(int(customerId))
                    dfx = pd.DataFrame(result4, columns=[
                                 'Id' ,'Purpose' , 'Date' ,'Customer Id' , 'Plan Id' ,'Admin Id'])
                    # with st.expander("View Plans"):
                    st.dataframe(dfx)
                    # if st.button("Claim Insurance") : 
                    list_of_payments = [i[0] for i in result4]
                    selected_payment_id = st.selectbox("Choose Plan to Claim:", list_of_payments)
                        # if st.button("Claim {}".format(selected_payment_id)):
                    claim_reason = st.text_input("Reason for claim : ")

                    date3 = date.today()
                    if selected_payment_id: 
                        result4 = get_payment_using_id(int(selected_payment_id))
                        dfp = pd.DataFrame(result4, columns=[
                                    'Id' ,'Purpose' , 'Date' ,'Customer Id' , 'Plan Id' ,'Admin Id'])
                        st.write(dfp)
                        planIdx = dfp["Plan Id"][0]
                        adminIdx = dfp["Admin Id"][0]
                        if st.button("Claim "):
                            add_claims(claim_reason,"",date3,int(selected_payment_id),int(customerId),int(planIdx), int(adminIdx)) 
                            st.success("Requested successfully")
                if choice2 == "Status":
                    customerId = df.iloc[:, 0]
                    result5 = get_claims_using_custid(int(customerId))

                    if result5:
                        df = pd.DataFrame(result5, columns=[
                            'ID', 'Reason', 'Status', 'Date', 'paymentId', 'customerId', 'planId', 'adminId'])
                        # with st.expander("View Plans"):
                        st.dataframe(df)
            else:
                st.warning("Incorrect password/username")

    elif choice == 'SIGNUP':
        st.subheader("SignUp")

        new_firstName = st.text_input("First Name:")
        new_lastName = st.text_input("Last Name:")
        new_mobile = st.text_input("Mobile : ")
        new_email = st.text_input("Email:")
        new_password = st.text_input("Password:", type='password')
        new_dob = st.date_input("Date of birth :")

        if st.button('signup'):
            create_customer()
            add_customer(new_firstName, new_lastName,new_mobile,  new_email, new_password, new_dob)
            st.success(
                "You have successfully created an account, Go back login page to login")


def customer_under_admin(adminId):
    st.title("My customers")

    data = get_payments_using_custid(int(adminId))

    if data:
        df = pd.DataFrame(data, columns= [
            'ID', 'Purpose', 'Date of Purchase', 'Customer Id', 'Plan Id', 'Admin Id'
        ])

        st.dataframe(df)