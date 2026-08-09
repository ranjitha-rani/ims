import streamlit as st
import pandas as pd
from plans import plan
from claims import claims
from customer import customer_under_admin
from db import login_admin, create_admin, add_admin, view_admin, get_admin_using_id, update_admin


def admin():
    st.title("Welcome Agent!!")

    menu = ["Choose", "LOGIN", "SIGNUP"]
    choice = st.sidebar.selectbox("New admin, SignUp else Login:", menu)

    if choice == 'LOGIN':
        st.subheader("Login")

        email = st.text_input("email :")
        password = st.text_input("Password :", type='password')

        if st.checkbox('login'):
            result = login_admin(email, password)
            if result:
                st.success("Logged in as {}".format(email))
                profile = view_admin(email, password)
                df = pd.DataFrame(profile, columns=[
                                  'ID', 'First Name', 'Last Name', 'Email', 'Password'])
                with st.expander("View Profile"):
                    st.dataframe(df)
                
                # if st.button("Update Profile "):
                    st.subheader("Update profile")
                    adminId = df.iloc[:, 0]
                    admin_details = get_admin_using_id(int(adminId))
                    if admin_details:
                        oldFirstName = admin_details[0][1]
                        oldLastName = admin_details[0][2]
                        oldEmail = admin_details[0][3]
                        oldPassword = admin_details[0][4]
                
                        new_firstName1 = st.text_input("First Name :", oldFirstName)
                        new_lastName1 = st.text_input("Last Name :", oldLastName)
                        new_email1 = st.text_input("Email :", oldEmail)
                        new_password1 = st.text_input("First Name :", oldPassword)

                        if st.checkbox("Update Profile "):
                            update_admin(new_firstName1, new_lastName1, new_email1, new_password1, int(adminId))
                            st.success("Details updated successfully ")
                menu1 = ["View plans","My customers", "View claim requests"]
                choice1 = st.sidebar.selectbox("CHOOSE :", menu1)
                
                
                if choice1 == "View plans":
                    plan(df.iloc[:, 0])

                if choice1 == "My customers":
                    customer_under_admin(int(df.iloc[:, 0]))

                if choice1 == "View claim requests":
                    claims(int(df.iloc[:, 0]))
            else:
                st.warning("Incorrect password/username")


    elif choice == 'SIGNUP':
        st.subheader("SignUp")

        new_firstName = st.text_input("First Name:")
        new_lastName = st.text_input("Last Name:")
        new_email = st.text_input("Email:")
        new_password = st.text_input("Password:", type='password')

        if st.button('signup'):
            create_admin()
            add_admin(new_firstName, new_lastName, new_email, new_password)
            st.success(
                "You have successfully created an account, Go back login page to login")



# seller -> product (pId) -> backupCart(Pid)

# select sellerId from product join backup on product.sellerId = product.